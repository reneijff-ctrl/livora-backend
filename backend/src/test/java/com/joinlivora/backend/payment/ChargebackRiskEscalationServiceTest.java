package com.joinlivora.backend.payment;

import com.joinlivora.backend.chargeback.model.ChargebackCase;
import com.joinlivora.backend.chargeback.repository.ChargebackCaseRepository;
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
    private ChargebackCaseRepository chargebackCaseRepository;

    @InjectMocks
    private ChargebackRiskEscalationService riskEscalationService;

    private final Long creatorId = 100L;

    @Test
    void testEvaluateEscalation_NoChargebacks() {
        when(chargebackCaseRepository.findAllByCreatorId(creatorId)).thenReturn(Collections.emptyList());

        RiskEscalationResult result = riskEscalationService.evaluateEscalation(creatorId);
        assertEquals(RiskLevel.LOW, result.getRiskLevel());
        assertTrue(result.getActions().isEmpty());
    }

    @Test
    void testEvaluateEscalation_OneChargeback() {
        ChargebackCase cb = new ChargebackCase();
        cb.setCreatedAt(Instant.now().minus(1, ChronoUnit.DAYS));
        when(chargebackCaseRepository.findAllByCreatorId(creatorId)).thenReturn(List.of(cb));

        RiskEscalationResult result = riskEscalationService.evaluateEscalation(creatorId);
        assertEquals(RiskLevel.MEDIUM, result.getRiskLevel());
        assertTrue(result.getActions().isEmpty());
    }

    @Test
    void testEvaluateEscalation_TwoChargebacksWithin30Days() {
        ChargebackCase cb1 = new ChargebackCase();
        cb1.setCreatedAt(Instant.now().minus(5, ChronoUnit.DAYS));
        ChargebackCase cb2 = new ChargebackCase();
        cb2.setCreatedAt(Instant.now().minus(10, ChronoUnit.DAYS));
        when(chargebackCaseRepository.findAllByCreatorId(creatorId)).thenReturn(List.of(cb1, cb2));

        RiskEscalationResult result = riskEscalationService.evaluateEscalation(creatorId);
        assertEquals(RiskLevel.HIGH, result.getRiskLevel());
        assertTrue(result.getActions().contains("PAYOUT_HOLD"));
        assertTrue(result.getActions().contains("MANUAL_REVIEW"));
    }

    @Test
    void testEvaluateEscalation_TwoChargebacksButOneOld() {
        ChargebackCase cb1 = new ChargebackCase();
        cb1.setCreatedAt(Instant.now().minus(5, ChronoUnit.DAYS));
        ChargebackCase cb2 = new ChargebackCase();
        cb2.setCreatedAt(Instant.now().minus(40, ChronoUnit.DAYS));
        when(chargebackCaseRepository.findAllByCreatorId(creatorId)).thenReturn(List.of(cb1, cb2));

        RiskEscalationResult result = riskEscalationService.evaluateEscalation(creatorId);
        // Still MEDIUM because only 1 is recent.
        assertEquals(RiskLevel.MEDIUM, result.getRiskLevel());
        assertTrue(result.getActions().isEmpty());
    }
}
