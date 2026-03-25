package com.joinlivora.backend.payment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ChargebackRepositoryTest {

    @Autowired
    private ChargebackRepository chargebackRepository;

    @Test
    void testSaveAndFindChargeback() {
        UUID transactionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String stripeChargeId = "ch_123456789";

        Chargeback chargeback = Chargeback.builder()
                .transactionId(transactionId)
                .userId(userId)
                .stripeChargeId(stripeChargeId)
                .reason("fraudulent")
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .resolved(false)
                .status(ChargebackStatus.RECEIVED)
                .build();

        Chargeback saved = chargebackRepository.save(chargeback);
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();

        Chargeback found = chargebackRepository.findById(saved.getId()).orElse(null);
        assertThat(found).isNotNull();
        assertThat(found.getTransactionId()).isEqualTo(transactionId);
        assertThat(found.getUserId()).isEqualTo(userId);
        assertThat(found.getStripeChargeId()).isEqualTo(stripeChargeId);
        assertThat(found.getReason()).isEqualTo("fraudulent");
        assertThat(found.getAmount()).isEqualByComparingTo("100.00");
        assertThat(found.getCurrency()).isEqualTo("USD");
        assertThat(found.isResolved()).isFalse();
    }

    @Test
    void testFindByStripeChargeId() {
        String stripeChargeId = "ch_test_987";
        Chargeback chargeback = Chargeback.builder()
                .transactionId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .stripeChargeId(stripeChargeId)
                .amount(BigDecimal.TEN)
                .currency("USD")
                .status(ChargebackStatus.RECEIVED)
                .build();
        chargebackRepository.save(chargeback);

        assertThat(chargebackRepository.findByStripeChargeId(stripeChargeId)).isPresent();
        assertThat(chargebackRepository.findByStripeChargeId("non_existent")).isEmpty();
    }
}








