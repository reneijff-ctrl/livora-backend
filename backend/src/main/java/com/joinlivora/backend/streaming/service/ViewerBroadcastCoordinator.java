package com.joinlivora.backend.streaming.service;

import com.joinlivora.backend.websocket.RealtimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

/**
 * Coordinates viewer count broadcasts across multiple backend instances using Redis.
 *
 * <p>Problem: In a multi-instance deployment, each instance may independently receive
 * viewer join/leave events and schedule its own broadcasts, causing duplicate WebSocket
 * messages and inconsistent UI updates.</p>
 *
 * <p>Solution: Uses a shared Redis Set to collect creatorIds needing updates, and a
 * distributed SETNX lock to ensure only ONE instance broadcasts per flush cycle (every 3 seconds).</p>
 *
 * <h3>Redis Keys</h3>
 * <ul>
 *   <li>{@code viewer:pending} — SET of creatorIds with pending viewer count updates</li>
 *   <li>{@code viewer:broadcast:lock} — SETNX lock with 3-second TTL to prevent duplicate flushes</li>
 * </ul>
 */
@Service
@Slf4j
public class ViewerBroadcastCoordinator {

    private static final String PENDING_KEY = "viewer:pending";
    private static final String LOCK_KEY = "viewer:broadcast:lock";
    private static final Duration LOCK_TTL = Duration.ofSeconds(3);

    private final StringRedisTemplate redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final LiveViewerCounterService liveViewerCounterService;

    public ViewerBroadcastCoordinator(
            StringRedisTemplate redisTemplate,
            SimpMessagingTemplate messagingTemplate,
            LiveViewerCounterService liveViewerCounterService
    ) {
        this.redisTemplate = redisTemplate;
        this.messagingTemplate = messagingTemplate;
        this.liveViewerCounterService = liveViewerCounterService;
    }

    /**
     * Schedules a viewer count broadcast for the given creator.
     * Called by any instance when a viewer joins or leaves.
     * The actual broadcast is deferred to the next flush cycle.
     *
     * @param creatorId the creator whose viewer count changed (null-safe)
     */
    public void scheduleBroadcast(Long creatorId) {
        if (creatorId == null) return;

        try {
            redisTemplate.opsForSet().add(PENDING_KEY, creatorId.toString());
        } catch (Exception e) {
            log.error("Failed to schedule viewer broadcast for creator {}: {}", creatorId, e.getMessage());
        }
    }

    /**
     * Processes pending viewer count broadcasts every 3 seconds.
     * Uses a distributed SETNX lock so only one instance flushes per cycle.
     */
    @Scheduled(fixedRate = 3000)
    public void processBroadcasts() {
        try {
            // Acquire distributed lock — only one instance wins per 3-second cycle
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(LOCK_KEY, "1", LOCK_TTL);

            if (!Boolean.TRUE.equals(acquired)) {
                return;
            }

            // Atomically read and clear the pending set
            Set<String> pending = redisTemplate.opsForSet().members(PENDING_KEY);
            if (pending == null || pending.isEmpty()) {
                return;
            }
            redisTemplate.delete(PENDING_KEY);

            // Broadcast each creator's current viewer count
            for (String creatorIdStr : pending) {
                try {
                    Long creatorId = Long.valueOf(creatorIdStr);
                    long currentCount = liveViewerCounterService.getViewerCount(creatorId);

                    RealtimeMessage message = RealtimeMessage.of("viewer_count:update", Map.of(
                            "creatorUserId", creatorId,
                            "viewerCount", currentCount
                    ));
                    messagingTemplate.convertAndSend(
                            "/exchange/amq.topic/viewers." + creatorId, message);

                    log.debug("VIEWER_BROADCAST: creatorId={}, count={}", creatorId, currentCount);
                } catch (NumberFormatException e) {
                    log.warn("Invalid creatorId in pending set: '{}'", creatorIdStr);
                } catch (Exception e) {
                    log.error("Failed to broadcast viewer count for creator {}: {}",
                            creatorIdStr, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Failed to process viewer broadcasts: {}", e.getMessage());
        }
    }
}
