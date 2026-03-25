package com.joinlivora.backend.aml;

import com.joinlivora.backend.aml.model.RiskScore;
import com.joinlivora.backend.aml.repository.RiskScoreRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class RiskScorePersistenceTest {

    @Autowired
    private RiskScoreRepository riskScoreRepository;

    @Test
    void testSaveAndRetrieveRiskScore() {
        UUID userId = UUID.randomUUID();
        RiskScore riskScore = RiskScore.builder()
                .userId(userId)
                .score(75)
                .level("HIGH")
                .lastEvaluatedAt(Instant.now())
                .build();

        RiskScore saved = riskScoreRepository.save(riskScore);
        assertThat(saved.getId()).isNotNull();

        RiskScore retrieved = riskScoreRepository.findById(saved.getId()).orElseThrow();
        assertThat(retrieved.getUserId()).isEqualTo(userId);
        assertThat(retrieved.getScore()).isEqualTo(75);
        assertThat(retrieved.getLevel()).isEqualTo("HIGH");
        assertThat(retrieved.getLastEvaluatedAt()).isNotNull();
    }

    @Test
    void testFindByUserIdOrderByLastEvaluatedAtDesc() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.now();
        
        RiskScore older = RiskScore.builder()
                .userId(userId)
                .score(20)
                .level("LOW")
                .lastEvaluatedAt(now.minusSeconds(3600))
                .build();
        
        RiskScore newer = RiskScore.builder()
                .userId(userId)
                .score(85)
                .level("CRITICAL")
                .lastEvaluatedAt(now)
                .build();

        riskScoreRepository.save(older);
        riskScoreRepository.save(newer);

        RiskScore latest = riskScoreRepository.findTopByUserIdOrderByLastEvaluatedAtDesc(userId).orElseThrow();
        assertThat(latest.getScore()).isEqualTo(85);
        assertThat(latest.getLevel()).isEqualTo("CRITICAL");
    }
}








