package com.joinlivora.backend.chat;

import com.joinlivora.backend.user.User;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChatRateLimitService {

    @Value("${livora.chat.slow-mode-interval-seconds:3}")
    private int slowModeIntervalSeconds;

    private final Map<String, Bucket> userRoomBuckets = new ConcurrentHashMap<>();

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

        String key = userId + ":" + roomId;
        Bucket bucket = userRoomBuckets.computeIfAbsent(key, k -> createNewBucket());

        if (!bucket.tryConsume(1)) {
            log.warn("CHAT: Slow mode active for creator {} in room {}. Interval: {}s", userId, roomId, slowModeIntervalSeconds);
            throw new RuntimeException("Slow mode is active. Please wait " + slowModeIntervalSeconds + " seconds between messages.");
        }
    }

    private Bucket createNewBucket() {
        // Slow mode: 1 message per slowModeIntervalSeconds
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(1)
                        .refillIntervally(1, Duration.ofSeconds(slowModeIntervalSeconds))
                        .build())
                .build();
    }

    // Visible for testing
    public void setSlowModeIntervalSeconds(int seconds) {
        this.slowModeIntervalSeconds = seconds;
        userRoomBuckets.clear();
    }
}
