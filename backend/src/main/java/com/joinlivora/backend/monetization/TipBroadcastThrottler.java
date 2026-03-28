package com.joinlivora.backend.monetization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Batches tip-related WebSocket broadcasts per creator using Redis,
 * reducing RabbitMQ load during tip wars. Works across multiple
 * backend instances via distributed SETNX locking.
 *
 * <p>Each tip event is serialized to a Redis List keyed by creator ID.
 * A scheduled job flushes all buffered events every 500ms. If only one
 * event is pending, it is sent directly; otherwise events are wrapped
 * in a {@code TIP_BATCH} envelope.</p>
 */
@Service
@Slf4j
public class TipBroadcastThrottler {

    private static final String BATCH_KEY_PREFIX = "tip:batch:";
    private static final String LOCK_KEY_PREFIX = "tip:batch:lock:";
    private static final String ACTIVE_CREATORS_KEY = "tip:batch:active";
    private static final Duration BATCH_TTL = Duration.ofSeconds(5);
    private static final Duration LOCK_TTL = Duration.ofMillis(500);

    private final StringRedisTemplate redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public TipBroadcastThrottler(StringRedisTemplate redisTemplate,
                                 SimpMessagingTemplate messagingTemplate,
                                 ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Enqueue a tip event for batched broadcast on the monetization topic.
     * The event is buffered in Redis and flushed by the scheduled batch window.
     * On serialization or Redis errors, the event is sent directly as a fallback.
     *
     * @param creatorUserId the creator whose monetization topic receives the event
     * @param event         the tip event payload (e.g. {@code LiveEvent<?>})
     */
    public void enqueueTipEvent(Long creatorUserId, Object event) {
        if (creatorUserId == null || event == null) {
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(event);
            String batchKey = BATCH_KEY_PREFIX + creatorUserId;

            redisTemplate.opsForList().rightPush(batchKey, json);
            redisTemplate.expire(batchKey, BATCH_TTL);

            // Track this creator as having pending events (avoids SCAN)
            redisTemplate.opsForSet().add(ACTIVE_CREATORS_KEY, creatorUserId.toString());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize tip event for creator {}, sending directly: {}",
                    creatorUserId, e.getMessage());
            sendDirect(creatorUserId, event);
        } catch (Exception e) {
            log.error("Redis error buffering tip event for creator {}, sending directly: {}",
                    creatorUserId, e.getMessage());
            sendDirect(creatorUserId, event);
        }
    }

    /**
     * Flush all buffered tip events every 500ms. Each creator's queued
     * events are sent as a single {@code TIP_BATCH} payload on the
     * monetization topic. Uses distributed locking per creator so only
     * one instance flushes each creator's batch.
     */
    @Scheduled(fixedDelay = 500)
    public void flushBatches() {
        Set<String> activeCreators;
        try {
            activeCreators = redisTemplate.opsForSet().members(ACTIVE_CREATORS_KEY);
        } catch (Exception e) {
            log.error("Failed to read active tip creators set: {}", e.getMessage());
            return;
        }

        if (activeCreators == null || activeCreators.isEmpty()) {
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
                log.warn("Invalid creator ID in active tip set: {}", creatorIdStr);
                redisTemplate.opsForSet().remove(ACTIVE_CREATORS_KEY, creatorIdStr);
            } catch (Exception e) {
                log.error("Failed to flush tip batch for creator {}: {}", creatorIdStr, e.getMessage());
            }
        }
    }

    /**
     * Drain and broadcast all buffered tip events for a single creator.
     */
    void flushCreatorBatch(Long creatorUserId) {
        String batchKey = BATCH_KEY_PREFIX + creatorUserId;

        // Atomic drain: read all then delete
        List<String> rawEvents = redisTemplate.opsForList().range(batchKey, 0, -1);
        redisTemplate.delete(batchKey);

        // Remove from active set if drained
        redisTemplate.opsForSet().remove(ACTIVE_CREATORS_KEY, creatorUserId.toString());

        if (rawEvents == null || rawEvents.isEmpty()) {
            return;
        }

        List<Object> batch = new ArrayList<>(rawEvents.size());
        for (String json : rawEvents) {
            try {
                batch.add(objectMapper.readValue(json, Object.class));
            } catch (JsonProcessingException e) {
                log.warn("Failed to deserialize buffered tip event for creator {}: {}",
                        creatorUserId, e.getMessage());
            }
        }

        if (batch.isEmpty()) {
            return;
        }

        String destination = "/exchange/amq.topic/monetization." + creatorUserId;

        if (batch.size() == 1) {
            messagingTemplate.convertAndSend(destination, batch.get(0));
        } else {
            Map<String, Object> batchPayload = Map.of(
                    "type", "TIP_BATCH",
                    "events", batch
            );
            messagingTemplate.convertAndSend(destination, batchPayload);
        }
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

    private void sendDirect(Long creatorUserId, Object event) {
        messagingTemplate.convertAndSend(
                "/exchange/amq.topic/monetization." + creatorUserId, event);
    }
}
