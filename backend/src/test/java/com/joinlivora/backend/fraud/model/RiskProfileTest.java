package com.joinlivora.backend.fraud.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RiskProfileTest {

    @Test
    void testRiskProfileCreation() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        
        RiskProfile profile = RiskProfile.builder()
                .userId(userId)
                .riskScore(45)
                .trustScore(80)
                .lastEvaluatedAt(now)
                .build();

        assertEquals(userId, profile.getUserId());
        assertEquals(45, profile.getRiskScore());
        assertEquals(80, profile.getTrustScore());
        assertEquals(now, profile.getLastEvaluatedAt());
    }

    @Test
    void testNoArgsConstructorAndSetters() {
        RiskProfile profile = new RiskProfile();
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();

        profile.setUserId(userId);
        profile.setRiskScore(10);
        profile.setTrustScore(90);
        profile.setLastEvaluatedAt(now);

        assertEquals(userId, profile.getUserId());
        assertEquals(10, profile.getRiskScore());
        assertEquals(90, profile.getTrustScore());
        assertEquals(now, profile.getLastEvaluatedAt());
    }
}








