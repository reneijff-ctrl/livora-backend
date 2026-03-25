package com.joinlivora.backend.monetization;

import com.joinlivora.backend.chat.ChatModerationService;
import com.joinlivora.backend.user.User;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service("tipValidationService")
@Slf4j
@RequiredArgsConstructor
public class TipValidationService {

    private final ChatModerationService moderationService;

    @Value("${livora.tipping.min-tokens:1}")
    private long minTipTokens;

    @Value("${livora.tipping.max-per-minute:5}")
    private int maxTipsPerMinute;

    @Value("${livora.supertips.max-per-minute:2}")
    private int maxSuperTipsPerMinute;

    @Value("${livora.supertips.room-cooldown-seconds:10}")
    private int roomCooldownSeconds;

    @Value("${livora.highlights.max-per-minute:5}")
    private int maxHighlightsPerMinute;

    private final Map<Long, Bucket> userBuckets = new ConcurrentHashMap<>();
    private final Map<Long, Bucket> userSuperTipBuckets = new ConcurrentHashMap<>();
    private final Map<Long, Bucket> userHighlightBuckets = new ConcurrentHashMap<>();
    private final Map<UUID, Instant> roomLastSuperTipTime = new ConcurrentHashMap<>();

    /**
     * Validates a token-based tip.
     *
     * @param viewer The creator sending the tip
     * @param amount The amount of tokens
     * @param roomId The room ID (optional)
     */
    public void validateTokenTip(User viewer, long amount, UUID roomId) {
        if (amount < minTipTokens) {
            throw new IllegalArgumentException("Minimum tip amount is " + minTipTokens + " tokens");
        }
        
        String roomKey = roomId != null ? "stream-" + roomId.toString() : null;
        commonValidation(viewer, roomKey);
    }

    /**
     * Validates a Stripe-based tip.
     *
     * @param viewer The creator sending the tip
     * @param amount The amount in currency
     */
    public void validateStripeTip(User viewer, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ONE) < 0) {
            throw new IllegalArgumentException("Minimum tip amount is 1.00");
        }
        
        commonValidation(viewer, null);
    }

    /**
     * Validates a SuperTip.
     */
    public void validateSuperTip(User viewer, UUID roomId) {
        String roomKey = "stream-" + roomId.toString();
        commonValidation(viewer, roomKey);

        // 1. Per-creator rate limiting for SuperTips
        Bucket bucket = userSuperTipBuckets.computeIfAbsent(viewer.getId(), k -> createNewBucket(maxSuperTipsPerMinute));
        if (!bucket.tryConsume(1)) {
            log.warn("SUPERTIP: Rate limit exceeded for creator {}", viewer.getEmail());
            throw new RuntimeException("SuperTip rate limit exceeded. Please wait a minute.");
        }

        // 2. Room cooldown
        Instant lastTip = roomLastSuperTipTime.get(roomId);
        if (lastTip != null) {
            long secondsPassed = Duration.between(lastTip, Instant.now()).toSeconds();
            if (secondsPassed < roomCooldownSeconds) {
                log.warn("SUPERTIP: Room cooldown active for room {}. {}s remaining", roomId, roomCooldownSeconds - secondsPassed);
                throw new RuntimeException("Room cooldown active. Please wait a few seconds.");
            }
        }
        roomLastSuperTipTime.put(roomId, Instant.now());
    }

    /**
     * Validates a highlighted message request.
     */
    public void validateHighlight(User user, UUID roomId) {
        String roomKey = "stream-" + roomId.toString();
        commonValidation(user, roomKey);

        // Rate limiting for highlights
        Bucket bucket = userHighlightBuckets.computeIfAbsent(user.getId(), k -> createNewBucket(maxHighlightsPerMinute));
        if (!bucket.tryConsume(1)) {
            log.warn("HIGHLIGHTED_CHAT: Rate limit exceeded for creator {}", user.getEmail());
            throw new RuntimeException("Highlight message rate limit exceeded. Please wait a minute.");
        }
    }

    private void commonValidation(User user, String roomKey) {
        // 1. Muted check
        if (moderationService.isMuted(user.getId(), roomKey)) {
            log.warn("TIPPING: Muted creator {} attempted to tip in room {}", user.getEmail(), roomKey);
            throw new AccessDeniedException("You are muted and cannot send tips");
        }

        // 2. Banned check
        if (moderationService.isBanned(user.getId(), roomKey)) {
            log.warn("TIPPING: Banned creator {} attempted to tip in room {}", user.getEmail(), roomKey);
            throw new AccessDeniedException("You are banned and cannot send tips");
        }
    }

    /**
     * Checks if the user is within their tip rate limit.
     * Consumes one token from the bucket.
     * @return true if within limit, false if exceeded.
     */
    public boolean checkRateLimit(User user) {
        Bucket bucket = userBuckets.computeIfAbsent(user.getId(), k -> createNewBucket(maxTipsPerMinute));
        return bucket.tryConsume(1);
    }

    private Bucket createNewBucket(int capacity) {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(capacity)
                        .refillIntervally(capacity, Duration.ofMinutes(1))
                        .build())
                .build();
    }
}
