package com.joinlivora.backend.payment;

import com.joinlivora.backend.fraud.model.RiskLevel;
import com.joinlivora.backend.payment.dto.RiskEscalationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChargebackRiskEscalationServiceTest {

    @Mock
    private ChargebackRepository chargebackRepository;

    @InjectMocks
    private ChargebackRiskEscalationService riskEscalationService;

    private final Long creatorId = 100L;

    @Test
    void testEvaluateEscalation_NoChargebacks() {
        when(chargebackRepository.findAllByCreatorId(creatorId)).thenReturn(Collections.emptyList());

        RiskEscalationResult result = riskEscalationService.evaluateEscalation(creatorId);

        assertEquals(RiskLevel.LOW, result.getRiskLevel());
        assertTrue(result.getActions().isEmpty());
    }

    @Test
    void testEvaluateEscalation_OneChargeback() {
        Chargeback cb = Chargeback.builder()
                .createdAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .build();
        when(chargebackRepository.findAllByCreatorId(creatorId)).thenReturn(List.of(cb));

        RiskEscalationResult result = riskEscalationService.evaluateEscalation(creatorId);

        assertEquals(RiskLevel.MEDIUM, result.getRiskLevel());
        assertTrue(result.getActions().isEmpty());
    }

    @Test
    void testEvaluateEscalation_TwoChargebacksWithin30Days() {
        Chargeback cb1 = Chargeback.builder()
                .createdAt(Instant.now().minus(5, ChronoUnit.DAYS))
                .build();
        Chargeback cb2 = Chargeback.builder()
                .createdAt(Instant.now().minus(10, ChronoUnit.DAYS))
                .build();
        when(chargebackRepository.findAllByCreatorId(creatorId)).thenReturn(List.of(cb1, cb2));

        RiskEscalationResult result = riskEscalationService.evaluateEscalation(creatorId);

        assertEquals(RiskLevel.HIGH, result.getRiskLevel());
        assertTrue(result.getActions().contains("PAYOUT_HOLD"));
        assertTrue(result.getActions().contains("MANUAL_REVIEW"));
    }

    @Test
    void testEvaluateEscalation_TwoChargebacksButOneOld() {
        Chargeback cb1 = Chargeback.builder()
                .createdAt(Instant.now().minus(5, ChronoUnit.DAYS))
                .build();
        Chargeback cb2 = Chargeback.builder()
                .createdAt(Instant.now().minus(40, ChronoUnit.DAYS))
                .build();
        when(chargebackRepository.findAllByCreatorId(creatorId)).thenReturn(List.of(cb1, cb2));

        RiskEscalationResult result = riskEscalationService.evaluateEscalation(creatorId);

        // Still MEDIUM because only 1 is recent.
        assertEquals(RiskLevel.MEDIUM, result.getRiskLevel());
        assertTrue(result.getActions().isEmpty());
    }
}








