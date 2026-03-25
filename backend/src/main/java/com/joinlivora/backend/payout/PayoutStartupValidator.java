package com.joinlivora.backend.payout;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
@RequiredArgsConstructor
public class PayoutStartupValidator {

    private static final Logger logger = LoggerFactory.getLogger(PayoutStartupValidator.class);

    private final CreatorPayoutRepository payoutRepository;
    private final CreatorEarningRepository earningRepository;
    private final PayoutRequestRepository payoutRequestRepository;
    private final JdbcTemplate jdbcTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void validatePayoutState() {
        logger.info("Starting payout state validation...");

        // 1. Number of payouts in PROCESSING > 24h
        Instant twentyFourHoursAgo = Instant.now().minus(24, ChronoUnit.HOURS);
        long stuckPayouts = payoutRepository.countByStatusAndCreatedAtBefore(PayoutStatus.PROCESSING, twentyFourHoursAgo);

        if (stuckPayouts > 0) {
            logger.warn("CRITICAL: Found {} payouts stuck in PROCESSING status for more than 24 hours!", stuckPayouts);
        } else {
            logger.info("No stuck PROCESSING payouts found.");
        }

        // 2. PayoutRequests stuck in PENDING > 7 days
        Instant sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS);
        long stuckRequests = payoutRequestRepository.countByStatusAndCreatedAtBefore(PayoutRequestStatus.PENDING, sevenDaysAgo);

        if (stuckRequests > 0) {
            logger.warn("WARNING: Found {} payout requests stuck in PENDING status for more than 7 days!", stuckRequests);
        } else {
            logger.info("No stuck PENDING payout requests found.");
        }

        // 3. Number of locked earnings without payout or payout request
        long lockedStranded = earningRepository.countByLockedTrueAndPayoutIsNullAndPayoutRequestIsNull();

        if (lockedStranded > 0) {
            logger.warn("WARNING: Found {} locked earnings that are not associated with any payout or payout request. These might be stranded funds.", lockedStranded);
        } else {
            logger.info("No stranded locked earnings found.");
        }

        // 4. Safety check: Users with OPEN chargebacks but payouts enabled
        try {
            Integer inconsistentUsers = jdbcTemplate.queryForObject(
                    "SELECT COUNT(DISTINCT u.id) FROM users u " +
                    "JOIN chargebacks c ON u.id = c.user_id " +
                    "WHERE c.status = 'OPEN' AND u.payouts_enabled = true", Integer.class);

            if (inconsistentUsers != null && inconsistentUsers > 0) {
                logger.error("CRITICAL SAFETY BREACH: Found {} users with OPEN chargebacks but payouts still enabled!", inconsistentUsers);
            } else {
                logger.info("Chargeback/Payout consistency check passed.");
            }
        } catch (Exception e) {
            logger.error("Failed to perform Chargeback/Payout consistency check: {}", e.getMessage());
        }

        logger.info("Payout state validation completed.");
    }
}
