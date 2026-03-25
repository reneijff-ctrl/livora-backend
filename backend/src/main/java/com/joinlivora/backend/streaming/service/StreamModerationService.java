package com.joinlivora.backend.streaming.service;

import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import com.joinlivora.backend.websocket.RealtimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class StreamModerationService {

    private final StringRedisTemplate redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;

    private static final String MUTE_KEY_PREFIX = "stream:%s:muted:%s";
    private static final String SHADOW_KEY_PREFIX = "stream:%s:shadow:%s";
    private static final String PINNED_KEY_PREFIX = "stream:%s:pinned";

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    /**
     * Mutes a user in a specific stream for a certain duration.
     * Uses Redis key: stream:{creatorId}:muted:{userId}
     */
    public void muteUser(Long creatorId, Long userId, int durationMinutes) {
        log.info("Muting user {} in stream of creator {} for {} minutes", userId, creatorId, durationMinutes);
        String key = String.format(MUTE_KEY_PREFIX, creatorId, userId);
        redisTemplate.opsForValue().set(key, "true", durationMinutes, TimeUnit.MINUTES);
    }

    /**
     * Shadow mutes a user in a specific stream.
     * Uses Redis key: stream:{creatorId}:shadow:{userId}
     */
    public void shadowMuteUser(Long creatorId, Long userId) {
        log.info("Shadow muting user {} in stream of creator {}", userId, creatorId);
        String key = String.format(SHADOW_KEY_PREFIX, creatorId, userId);
        // Shadow mute doesn't specify duration in requirements, assuming persistent for the session.
        // I'll set it to a reasonable long time (e.g., 24 hours) or indefinitely.
        // Let's go with 24 hours for safety if it's meant to be persistent.
        redisTemplate.opsForValue().set(key, "true", 24, TimeUnit.HOURS);
    }

    /**
     * Kicks a user from a stream.
     * Sends WebSocket event to /user/queue/moderation
     */
    public void kickUser(Long creatorId, Long userId) {
        log.info("Kicking user {} from stream of creator {}", userId, creatorId);
        
        userRepository.findById(userId).ifPresent(user -> {
            RealtimeMessage kickEvent = RealtimeMessage.builder()
                    .type("KICK")
                    .payload(Map.of(
                        "creatorId", creatorId,
                        "reason", "You have been kicked from the stream"
                    ))
                    .timestamp(Instant.now())
                    .build();
            
            messagingTemplate.convertAndSendToUser(user.getId().toString(), "/queue/moderation", kickEvent);
        });
    }

    /**
     * Unmutes a user in a specific stream.
     */
    public void unmuteUser(Long creatorId, Long userId) {
        log.info("Unmuting user {} in stream of creator {}", userId, creatorId);
        String key = String.format(MUTE_KEY_PREFIX, creatorId, userId);
        redisTemplate.delete(key);
    }

    /**
     * Checks if a user is muted in a specific stream.
     */
    public boolean isMuted(Long creatorId, Long userId) {
        String key = String.format(MUTE_KEY_PREFIX, creatorId, userId);
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * Checks if a user is shadow muted in a specific stream.
     */
    public boolean isShadowMuted(Long creatorId, Long userId) {
        String key = String.format(SHADOW_KEY_PREFIX, creatorId, userId);
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * Gets the pinned message for a stream.
     */
    public Optional<Map<String, Object>> getPinnedMessage(Long creatorId) {
        String key = String.format(PINNED_KEY_PREFIX, creatorId);
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) return Optional.empty();
        
        try {
            return Optional.of(objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}));
        } catch (Exception e) {
            log.error("Failed to parse pinned message from Redis", e);
            return Optional.empty();
        }
    }
}
