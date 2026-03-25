package com.joinlivora.backend.payout;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class CreatorPayoutRepositoryTest {

    @Autowired
    private CreatorPayoutRepository repository;

    @Autowired
    private org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager entityManager;

    @Test
    void testSaveAndFind() {
        UUID creatorId = UUID.randomUUID();
        CreatorPayout payout = CreatorPayout.builder()
                .creatorId(creatorId)
                .amount(new BigDecimal("50.00"))
                .currency("EUR")
                .status(PayoutStatus.PENDING)
                .build();

        CreatorPayout saved = repository.save(payout);
        assertNotNull(saved.getId());
        assertNotNull(saved.getCreatedAt());

        List<CreatorPayout> history = repository.findAllByCreatorIdOrderByCreatedAtDesc(creatorId);
        assertEquals(1, history.size());
        assertEquals(new BigDecimal("50.00"), history.get(0).getAmount());
        assertEquals("EUR", history.get(0).getCurrency());
        assertEquals(PayoutStatus.PENDING, history.get(0).getStatus());
    }

    @Test
    void testFindByStatus() {
        UUID creatorId = UUID.randomUUID();
        repository.save(CreatorPayout.builder()
                .creatorId(creatorId)
                .amount(new BigDecimal("10.00"))
                .currency("EUR")
                .status(PayoutStatus.COMPLETED)
                .build());

        repository.save(CreatorPayout.builder()
                .creatorId(creatorId)
                .amount(new BigDecimal("20.00"))
                .currency("EUR")
                .status(PayoutStatus.PENDING)
                .build());

        List<CreatorPayout> pending = repository.findAllByStatus(PayoutStatus.PENDING);
        assertEquals(1, pending.size());
        assertEquals(new BigDecimal("20.00"), pending.get(0).getAmount());
    }

    @Test
    void testAmountImmutability() {
        UUID creatorId = UUID.randomUUID();
        CreatorPayout payout = CreatorPayout.builder()
                .creatorId(creatorId)
                .amount(new BigDecimal("50.00"))
                .currency("EUR")
                .status(PayoutStatus.PENDING)
                .build();

        CreatorPayout saved = repository.saveAndFlush(payout);
        UUID id = saved.getId();

        // Try to update amount
        saved.setAmount(new BigDecimal("100.00"));
        saved.setStatus(PayoutStatus.COMPLETED);
        repository.saveAndFlush(saved);

        // Clear persistence context to force reload from DB
        entityManager.clear();

        repository.findById(id).ifPresent(found -> {
            assertEquals(new BigDecimal("50.00"), found.getAmount()); // Should NOT have changed
            assertEquals(PayoutStatus.COMPLETED, found.getStatus()); // Should have changed
        });
    }
}








