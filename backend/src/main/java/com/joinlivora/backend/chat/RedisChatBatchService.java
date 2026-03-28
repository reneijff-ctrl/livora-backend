package com.joinlivora.backend.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.joinlivora.backend.chat.dto.ChatMessageDto;
import com.joinlivora.backend.streaming.service.LiveViewerCounterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Slf4j
public class RedisChatBatchService {

    private static final int VIEWER_THRESHOLD = 50;
    private static final String BATCH_KEY_PREFIX = "chat:batch:";
    private static final String LOCK_KEY_PREFIX = "chat:batch:lock:";
    private static final String ACTIVE_CREATORS_KEY = "chat:batch:active";
    private static final Duration BATCH_TTL = Duration.ofSeconds(5);
    private static final Duration LOCK_TTL = Duration.ofMillis(200);

    private static final DefaultRedisScript<List> ATOMIC_DRAIN_SCRIPT;

    static {
        ATOMIC_DRAIN_SCRIPT = new DefaultRedisScript<>();
        ATOMIC_DRAIN_SCRIPT.setScriptText(
                "local msgs = redis.call('LRANGE', KEYS[1], 0, -1)\n" +
                "redis.call('DEL', KEYS[1])\n" +
                "return msgs");
        ATOMIC_DRAIN_SCRIPT.setResultType(List.class);
    }

    private final StringRedisTemplate redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final LiveViewerCounterService liveViewerCounterService;
    private final ObjectMapper objectMapper;

    public RedisChatBatchService(StringRedisTemplate redisTemplate,
                                 SimpMessagingTemplate messagingTemplate,
                                 LiveViewerCounterService liveViewerCounterService,
                                 ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.messagingTemplate = messagingTemplate;
        this.liveViewerCounterService = liveViewerCounterService;
        this.objectMapper = objectMapper;
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
            messagingTemplate.convertAndSend(
                    "/exchange/amq.topic/chat." + creatorUserId, message);
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(message);
            String batchKey = BATCH_KEY_PREFIX + creatorUserId;

            redisTemplate.opsForList().rightPush(batchKey, json);
            redisTemplate.expire(batchKey, BATCH_TTL);

            // Track this creator as having pending messages (avoids SCAN)
            redisTemplate.opsForSet().add(ACTIVE_CREATORS_KEY, creatorUserId.toString());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize chat message for creator {}, sending directly: {}",
                    creatorUserId, e.getMessage());
            messagingTemplate.convertAndSend(
                    "/exchange/amq.topic/chat." + creatorUserId, message);
        } catch (Exception e) {
            log.error("Redis error buffering chat message for creator {}, sending directly: {}",
                    creatorUserId, e.getMessage());
            messagingTemplate.convertAndSend(
                    "/exchange/amq.topic/chat." + creatorUserId, message);
        }
    }

    /**
     * Flush all buffered chat messages every 200ms. Each creator's queued
     * messages are sent as a single CHAT_BATCH payload. Uses distributed
     * locking per creator so only one instance flushes each creator's batch.
     */
    @Scheduled(fixedDelay = 200)
    public void flushBatches() {
        Set<String> activeCreators;
        try {
            activeCreators = redisTemplate.opsForSet().members(ACTIVE_CREATORS_KEY);
        } catch (Exception e) {
            log.error("Failed to read active creators set: {}", e.getMessage());
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
                log.warn("Invalid creator ID in active set: {}", creatorIdStr);
                redisTemplate.opsForSet().remove(ACTIVE_CREATORS_KEY, creatorIdStr);
            } catch (Exception e) {
                log.error("Failed to flush batch for creator {}: {}", creatorIdStr, e.getMessage());
            }
        }
    }

    /**
     * Drain and broadcast all buffered messages for a single creator.
     */
    @SuppressWarnings("unchecked")
    void flushCreatorBatch(Long creatorUserId) {
        String batchKey = BATCH_KEY_PREFIX + creatorUserId;

        // Atomic drain via Lua script: LRANGE + DEL in a single atomic operation
        List<String> rawMessages = redisTemplate.execute(
                ATOMIC_DRAIN_SCRIPT, Collections.singletonList(batchKey));

        // Remove from active set if drained
        redisTemplate.opsForSet().remove(ACTIVE_CREATORS_KEY, creatorUserId.toString());

        if (rawMessages == null || rawMessages.isEmpty()) {
            return;
        }

        List<ChatMessageDto> batch = new ArrayList<>(rawMessages.size());
        for (String json : rawMessages) {
            try {
                batch.add(objectMapper.readValue(json, ChatMessageDto.class));
            } catch (JsonProcessingException e) {
                log.warn("Failed to deserialize buffered chat message for creator {}: {}",
                        creatorUserId, e.getMessage());
            }
        }

        if (batch.isEmpty()) {
            return;
        }

        String destination = "/exchange/amq.topic/chat." + creatorUserId;

        if (batch.size() == 1) {
            messagingTemplate.convertAndSend(destination, batch.get(0));
        } else {
            Map<String, Object> batchPayload = Map.of(
                    "type", "CHAT_BATCH",
                    "messages", batch
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
}
