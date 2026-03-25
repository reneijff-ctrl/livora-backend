package com.joinlivora.backend.payout;

import com.joinlivora.backend.payout.dto.PayoutFrequency;
import com.joinlivora.backend.payout.dto.PayoutLimit;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class PayoutLimitPolicyTest {

    private final PayoutLimitPolicy policy = new PayoutLimitPolicy();

    @Test
    void testLowRisk() {
        PayoutLimit limit = policy.getLimit(25);
        assertNull(limit.getMaxPayoutAmount());
        assertEquals(PayoutFrequency.NO_LIMIT, limit.getPayoutFrequency());
        assertTrue(limit.getReason().contains("Risk score < 30"));
    }

    @Test
    void testMediumRisk() {
        PayoutLimit limit = policy.getLimit(45);
        assertEquals(new BigDecimal("100.00"), limit.getMaxPayoutAmount());
        assertEquals(PayoutFrequency.DAILY, limit.getPayoutFrequency());
        assertTrue(limit.getReason().contains("Risk score 30-59"));
    }

    @Test
    void testHighRisk() {
        PayoutLimit limit = policy.getLimit(70);
        assertEquals(new BigDecimal("500.00"), limit.getMaxPayoutAmount());
        assertEquals(PayoutFrequency.WEEKLY, limit.getPayoutFrequency());
        assertTrue(limit.getReason().contains("Risk score 60-79"));
    }

    @Test
    void testCriticalRisk() {
        PayoutLimit limit = policy.getLimit(85);
        assertEquals(BigDecimal.ZERO, limit.getMaxPayoutAmount());
        assertEquals(PayoutFrequency.PAUSED, limit.getPayoutFrequency());
        assertTrue(limit.getReason().contains("Risk score >= 80"));
    }
}








