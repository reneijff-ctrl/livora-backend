package com.joinlivora.backend.payout;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class CreatorStripeAccountRepositoryTest {

    @Autowired
    private LegacyCreatorStripeAccountRepository repository;

    @Test
    void testSaveAndFind() {
        Long creatorId = 12345L;
        LegacyCreatorStripeAccount account = LegacyCreatorStripeAccount.builder()
                .creatorId(creatorId)
                .stripeAccountId("acct_123")
                .onboardingCompleted(true)
                .payoutsEnabled(true)
                .chargesEnabled(false)
                .build();

        LegacyCreatorStripeAccount saved = repository.save(account);
        assertNotNull(saved.getId());
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());

        Optional<LegacyCreatorStripeAccount> found = repository.findByCreatorId(creatorId);
        assertTrue(found.isPresent());
        assertEquals("acct_123", found.get().getStripeAccountId());
        assertTrue(found.get().isOnboardingCompleted());
        assertTrue(found.get().isPayoutsEnabled());
        assertFalse(found.get().isChargesEnabled());
    }

    @Test
    void testUpdateHooks() throws InterruptedException {
        LegacyCreatorStripeAccount account = LegacyCreatorStripeAccount.builder()
                .creatorId(999L)
                .stripeAccountId("acct_999")
                .build();

        LegacyCreatorStripeAccount saved = repository.saveAndFlush(account);
        Instant firstUpdate = saved.getUpdatedAt();

        Thread.sleep(10); // Ensure time difference

        saved.setPayoutsEnabled(true);
        LegacyCreatorStripeAccount updated = repository.saveAndFlush(saved);

        assertTrue(updated.getUpdatedAt().isAfter(firstUpdate));
    }
}








