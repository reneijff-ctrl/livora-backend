package com.joinlivora.backend.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.joinlivora.backend.chat.dto.ChatMessageDto;
import com.joinlivora.backend.config.MetricsService;
import com.joinlivora.backend.resilience.RedisCircuitBreakerService;
import com.joinlivora.backend.streaming.service.LiveViewerCounterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
public class RedisChatBatchService {

    private static final int VIEWER_THRESHOLD = 50;
    private static final String BATCH_KEY_PREFIX = "chat:batch:";
    private static final String LOCK_KEY_PREFIX = "chat:batch:lock:";
    private static final String ACTIVE_CREATORS_KEY = "chat:batch:active";
    private static final Duration BATCH_TTL = Duration.ofSeconds(5);
    private static final Duration LOCK_TTL = Duration.ofMillis(500);
    /** How many consecutive broker failures before switching to Redis-only mode. */
    private static final int BROKER_FAILURE_THRESHOLD = 3;
    /** How long to stay in Redis-only mode before probing RabbitMQ again (ms). */
    private static final long BROKER_RECOVERY_PROBE_MS = 30_000L;

    /**
     * RabbitMQ failure flag. When true, all sends bypass RabbitMQ and use
     * Redis Pub/Sub exclusively until a probe succeeds.
     */
    private final AtomicBoolean rabbitMqDown = new AtomicBoolean(false);
    private final AtomicLong    brokerConsecutiveFailures = new AtomicLong(0);
    private final AtomicLong    brokerDownSince           = new AtomicLong(0);

    private final StringRedisTemplate redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final LiveViewerCounterService liveViewerCounterService;
    private final ObjectMapper objectMapper;
    private final MetricsService metricsService;
    private final RedisPubSubChatService pubSubChatService;
    private final RedisCircuitBreakerService redisCircuitBreaker;
    public RedisChatBatchService(StringRedisTemplate redisTemplate,
                                 SimpMessagingTemplate messagingTemplate,
                                 LiveViewerCounterService liveViewerCounterService,
                                 ObjectMapper objectMapper,
                                 MetricsService metricsService,
                                 RedisPubSubChatService pubSubChatService,
                                 RedisCircuitBreakerService redisCircuitBreaker) {
        this.redisTemplate = redisTemplate;
        this.messagingTemplate = messagingTemplate;
        this.liveViewerCounterService = liveViewerCounterService;
        this.objectMapper = objectMapper;
        this.metricsService = metricsService;
        this.pubSubChatService = pubSubChatService;
        this.redisCircuitBreaker = redisCircuitBreaker;
    }

