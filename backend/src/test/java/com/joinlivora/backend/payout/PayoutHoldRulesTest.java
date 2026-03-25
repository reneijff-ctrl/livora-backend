package com.joinlivora.backend.payout;

import com.joinlivora.backend.fraud.model.RiskLevel;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

class PayoutHoldRulesTest {

    @Test
    void calculateHoldUntil_Low_ShouldReturnSameInstant() {
        Instant now = Instant.now();
        Instant result = PayoutHoldRules.calculateHoldUntil(RiskLevel.LOW, now);
        assertEquals(now, result);
    }

    @Test
    void calculateHoldUntil_Medium_ShouldReturn24HoursLater() {
        Instant now = Instant.now();
        Instant result = PayoutHoldRules.calculateHoldUntil(RiskLevel.MEDIUM, now);
        assertEquals(now.plus(24, ChronoUnit.HOURS), result);
    }

    @Test
    void calculateHoldUntil_High_ShouldReturn7DaysLater() {
        Instant now = Instant.now();
        Instant result = PayoutHoldRules.calculateHoldUntil(RiskLevel.HIGH, now);
        assertEquals(now.plus(7, ChronoUnit.DAYS), result);
    }

    @Test
    void calculateHoldUntil_Null_ShouldReturnSameInstant() {
        Instant now = Instant.now();
        Instant result = PayoutHoldRules.calculateHoldUntil(null, now);
        assertEquals(now, result);
    }

    @Test
    void calculateHoldUntil_WithoutInstant_ShouldWork() {
        Instant before = Instant.now().minus(1, ChronoUnit.SECONDS);
        Instant result = PayoutHoldRules.calculateHoldUntil(RiskLevel.MEDIUM);
        Instant after = Instant.now().plus(24, ChronoUnit.HOURS).plus(1, ChronoUnit.SECONDS);
        
        assertTrue(result.isAfter(before.plus(24, ChronoUnit.HOURS)));
        assertTrue(result.isBefore(after));
    }
}








