package com.joinlivora.backend.chargeback.repository;

import com.joinlivora.backend.chargeback.model.ChargebackCase;
import com.joinlivora.backend.chargeback.model.ChargebackStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class ChargebackCaseRepositoryTest {

    @Autowired
    private ChargebackCaseRepository repository;

    @Test
    void findByPaymentIntentId_ShouldReturnCase() {
        // Given
        String piId = "pi_test_123";
        UUID userId = UUID.randomUUID();
        ChargebackCase c = ChargebackCase.builder()
                .userId(userId)
                .paymentIntentId(piId)
                .amount(new BigDecimal("50.00"))
                .currency("EUR")
                .status(ChargebackStatus.OPEN)
                .fraudScoreAtTime(20)
                .build();
        repository.save(c);

        // When
        Optional<ChargebackCase> found = repository.findByPaymentIntentId(piId);

        // Then
        assertTrue(found.isPresent());
        assertEquals(piId, found.get().getPaymentIntentId());
        assertEquals(userId, found.get().getUserId());
    }

    @Test
    void findByPaymentIntentId_WhenNotFound_ShouldReturnEmpty() {
        // When
        Optional<ChargebackCase> found = repository.findByPaymentIntentId("non_existent");

        // Then
        assertFalse(found.isPresent());
    }
}








