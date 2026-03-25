package com.joinlivora.backend.payout;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class CreatorPayoutSettingsRepositoryTest {

    @Autowired
    private CreatorPayoutSettingsRepository repository;

    @Test
    void testSaveAndFind() {
        UUID creatorId = UUID.randomUUID();
        CreatorPayoutSettings settings = CreatorPayoutSettings.builder()
                .creatorId(creatorId)
                .payoutMethod(PayoutMethod.STRIPE_CARD)
                .stripeAccountId("acct_123")
                .minimumPayoutAmount(new BigDecimal("100.00"))
                .enabled(true)
                .build();

        CreatorPayoutSettings saved = repository.save(settings);
        assertNotNull(saved.getId());
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());

        CreatorPayoutSettings found = repository.findByCreatorId(creatorId).orElseThrow();
        assertEquals("acct_123", found.getStripeAccountId());
        assertEquals(PayoutMethod.STRIPE_CARD, found.getPayoutMethod());
    }
}








