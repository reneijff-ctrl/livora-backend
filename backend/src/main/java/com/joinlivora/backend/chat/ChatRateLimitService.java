package com.joinlivora.backend.chat;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
@Slf4j
public class ChatRateLimitService {

    @Value("${livora.chat.slow-mode-interval-seconds:3}")
    private int slowModeIntervalSeconds;

    private final StringRedisTemplate redisTemplate;

    private static final String SLOW_MODE_KEY_PREFIX = "chat:slow:";

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.joinlivora.backend.resilience.RedisCircuitBreakerService redisCircuitBreaker;

    public ChatRateLimitService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Validates if a creator can send a message in a specific room based on slow mode rules.
     * Throws RuntimeException if rate limit is exceeded.
     *
     * @param userId ID of the creator
     * @param roomId ID of the stream room
     */
    public void validateMessageRate(Long userId, UUID roomId) {
        if (slowModeIntervalSeconds <= 0) {
            return;
        }

        // Use execute() — no isOpen() pre-check — so HALF_OPEN probing is not blocked.
        // Fail-open: if Redis is down, slow-mode cannot be enforced; message is allowed through.
        // This is acceptable because slow-mode is a UX feature, not a payment gate.
        final String key = SLOW_MODE_KEY_PREFIX + userId + ":" + roomId;
        final int interval = slowModeIntervalSeconds;
        Boolean wasAbsent;
        if (redisCircuitBreaker != null) {
            wasAbsent = redisCircuitBreaker.execute(
                    () -> redisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofSeconds(interval)),
                    Boolean.TRUE, // FAIL-OPEN fallback: treat as "key was absent" = allow message
                    "redis:chat:slow-mode:" + userId);
        } else {
            try {
                wasAbsent = redisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofSeconds(interval));
            } catch (org.springframework.dao.DataAccessException ex) {
                // Fail-open: if Redis is unavailable and no circuit breaker, allow the message through
                log.warn("CHAT RATE LIMIT REDIS ERROR for userId={} room={}: {}", userId, roomId, ex.getMessage());
                wasAbsent = Boolean.TRUE;
            }
        }
        if (!Boolean.TRUE.equals(wasAbsent)) {
            log.warn("CHAT: Slow mode active for creator {} in room {}. Interval: {}s", userId, roomId, slowModeIntervalSeconds);
            throw new RuntimeException("Slow mode is active. Please wait " + slowModeIntervalSeconds + " seconds between messages.");
        }
    }

    // Visible for testing
    public void setSlowModeIntervalSeconds(int seconds) {
        this.slowModeIntervalSeconds = seconds;
    }
}
