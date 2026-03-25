package com.joinlivora.backend.reputation.job;

import com.joinlivora.backend.reputation.model.CreatorReputationSnapshot;
import com.joinlivora.backend.reputation.model.ReputationStatus;
import com.joinlivora.backend.reputation.repository.CreatorReputationSnapshotRepository;
import com.joinlivora.backend.reputation.repository.ReputationEventRepository;
import com.joinlivora.backend.reputation.service.ReputationCalculationService;
import com.joinlivora.backend.reputation.service.ReputationDecayService;
import com.joinlivora.backend.reputation.service.ReputationAuditService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@Import({ReputationDecayJob.class, ReputationDecayService.class, ReputationCalculationService.class, ReputationAuditService.class})
class ReputationDecayIntegrationTest {

    @Autowired
    private ReputationDecayJob decayJob;

    @Autowired
    private CreatorReputationSnapshotRepository snapshotRepository;

    @Autowired
    private ReputationEventRepository eventRepository;

    @Test
    void testDailyDecayFlow() {
        UUID c1 = UUID.randomUUID();
        snapshotRepository.save(CreatorReputationSnapshot.builder()
                .creatorId(c1)
                .currentScore(50)
                .status(ReputationStatus.NORMAL)
                .lastPositiveEventAt(Instant.now().minus(8, ChronoUnit.DAYS))
                .build());

        UUID c2 = UUID.randomUUID();
        snapshotRepository.save(CreatorReputationSnapshot.builder()
                .creatorId(c2)
                .currentScore(50)
                .status(ReputationStatus.NORMAL)
                .lastPositiveEventAt(Instant.now().minus(31, ChronoUnit.DAYS))
                .build());

        UUID c3 = UUID.randomUUID();
        snapshotRepository.save(CreatorReputationSnapshot.builder()
                .creatorId(c3)
                .currentScore(10) // Already at floor
                .status(ReputationStatus.RESTRICTED)
                .lastPositiveEventAt(Instant.now().minus(31, ChronoUnit.DAYS))
                .build());

        decayJob.run();

        assertEquals(49, snapshotRepository.findById(c1).get().getCurrentScore());
        assertEquals(48, snapshotRepository.findById(c2).get().getCurrentScore());
        assertEquals(10, snapshotRepository.findById(c3).get().getCurrentScore());
        
        // Check that events were recorded
        long decayEvents = eventRepository.findAll().stream()
                .filter(e -> e.getType().name().equals("DECAY"))
                .count();
        assertEquals(2, decayEvents);
    }
}