    /**
     * Enqueue a chat message for broadcast. If the room has fewer than
     * {@link #VIEWER_THRESHOLD} viewers, the message is broadcast immediately.
     * Otherwise it is buffered in Redis and flushed by the scheduled batch window.
     */
    public void enqueueMessage(Long creatorUserId, ChatMessageDto message) {
        if (creatorUserId == null || message == null) {
            return;
        }

        long viewerCount = liveViewerCounterService.getViewerCount(creatorUserId);

        if (viewerCount < VIEWER_THRESHOLD) {
            // Dual-publish: RabbitMQ (primary) + Redis Pub/Sub (when flag enabled)
            messagingTemplate.convertAndSend(
                    "/exchange/amq.topic/chat." + creatorUserId, message);
            pubSubChatService.publish(creatorUserId, message);
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(message);
            String batchKey = BATCH_KEY_PREFIX + creatorUserId;

            redisTemplate.opsForList().rightPush(batchKey, json);
            redisTemplate.opsForList().trim(batchKey, -500, -1); // cap list at 500 to prevent unbounded O(N) reads
            redisTemplate.expire(batchKey, BATCH_TTL);

            // Track this creator as having pending messages (avoids SCAN)
            redisTemplate.opsForSet().add(ACTIVE_CREATORS_KEY, creatorUserId.toString());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize chat message for creator {}, sending directly: {}",
                    creatorUserId, e.getMessage());
            messagingTemplate.convertAndSend(
                    "/exchange/amq.topic/chat." + creatorUserId, message);
            pubSubChatService.publish(creatorUserId, message);
        } catch (Exception e) {
            log.error("Redis error buffering chat message for creator {}, sending directly: {}",
                    creatorUserId, e.getMessage());
            messagingTemplate.convertAndSend(
                    "/exchange/amq.topic/chat." + creatorUserId, message);
            pubSubChatService.publish(creatorUserId, message);
        }
    }

    /**
     * Flush all buffered chat messages every 200ms. Each creator's queued
     * messages are sent as a single CHAT_BATCH payload. Uses distributed
     * locking per creator so only one instance flushes each creator's batch.
     */
    @Scheduled(fixedDelay = 200)
    public void flushBatches() {
        // execute() handles OPEN, HALF_OPEN probe, and CLOSED states transparently.
        // No pre-check with isOpen() — that would block the HALF_OPEN recovery probe.
        @SuppressWarnings("unchecked")
        Set<String> rawCreators = (Set<String>) redisCircuitBreaker.execute(
                () -> redisTemplate.opsForSet().members(ACTIVE_CREATORS_KEY),
                java.util.Collections.emptySet(),
                "redis:chat:active-creators-set");
        Set<String> activeCreators = (rawCreators != null) ? rawCreators : java.util.Collections.emptySet();
        if (activeCreators.isEmpty()) {
            return;
        }

        for (String creatorIdStr : activeCreators) {
            try {
                Long creatorUserId = Long.valueOf(creatorIdStr);
                String lockKey = LOCK_KEY_PREFIX + creatorUserId;

                // Distributed lock: only one instance flushes this creator per cycle
                Boolean acquired = redisTemplate.opsForValue()
                        .setIfAbsent(lockKey, "1", LOCK_TTL);

                if (Boolean.TRUE.equals(acquired)) {
                    flushCreatorBatch(creatorUserId);
                }
            } catch (NumberFormatException e) {
                log.warn("Invalid creator ID in active set: {}", creatorIdStr);
                redisTemplate.opsForSet().remove(ACTIVE_CREATORS_KEY, creatorIdStr);
            } catch (Exception e) {
                log.error("Failed to flush batch for creator {}: {}", creatorIdStr, e.getMessage());
            }
        }
    }

    /**
     * Peek, broadcast, and delete-on-success all buffered messages for a single creator.
     *
     * <p>Uses a safe peek-then-trim pattern to guarantee message durability:
     * <ol>
     *   <li>LRANGE key 0 -1 — read messages without removing them (peek)</li>
     *   <li>Process and send messages to the broker</li>
     *   <li>LTRIM key N -1 — remove only the messages that were successfully processed</li>
     * </ol>
     * If processing fails, messages remain in Redis and will be retried on the next
     * scheduled invocation. No messages are lost on broker or DB failure.
     */
    void flushCreatorBatch(Long creatorUserId) {
        String batchKey = BATCH_KEY_PREFIX + creatorUserId;

        // Peek: read messages WITHOUT deleting them first
        List<String> rawMessages = redisTemplate.opsForList().range(batchKey, 0, -1);

        if (rawMessages == null || rawMessages.isEmpty()) {
            // Nothing to process — clean up active set
            redisTemplate.opsForSet().remove(ACTIVE_CREATORS_KEY, creatorUserId.toString());
            return;
        }

        int peekedCount = rawMessages.size();

        List<ChatMessageDto> batch = new ArrayList<>(peekedCount);
        for (String json : rawMessages) {
            try {
                batch.add(objectMapper.readValue(json, ChatMessageDto.class));
            } catch (JsonProcessingException e) {
                log.warn("Failed to deserialize buffered chat message for creator {}: {}",
                        creatorUserId, e.getMessage());
            }
        }

        if (batch.isEmpty()) {
            // All messages were corrupted — trim them to avoid infinite retry of bad data
            metricsService.getChatMessagesFailed().increment(peekedCount);
            redisTemplate.opsForList().trim(batchKey, peekedCount, -1);
            redisTemplate.opsForSet().remove(ACTIVE_CREATORS_KEY, creatorUserId.toString());
            return;
        }

        String destination = "/exchange/amq.topic/chat." + creatorUserId;

        // Process: send to broker. If this throws, we do NOT trim — messages stay for retry.
        try {
            if (rabbitMqDown.get()) {
                // RabbitMQ is currently down — attempt a recovery probe every 30 s
                boolean probe = (Instant.now().toEpochMilli() - brokerDownSince.get()) >= BROKER_RECOVERY_PROBE_MS;
                if (!probe) {
                    // Still in Redis-only mode — use Pub/Sub exclusively
                    sendViaRedisPubSubOnly(creatorUserId, batch, destination);
                } else {
                    // Probe: try RabbitMQ once
                    log.info("[RABBITMQ_FAILOVER] Probing RabbitMQ recovery for creator {}", creatorUserId);
                    trySendViaRabbitMq(creatorUserId, batch, destination);
                }
            } else {
                // Normal path: RabbitMQ primary + Redis Pub/Sub dual-publish
                trySendViaRabbitMq(creatorUserId, batch, destination);
            }
        } catch (Exception e) {
            log.error("Failed to send chat batch for creator {} — messages left in Redis for retry: {}",
                    creatorUserId, e.getMessage());
            metricsService.getChatMessagesRetried().increment(batch.size());
            return;
        }

        // Delete-on-success: trim away only the messages we just processed.
        // If new messages were pushed concurrently, they remain untouched.
        metricsService.getChatMessagesSent().increment(batch.size());
        redisTemplate.opsForList().trim(batchKey, peekedCount, -1);
        redisTemplate.opsForSet().remove(ACTIVE_CREATORS_KEY, creatorUserId.toString());
    }

    // ── Broker send helpers ───────────────────────────────────────────────────

    private void trySendViaRabbitMq(Long creatorUserId, List<ChatMessageDto> batch, String destination) {
        try {
            if (batch.size() == 1) {
                messagingTemplate.convertAndSend(destination, batch.get(0));
                pubSubChatService.publish(creatorUserId, batch.get(0));
            } else {
                Map<String, Object> batchPayload = Map.of("type", "CHAT_BATCH", "messages", batch);
                messagingTemplate.convertAndSend(destination, batchPayload);
                pubSubChatService.publishBatch(creatorUserId, batchPayload);
            }
            // Success — reset failure counter; if we were in recovery mode, close it
            if (rabbitMqDown.compareAndSet(true, false)) {
                brokerConsecutiveFailures.set(0);
                log.info("[RABBITMQ_FAILOVER] RabbitMQ recovered — resuming dual-publish for creator {}",
                        creatorUserId);
            } else {
                brokerConsecutiveFailures.set(0);
            }
        } catch (Exception ex) {
            long failures = brokerConsecutiveFailures.incrementAndGet();
            log.warn("[RABBITMQ_FAILOVER] RabbitMQ send failure #{} for creator {}: {}",
                    failures, creatorUserId, ex.getMessage());
            if (failures >= BROKER_FAILURE_THRESHOLD && rabbitMqDown.compareAndSet(false, true)) {
                brokerDownSince.set(Instant.now().toEpochMilli());
                log.error("[RABBITMQ_FAILOVER] RabbitMQ declared DOWN after {} consecutive failures. "
                        + "Switching to Redis Pub/Sub-only delivery for all chats.", BROKER_FAILURE_THRESHOLD);
            }
            // Fall back to Redis Pub/Sub for this batch so messages are not lost
            sendViaRedisPubSubOnly(creatorUserId, batch, destination);
        }
    }

    private void sendViaRedisPubSubOnly(Long creatorUserId, List<ChatMessageDto> batch, String destination) {
        try {
            if (batch.size() == 1) {
                pubSubChatService.publish(creatorUserId, batch.get(0));
            } else {
                Map<String, Object> batchPayload = Map.of("type", "CHAT_BATCH", "messages", batch);
                pubSubChatService.publishBatch(creatorUserId, batchPayload);
            }
            log.debug("[RABBITMQ_FAILOVER] Delivered {} messages via Redis Pub/Sub for creator {}",
                    batch.size(), creatorUserId);
        } catch (Exception ex) {
            log.error("[RABBITMQ_FAILOVER] Redis Pub/Sub also failed for creator {} — messages will retry: {}",
                    creatorUserId, ex.getMessage());
            throw ex; // propagate so caller does NOT trim and messages are retried
        }
    }

    /** @return true when RabbitMQ is currently considered down */
    public boolean isRabbitMqDown() {
        return rabbitMqDown.get();
    }

    /**
     * Remove the buffer for a creator (e.g., when a stream ends).
     */
    public void clearBuffer(Long creatorUserId) {
        if (creatorUserId == null) {
            return;
        }
        redisTemplate.delete(BATCH_KEY_PREFIX + creatorUserId);
        redisTemplate.opsForSet().remove(ACTIVE_CREATORS_KEY, creatorUserId.toString());
    }
}
