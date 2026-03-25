package com.joinlivora.backend.fraud.repository;

import com.joinlivora.backend.fraud.model.FraudDecisionLevel;
import com.joinlivora.backend.fraud.model.UserRiskState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class UserRiskStateRepositoryTest {

    @Autowired
    private UserRiskStateRepository repository;

    @Test
    void testSaveAndFind() {
        Long userId = 1L;
        Instant blockedUntil = Instant.now().plusSeconds(3600);

        UserRiskState state = UserRiskState.builder()
                .userId(userId)
                .currentRisk(FraudDecisionLevel.MEDIUM)
                .blockedUntil(blockedUntil)
                .paymentLocked(true)
                .build();

        repository.save(state);

        Optional<UserRiskState> found = repository.findById(userId);
        assertTrue(found.isPresent());
        assertEquals(FraudDecisionLevel.MEDIUM, found.get().getCurrentRisk());
        assertEquals(blockedUntil, found.get().getBlockedUntil());
        assertTrue(found.get().isPaymentLocked());
        assertNotNull(found.get().getUpdatedAt());
    }

    @Test
    void testUpdate() {
        Long userId = 2L;
        UserRiskState state = UserRiskState.builder()
                .userId(userId)
                .currentRisk(FraudDecisionLevel.LOW)
                .build();

        state = repository.saveAndFlush(state);
        Instant firstUpdate = state.getUpdatedAt();
        assertNotNull(firstUpdate);

        state.setCurrentRisk(FraudDecisionLevel.HIGH);
        repository.saveAndFlush(state);

        UserRiskState updated = repository.findById(userId).orElseThrow();
        assertEquals(FraudDecisionLevel.HIGH, updated.getCurrentRisk());
        assertTrue(updated.getUpdatedAt().isAfter(firstUpdate) || updated.getUpdatedAt().equals(firstUpdate));
        // Note: Instant.now() might be very close, but @PreUpdate should trigger.
    }

    @Test
    void testFindAllByBlockedUntilBefore() {
        Instant now = Instant.now();
        Long user1 = 3L;
        Long user2 = 4L;
        Long user3 = 5L;

        repository.save(UserRiskState.builder()
                .userId(user1)
                .currentRisk(FraudDecisionLevel.HIGH)
                .blockedUntil(now.minusSeconds(60))
                .build());

        repository.save(UserRiskState.builder()
                .userId(user2)
                .currentRisk(FraudDecisionLevel.HIGH)
                .blockedUntil(now.plusSeconds(60))
                .build());

        repository.save(UserRiskState.builder()
                .userId(user3)
                .currentRisk(FraudDecisionLevel.HIGH)
                .blockedUntil(null)
                .build());

        java.util.List<UserRiskState> expired = repository.findAllByBlockedUntilBefore(now);
        assertEquals(1, expired.size());
        assertEquals(user1, expired.get(0).getUserId());
    }
}








