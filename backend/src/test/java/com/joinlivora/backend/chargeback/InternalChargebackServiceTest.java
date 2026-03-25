package com.joinlivora.backend.chargeback;

import com.joinlivora.backend.chargeback.model.ChargebackCase;
import com.joinlivora.backend.chargeback.model.ChargebackStatus;
import com.joinlivora.backend.chargeback.repository.ChargebackCaseRepository;
import com.joinlivora.backend.fraud.model.FraudDecision;
import com.joinlivora.backend.fraud.repository.FraudDecisionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InternalChargebackServiceTest {

    @Mock
    private ChargebackCaseRepository chargebackCaseRepository;
    @Mock
    private FraudDecisionRepository fraudDecisionRepository;

    @InjectMocks
    private InternalChargebackService chargebackService;

    private UUID userId = UUID.randomUUID();
    private String piId = "pi_123";
    private BigDecimal amount = new BigDecimal("50.00");
    private String currency = "EUR";
    private String reason = "fraudulent";

    @Test
    void registerChargeback_NewCase_ShouldSucceed() {
        // Given
        FraudDecision decision = FraudDecision.builder()
                .score(75)
                .build();

        when(chargebackCaseRepository.findByPaymentIntentId(piId)).thenReturn(Optional.empty());
        when(fraudDecisionRepository.findFirstByUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(Optional.of(decision));
        when(chargebackCaseRepository.save(any(ChargebackCase.class))).thenAnswer(i -> i.getArgument(0));

        // When
        ChargebackCase result = chargebackService.registerChargeback(userId, piId, amount, currency, reason);

        // Then
        assertNotNull(result);
        assertEquals(piId, result.getPaymentIntentId());
        assertEquals(userId, result.getUserId());
        assertEquals(75, result.getFraudScoreAtTime());
        assertEquals(ChargebackStatus.OPEN, result.getStatus());
        
        verify(chargebackCaseRepository).save(any(ChargebackCase.class));
    }

    @Test
    void registerChargeback_NoFraudDecision_ShouldDefaultToZeroScore() {
        // Given
        when(chargebackCaseRepository.findByPaymentIntentId(piId)).thenReturn(Optional.empty());
        when(fraudDecisionRepository.findFirstByUserIdOrderByCreatedAtDesc(any(UUID.class)))
                .thenReturn(Optional.empty());
        when(chargebackCaseRepository.save(any(ChargebackCase.class))).thenAnswer(i -> i.getArgument(0));

        // When
        ChargebackCase result = chargebackService.registerChargeback(userId, piId, amount, currency, reason);

        // Then
        assertNotNull(result);
        assertEquals(0, result.getFraudScoreAtTime());
        assertEquals(userId, result.getUserId());
        verify(chargebackCaseRepository).save(any(ChargebackCase.class));
    }

    @Test
    void registerChargeback_WhenAlreadyExists_ShouldReturnExisting() {
        // Given
        ChargebackCase existing = ChargebackCase.builder().paymentIntentId(piId).build();
        when(chargebackCaseRepository.findByPaymentIntentId(piId)).thenReturn(Optional.of(existing));

        // When
        ChargebackCase result = chargebackService.registerChargeback(userId, piId, amount, currency, reason);

        // Then
        assertSame(existing, result);
        verify(chargebackCaseRepository, never()).save(any());
    }

    @Test
    void updateStatus_ShouldSucceed() {
        // Given
        UUID id = UUID.randomUUID();
        ChargebackCase chargebackCase = ChargebackCase.builder().id(id).status(ChargebackStatus.OPEN).build();
        when(chargebackCaseRepository.findById(id)).thenReturn(Optional.of(chargebackCase));
        when(chargebackCaseRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // When
        ChargebackCase result = chargebackService.updateStatus(id, ChargebackStatus.UNDER_REVIEW);

        // Then
        assertEquals(ChargebackStatus.UNDER_REVIEW, result.getStatus());
        verify(chargebackCaseRepository).save(chargebackCase);
    }
}








