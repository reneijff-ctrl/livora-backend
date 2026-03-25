package com.joinlivora.backend.payout;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class PayoutRiskRepositoryTest {

    @Autowired
    private PayoutRiskRepository repository;

    @Test
    void testSaveAndFind() {
        Long userId = 100L;
        PayoutRisk risk = PayoutRisk.builder()
                .userId(userId)
                .riskScore(75)
                .reasons("[\"High chargeback rate\", \"Multiple IPs\"]")
                .build();

        PayoutRisk saved = repository.save(risk);
        assertNotNull(saved.getId());
        assertNotNull(saved.getLastEvaluatedAt());

        PayoutRisk found = repository.findFirstByUserIdOrderByLastEvaluatedAtDesc(userId).orElse(null);
        assertNotNull(found);
        assertEquals(75, found.getRiskScore());
        assertEquals("[\"High chargeback rate\", \"Multiple IPs\"]", found.getReasons());
        assertEquals(userId, found.getUserId());
    }

    @Test
    void testUpdate() {
        Long userId = 200L;
        PayoutRisk risk = PayoutRisk.builder()
                .userId(userId)
                .riskScore(50)
                .build();

        PayoutRisk saved = repository.save(risk);
        Instant firstEvaluated = saved.getLastEvaluatedAt();
        assertNotNull(firstEvaluated);

        Instant now = Instant.now();
        saved.setRiskScore(60);
        saved.setLastEvaluatedAt(now);
        repository.save(saved);

        PayoutRisk updated = repository.findFirstByUserIdOrderByLastEvaluatedAtDesc(userId).orElse(null);
        assertNotNull(updated);
        assertEquals(60, updated.getRiskScore());
        assertEquals(now, updated.getLastEvaluatedAt());
    }
}








