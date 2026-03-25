package com.joinlivora.backend.payout;

import com.joinlivora.backend.payout.dto.PayoutHoldDecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PayoutHoldDecisionServiceTest {

    private PayoutHoldDecisionService service;
    private PayoutHoldProperties properties;

    @BeforeEach
    void setUp() {
        properties = new PayoutHoldProperties();
        // Initialize with default values matching requirements
        properties.setNone(new PayoutHoldProperties.Thresholds(20, 0.5, 0));
        properties.setShortHold(new PayoutHoldProperties.Thresholds(40, 0.5, 3));
        properties.setMediumHold(new PayoutHoldProperties.Thresholds(60, 0.5, 7));
        properties.setLongHold(new PayoutHoldProperties.Thresholds(100, 1.0, 14));
        
        service = new PayoutHoldDecisionService(properties);
    }

    @Test
    void decide_RiskScoreAbove60_ShouldReturnLongHold() {
        PayoutHoldDecision decision = service.decide(65, 0.0, 365, BigDecimal.valueOf(10000));
        assertEquals(HoldLevel.LONG, decision.getHoldLevel());
        assertEquals(14, decision.getHoldDays());
        assertEquals("High risk score or elevated chargeback rate", decision.getReason());
    }

    @Test
    void decide_ChargebackRateAbove1Percent_ShouldReturnLongHold() {
        PayoutHoldDecision decision = service.decide(10, 1.5, 365, BigDecimal.valueOf(10000));
        assertEquals(HoldLevel.LONG, decision.getHoldLevel());
        assertEquals(14, decision.getHoldDays());
    }

    @Test
    void decide_RiskScore40to60_ShouldReturnMediumHold() {
        PayoutHoldDecision decision = service.decide(45, 0.1, 365, BigDecimal.valueOf(10000));
        assertEquals(HoldLevel.MEDIUM, decision.getHoldLevel());
        assertEquals(7, decision.getHoldDays());
        assertEquals("Moderate risk score", decision.getReason());
    }

    @Test
    void decide_RiskScore20to40_ShouldReturnShortHold() {
        PayoutHoldDecision decision = service.decide(25, 0.1, 365, BigDecimal.valueOf(10000));
        assertEquals(HoldLevel.SHORT, decision.getHoldLevel());
        assertEquals(3, decision.getHoldDays());
        assertEquals("Minor risk detected", decision.getReason());
    }

    @Test
    void decide_LowRisk_ShouldReturnNoHold() {
        PayoutHoldDecision decision = service.decide(10, 0.1, 365, BigDecimal.valueOf(10000));
        assertEquals(HoldLevel.NONE, decision.getHoldLevel());
        assertEquals(0, decision.getHoldDays());
        assertEquals("Low risk profile", decision.getReason());
    }
}








