package com.joinlivora.backend.payouts;

import com.joinlivora.backend.payouts.model.CreatorAccount;
import com.joinlivora.backend.payouts.model.PayoutFreezeHistory;
import com.joinlivora.backend.payouts.repository.CreatorAccountRepository;
import com.joinlivora.backend.payouts.repository.PayoutFreezeHistoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class PayoutFreezePersistenceTest {

    @Autowired
    private CreatorAccountRepository creatorAccountRepository;

    @Autowired
    private PayoutFreezeHistoryRepository payoutFreezeHistoryRepository;

    @Test
    void testSaveAndRetrieveCreatorAccount() {
        UUID creatorId = UUID.randomUUID();
        CreatorAccount account = CreatorAccount.builder()
                .creatorId(creatorId)
                .payoutFrozen(true)
                .freezeReason("Suspicious activity")
                .frozenAt(Instant.now())
                .build();

        CreatorAccount saved = creatorAccountRepository.save(account);
        assertThat(saved.getId()).isNotNull();

        CreatorAccount retrieved = creatorAccountRepository.findByCreatorId(creatorId).orElseThrow();
        assertThat(retrieved.isPayoutFrozen()).isTrue();
        assertThat(retrieved.getFreezeReason()).isEqualTo("Suspicious activity");
    }

    @Test
    void testSaveAndRetrievePayoutFreezeHistory() {
        UUID creatorId = UUID.randomUUID();
        PayoutFreezeHistory history = PayoutFreezeHistory.builder()
                .creatorId(creatorId)
                .reason("Fraud detected")
                .triggeredBy("SYSTEM")
                .build();

        PayoutFreezeHistory saved = payoutFreezeHistoryRepository.save(history);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();

        var retrieved = payoutFreezeHistoryRepository.findAllByCreatorIdOrderByCreatedAtDesc(creatorId);
        assertThat(retrieved).hasSize(1);
        assertThat(retrieved.get(0).getReason()).isEqualTo("Fraud detected");
        assertThat(retrieved.get(0).getTriggeredBy()).isEqualTo("SYSTEM");
    }
}








