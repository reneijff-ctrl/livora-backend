package com.joinlivora.backend.creator.service;

import com.joinlivora.backend.presence.service.CreatorPresenceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.stereotype.Service;
import java.util.concurrent.atomic.AtomicBoolean;

import java.time.Duration;

@Service
@Slf4j
public class OnlineStatusService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final CreatorPresenceService creatorPresenceService;
    private final AtomicBoolean redisWarnLogged = new AtomicBoolean(false);

    public OnlineStatusService(
            @Autowired(required = false) RedisTemplate<String, Object> redisTemplate,
            CreatorPresenceService creatorPresenceService) {
        this.redisTemplate = redisTemplate;
        this.creatorPresenceService = creatorPresenceService;
        if (redisTemplate == null) {
            log.info("RedisTemplate not found, OnlineStatusService will operate in no-op mode.");
        }
    }
    private static final String KEY_PREFIX = "presence:creator:";
    private static final long TTL_SECONDS = 60;

    public boolean isAvailable() {
        return redisTemplate != null && !redisWarnLogged.get();
    }

    private void handleRedisError(String operation, Exception ex) {
        if (redisWarnLogged.compareAndSet(false, true)) {
            log.warn("Redis unavailable: {} failed. Further warnings suppressed. Error: {}", operation, ex.getMessage());
        }
    }

    /**
     * Set the creator's online status in Redis with a TTL.
     * @param creatorId The ID from Creator entity (creator_records table)
     */
    public void setOnline(Long creatorId) {
        if (creatorId == null || !isAvailable()) return;
        String key = KEY_PREFIX + creatorId;
        try {
            redisTemplate.opsForValue().set(key, true, Duration.ofSeconds(TTL_SECONDS));
        } catch (Exception ex) {
            handleRedisError("setOnline", ex);
        }
    }

    /**
     * Remove the creator's online status from Redis.
     * @param creatorId The ID from Creator entity (creator_records table)
     */
    public void setOffline(Long creatorId) {
        if (creatorId == null || !isAvailable()) return;
        String key = KEY_PREFIX + creatorId;
        try {
            redisTemplate.delete(key);
        } catch (Exception ex) {
            handleRedisError("setOffline", ex);
        }
    }

    /**
     * Check if a creator is online.
     * @param creatorId The ID from Creator entity (creator_records table)
     * A creator is ONLINE if:
     * - Distributed source of truth (Redis TTL key) exists
     * - OR local source of truth (WebSocket session / active stream)
     */
    public boolean isOnline(Long creatorId) {
        if (creatorId == null) return false;

        // 1. Try Redis (distributed source of truth with TTL)
        if (isAvailable()) {
            try {
                String key = KEY_PREFIX + creatorId;
                Object val = redisTemplate.opsForValue().get(key);
                if (val != null) {
                    return true;
                }
            } catch (Exception ex) {
                handleRedisError("isOnline", ex);
            }
        }

        // 2. Fallback to local service/DB truth (e.g. if Redis is down or key just expired but session still active)
        return creatorPresenceService.isOnline(creatorId);
    }
    
    /**
     * Refresh the TTL for a creator's online status.
     * @param creatorId The ID from Creator entity (creator_records table)
     */
    public void refreshOnlineStatus(Long creatorId) {
        setOnline(creatorId);
    }
}
