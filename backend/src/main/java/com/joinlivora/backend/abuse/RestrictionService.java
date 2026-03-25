package com.joinlivora.backend.abuse;

import com.joinlivora.backend.abuse.model.RestrictionLevel;
import com.joinlivora.backend.abuse.model.UserRestriction;
import com.joinlivora.backend.abuse.repository.UserRestrictionRepository;
import com.joinlivora.backend.exception.UserRestrictedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RestrictionService {

    public static final BigDecimal DEFAULT_TIP_LIMIT = new BigDecimal("50.00");

    private final UserRestrictionRepository userRestrictionRepository;

    /**
     * Applies a restriction to a creator based on their fraud score.
     * If an active restriction already exists, it is only updated if the new level is higher (escalation).
     *
     * @param userId    The ID of the creator (as UUID)
     * @param score     The calculated fraud score (0-100)
     * @param reason    The type for the restriction
     */
    @Transactional
    public boolean applyRestriction(UUID userId, int score, String reason) {
        RestrictionLevel newLevel = mapScoreToRestriction(score);
        if (newLevel == RestrictionLevel.NONE) {
            log.debug("No restriction needed for score {} and creator {}", score, userId);
            return false;
        }

        Instant now = Instant.now();
        Optional<UserRestriction> existingOpt = userRestrictionRepository.findActiveByUserId(userId, now);

        if (existingOpt.isPresent()) {
            UserRestriction existing = existingOpt.get();
            RestrictionLevel currentLevel = existing.getRestrictionLevel();
            
            if (newLevel.ordinal() > currentLevel.ordinal()) {
                log.info("Escalating restriction for creator {}: {} -> {} (Score: {})", userId, currentLevel, newLevel, score);
                existing.setRestrictionLevel(newLevel);
                existing.setReason(reason + " (Score: " + score + ", Escalated from " + currentLevel + ")");
                existing.setExpiresAt(calculateExpiry(newLevel));
                userRestrictionRepository.save(existing);
                return true;
            } else {
                log.debug("Current restriction {} for creator {} is higher or equal to new level {}, skipping escalation", currentLevel, userId, newLevel);
                return false;
            }
        } else {
            log.info("Applying initial restriction for creator {}: {} (Score: {})", userId, newLevel, score);
            UserRestriction restriction = UserRestriction.builder()
                    .userId(userId)
                    .restrictionLevel(newLevel)
                    .reason(reason + " (Score: " + score + ")")
                    .expiresAt(calculateExpiry(newLevel))
                    .build();
            userRestrictionRepository.save(restriction);
            return true;
        }
    }

    @Transactional(readOnly = true)
    public Optional<UserRestriction> getActiveRestriction(UUID userId) {
        return userRestrictionRepository.findActiveByUserId(userId, Instant.now());
    }

    /**
     * Validates if a creator can send tips with a specific amount.
     * Throws UserRestrictedException if a relevant restriction is active.
     *
     * @param userId The ID of the creator
     * @param amount The amount of the tip
     */
    @Transactional(readOnly = true)
    public void validateTippingAccess(UUID userId, BigDecimal amount) {
        getActiveRestriction(userId).ifPresent(restriction -> {
            RestrictionLevel level = restriction.getRestrictionLevel();
            if (level == RestrictionLevel.FRAUD_LOCK || level == RestrictionLevel.TEMP_SUSPENSION || level == RestrictionLevel.TIP_COOLDOWN) {
                log.warn("Blocking tip: User {} has an active {} restriction", userId, level);
                String message = level == RestrictionLevel.FRAUD_LOCK ?
                        "Your tipping access is disabled pending manual review." :
                        "Your tipping access is restricted.";
                throw new UserRestrictedException(level, message, restriction.getExpiresAt());
            }
            if (level == RestrictionLevel.TIP_LIMIT && amount != null) {
                if (amount.compareTo(DEFAULT_TIP_LIMIT) > 0) {
                    log.warn("Blocking tip: User {} exceeded {} limit. Amount: {}", userId, level, amount);
                    throw new UserRestrictedException(level,
                            "Your tipping amount is limited to " + DEFAULT_TIP_LIMIT + " due to security risk.",
                            restriction.getExpiresAt());
                }
            }
        });
    }

    /**
     * Legacy method for backward compatibility.
     * @param userId The ID of the creator
     */
    @Transactional(readOnly = true)
    public void validateTippingAccess(UUID userId) {
        validateTippingAccess(userId, null);
    }

    private RestrictionLevel mapScoreToRestriction(int score) {
        if (score >= 100) return RestrictionLevel.TEMP_SUSPENSION;
        if (score >= 90) return RestrictionLevel.FRAUD_LOCK;
        if (score >= 80) return RestrictionLevel.CHAT_MUTE;
        if (score >= 70) return RestrictionLevel.TIP_LIMIT;
        if (score >= 50) return RestrictionLevel.TIP_COOLDOWN;
        if (score >= 30) return RestrictionLevel.SLOW_MODE;
        return RestrictionLevel.NONE;
    }

    private Instant calculateExpiry(RestrictionLevel level) {
        Duration duration = switch (level) {
            case NONE -> Duration.ZERO;
            case SLOW_MODE -> Duration.ofMinutes(2);
            case TIP_COOLDOWN -> Duration.ofMinutes(5);
            case TIP_LIMIT -> Duration.ofHours(24);
            case CHAT_MUTE -> Duration.ofMinutes(15);
            case FRAUD_LOCK -> Duration.ofDays(7);
            case TEMP_SUSPENSION -> Duration.ofHours(24);
        };
        return Instant.now().plus(duration);
    }

    /**
     * Scheduled task to remove expired restrictions from the database.
     * Runs once every hour.
     */
    @Scheduled(fixedDelay = 3600000)
    @Transactional(propagation = Propagation.REQUIRED)
    public void autoExpireRestrictions() {
        Instant now = Instant.now();
        log.info("Running auto-expiry of restrictions at {}", now);
        userRestrictionRepository.deleteByExpiresAtBefore(now);
    }
}
