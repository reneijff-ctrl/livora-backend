package com.joinlivora.backend.payout;

import com.joinlivora.backend.fraud.model.RiskLevel;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class PayoutHoldRules {

    public static final int MEDIUM_HOLD_HOURS = 24;
    public static final int HIGH_HOLD_DAYS = 7;

    /**
     * Calculates the hold expiration timestamp based on risk level.
     * 
     * Rules:
     * - LOW: no hold (returns current time)
     * - MEDIUM: hold for 24 hours
     * - HIGH: hold for 7 days
     *
     * @param riskLevel the risk level
     * @param from the starting timestamp
     * @return the expiration timestamp
     */
    public static Instant calculateHoldUntil(RiskLevel riskLevel, Instant from) {
        if (riskLevel == null) {
            return from;
        }

        return switch (riskLevel) {
            case LOW -> from;
            case MEDIUM -> from.plus(MEDIUM_HOLD_HOURS, ChronoUnit.HOURS);
            case HIGH, CRITICAL -> from.plus(HIGH_HOLD_DAYS, ChronoUnit.DAYS);
        };
    }

    public static Instant calculateHoldUntil(RiskLevel riskLevel) {
        return calculateHoldUntil(riskLevel, Instant.now());
    }
}
