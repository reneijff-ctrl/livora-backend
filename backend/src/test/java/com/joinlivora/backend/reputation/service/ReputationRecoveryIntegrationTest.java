package com.joinlivora.backend.reputation.service;

import com.joinlivora.backend.reputation.model.*;
import com.joinlivora.backend.reputation.repository.CreatorReputationSnapshotRepository;
import com.joinlivora.backend.reputation.repository.ReputationEventRepository;
import com.joinlivora.backend.reputation.service.ReputationAuditService;
import com.joinlivora.backend.reputation.service.ReputationCalculationService;
import com.joinlivora.backend.reputation.service.ReputationRecoveryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
@ActiveProfiles("test")
@Import({ReputationRecoveryService.class, ReputationCalculationService.class, ReputationAuditService.class})
class ReputationRecoveryIntegrationTest {

    @Autowired
    private ReputationRecoveryService recoveryService;

    @Autowired
    private CreatorReputationSnapshotRepository snapshotRepository;

    @Autowired
    private ReputationEventRepository eventRepository;

    @Test
    void testFullRecoveryFlow() {
        // 1. Setup creator
        UUID creatorId = UUID.randomUUID();
        CreatorReputationSnapshot snapshot = CreatorReputationSnapshot.builder()
                .creatorId(creatorId)
                .currentScore(50)
                .status(ReputationStatus.NORMAL)
                .updatedAt(Instant.now())
                .build();
        snapshotRepository.save(snapshot);

        // 2. Add activity (5 tips in the last 2 days)
        for (int i = 0; i < 5; i++) {
            eventRepository.save(ReputationEvent.builder()
                    .creatorId(creatorId)
                    .type(ReputationEventType.TIP)
                    .deltaScore(2)
                    .source(ReputationEventSource.SYSTEM)
                    .createdAt(Instant.now().minus(2, ChronoUnit.DAYS))
                    .build());
        }

        // 3. Ensure no recovery recently
        // (None added)

        // 4. Run recovery
        recoveryService.processRecovery(snapshot);

        // 5. Verify
        CreatorReputationSnapshot updated = snapshotRepository.findById(creatorId).orElseThrow();
        assertEquals(55, updated.getCurrentScore());
        assertEquals(ReputationStatus.NORMAL, updated.getStatus());

        // 6. Run again - should not increase (max once per week)
        recoveryService.processRecovery(updated);
        assertEquals(55, snapshotRepository.findById(creatorId).get().getCurrentScore());
    }

    @Test
    void testRecoveryBlockedByFraud() {
        UUID creatorId = UUID.randomUUID();
        CreatorReputationSnapshot snapshot = CreatorReputationSnapshot.builder()
                .creatorId(creatorId)
                .currentScore(50)
                .status(ReputationStatus.NORMAL)
                .updatedAt(Instant.now())
                .build();
        snapshotRepository.save(snapshot);

        // Activity threshold met
        for (int i = 0; i < 5; i++) {
            eventRepository.save(ReputationEvent.builder()
                    .creatorId(creatorId)
                    .type(ReputationEventType.TIP)
                    .deltaScore(2)
                    .source(ReputationEventSource.SYSTEM)
                    .createdAt(Instant.now().minus(2, ChronoUnit.DAYS))
                    .build());
        }

        // Recent Chargeback (3 days ago)
        eventRepository.save(ReputationEvent.builder()
                .creatorId(creatorId)
                .type(ReputationEventType.CHARGEBACK)
                .deltaScore(-20)
                .source(ReputationEventSource.SYSTEM)
                .createdAt(Instant.now().minus(3, ChronoUnit.DAYS))
                .build());

        // Run recovery
        recoveryService.processRecovery(snapshot);

        // Verify no change
        assertEquals(50, snapshotRepository.findById(creatorId).get().getCurrentScore());
    }
}








