package com.joinlivora.backend.fraud.repository;

import com.joinlivora.backend.fraud.model.FraudScore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class FraudScoreRepositoryTest {

    @Autowired
    private FraudScoreRepository repository;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void findByUserId_ShouldReturnFraudScore() {
        Long userId = 123L;
        FraudScore fraudScore = FraudScore.builder()
                .userId(userId)
                .score(75)
                .riskLevel("HIGH")
                .calculatedAt(Instant.now())
                .build();

        repository.save(fraudScore);

        Optional<FraudScore> found = repository.findByUserId(userId);

        assertThat(found).isPresent();
        assertThat(found.get().getUserId()).isEqualTo(userId);
        assertThat(found.get().getScore()).isEqualTo(75);
        assertThat(found.get().getRiskLevel()).isEqualTo("HIGH");
    }

    @Test
    void findByUserId_WhenNotFound_ShouldReturnEmpty() {
        Optional<FraudScore> found = repository.findByUserId(999L);
        assertThat(found).isEmpty();
    }
}








