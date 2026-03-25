package com.joinlivora.backend.chat;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Service to limit the rate of chat messages per user.
 * Requirements:
 * - Use Redis
 * - Key: chat:rate:{userId}
 * - Allow max 5 messages per 10 seconds
 * - Use INCR + EXPIRE
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ChatRateLimiterService {

    private final StringRedisTemplate redisTemplate;

    private static final int MAX_MESSAGES = 5;
    private static final int WINDOW_SECONDS = 10;
    private static final String KEY_PREFIX = "chat:rate:";

    /**
     * Checks if the user is allowed to send a message.
     * 
     * @param userId The ID of the user
     * @return true if allowed, false if rate limit exceeded
     */
    public boolean isAllowed(Long userId) {
        if (userId == null) {
            return true; // System messages or anonymous (if allowed) are not rate limited here
        }

        String key = KEY_PREFIX + userId;
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            
            if (count != null && count == 1) {
                redisTemplate.expire(key, WINDOW_SECONDS, TimeUnit.SECONDS);
            }
            
            boolean allowed = count != null && count <= MAX_MESSAGES;
            if (!allowed) {
                log.warn("Rate limit exceeded for user {}: {} messages in {}s", userId, count, WINDOW_SECONDS);
            }
            return allowed;
        } catch (Exception e) {
            log.error("Error checking rate limit for user {}", userId, e);
            return true; // Fail open to avoid blocking users on Redis issues
        }
    }
    
    /**
     * Resets the rate limit for a user (useful for testing or manual overrides).
     * 
     * @param userId The ID of the user
     */
    public void reset(Long userId) {
        if (userId != null) {
            redisTemplate.delete(KEY_PREFIX + userId);
        }
    }
}
