package com.joinlivora.backend.payout;

import com.joinlivora.backend.payout.dto.PayoutFrequency;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class CreatorPayoutStateRepositoryTest {

    @Autowired
    private CreatorPayoutStateRepository repository;

    @Test
    void testSaveAndFind() {
        UUID creatorId = UUID.randomUUID();
        CreatorPayoutState state = CreatorPayoutState.builder()
                .creatorId(creatorId)
                .currentLimit(new BigDecimal("100.00"))
                .frequency(PayoutFrequency.DAILY)
                .status(PayoutStateStatus.ACTIVE)
                .build();

        CreatorPayoutState saved = repository.save(state);
        assertNotNull(saved.getId());
        assertNotNull(saved.getUpdatedAt());

        CreatorPayoutState found = repository.findByCreatorId(creatorId).orElseThrow();
        assertEquals(creatorId, found.getCreatorId());
        assertEquals(new BigDecimal("100.00"), found.getCurrentLimit());
        assertEquals(PayoutFrequency.DAILY, found.getFrequency());
        assertEquals(PayoutStateStatus.ACTIVE, found.getStatus());
    }

    @Test
    void testUpdate() throws InterruptedException {
        UUID creatorId = UUID.randomUUID();
        CreatorPayoutState state = CreatorPayoutState.builder()
                .creatorId(creatorId)
                .currentLimit(new BigDecimal("100.00"))
                .frequency(PayoutFrequency.DAILY)
                .status(PayoutStateStatus.ACTIVE)
                .build();

        CreatorPayoutState saved = repository.saveAndFlush(state);
        Instant updatedAtBefore = saved.getUpdatedAt();

        Thread.sleep(10); // Ensure time difference

        saved.setStatus(PayoutStateStatus.LIMITED);
        saved.setCurrentLimit(new BigDecimal("500.00"));
        saved.setFrequency(PayoutFrequency.WEEKLY);
        CreatorPayoutState updated = repository.saveAndFlush(saved);

        assertTrue(updated.getUpdatedAt().isAfter(updatedAtBefore));
        assertEquals(PayoutStateStatus.LIMITED, updated.getStatus());
        assertEquals(new BigDecimal("500.00"), updated.getCurrentLimit());
        assertEquals(PayoutFrequency.WEEKLY, updated.getFrequency());
    }
}








