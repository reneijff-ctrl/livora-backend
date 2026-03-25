package com.joinlivora.backend.fraud.model;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class FraudRiskAssessmentRepositoryTest {

    @Autowired
    private org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager entityManager;

    @Test
    void testSaveAndFind() {
        UUID userId = UUID.randomUUID();
        FraudRiskAssessment assessment = FraudRiskAssessment.builder()
                .userId(userId)
                .score(75)
                .riskLevel(RiskLevel.HIGH)
                .reasons("{\"factor\":\"velocity\",\"value\":\"high\"}")
                .build();

        FraudRiskAssessment saved = entityManager.persistAndFlush(assessment);
        assertNotNull(saved.getId());
        assertNotNull(saved.getCreatedAt());

        FraudRiskAssessment found = entityManager.find(FraudRiskAssessment.class, saved.getId());
        assertEquals(userId, found.getUserId());
        assertEquals(75, found.getScore());
        assertEquals(RiskLevel.HIGH, found.getRiskLevel());
        assertEquals("{\"factor\":\"velocity\",\"value\":\"high\"}", found.getReasons());
    }
}








