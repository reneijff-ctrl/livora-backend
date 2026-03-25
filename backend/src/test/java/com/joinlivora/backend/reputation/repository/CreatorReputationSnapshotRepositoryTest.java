package com.joinlivora.backend.reputation.repository;

import com.joinlivora.backend.reputation.model.CreatorReputationSnapshot;
import com.joinlivora.backend.reputation.model.ReputationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class CreatorReputationSnapshotRepositoryTest {

    @Autowired
    private CreatorReputationSnapshotRepository repository;

    @Test
    void testSaveAndFind() {
        UUID creatorId = UUID.randomUUID();
        CreatorReputationSnapshot snapshot = CreatorReputationSnapshot.builder()
                .creatorId(creatorId)
                .currentScore(85)
                .status(ReputationStatus.TRUSTED)
                .lastPositiveEventAt(Instant.now())
                .build();

        CreatorReputationSnapshot saved = repository.save(snapshot);
        assertEquals(creatorId, saved.getCreatorId());
        assertNotNull(saved.getUpdatedAt());

        CreatorReputationSnapshot found = repository.findById(creatorId).orElseThrow();
        assertEquals(85, found.getCurrentScore());
        assertEquals(ReputationStatus.TRUSTED, found.getStatus());
    }

    @Test
    void testUpdate() throws InterruptedException {
        UUID creatorId = UUID.randomUUID();
        CreatorReputationSnapshot snapshot = CreatorReputationSnapshot.builder()
                .creatorId(creatorId)
                .currentScore(50)
                .status(ReputationStatus.NORMAL)
                .build();

        CreatorReputationSnapshot saved = repository.saveAndFlush(snapshot);
        Instant updatedAtBefore = saved.getUpdatedAt();

        Thread.sleep(10); // Ensure time difference

        saved.setCurrentScore(45);
        saved.setStatus(ReputationStatus.WATCHED);
        saved.setLastDecayAt(Instant.now());
        
        CreatorReputationSnapshot updated = repository.saveAndFlush(saved);

        assertTrue(updated.getUpdatedAt().isAfter(updatedAtBefore));
        assertEquals(45, updated.getCurrentScore());
        assertEquals(ReputationStatus.WATCHED, updated.getStatus());
        assertNotNull(updated.getLastDecayAt());
    }
}








