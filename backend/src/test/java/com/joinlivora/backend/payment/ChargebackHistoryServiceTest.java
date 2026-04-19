package com.joinlivora.backend.payment;

import com.joinlivora.backend.chargeback.model.ChargebackCase;
import com.joinlivora.backend.chargeback.repository.ChargebackCaseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChargebackHistoryServiceTest {

    @Mock
    private ChargebackCaseRepository chargebackCaseRepository;

    @InjectMocks
    private ChargebackHistoryService chargebackHistoryService;

    private final Long userId = 1L;

    @Test
    void testGetChargebackRiskScore_NoHistory() {
        when(chargebackCaseRepository.findAllByUserId(new UUID(0L, userId))).thenReturn(Collections.emptyList());

        int score = chargebackHistoryService.getChargebackRiskScore(userId);

        assertEquals(0, score);
    }

    @Test
    void testGetChargebackRiskScore_OneChargeback() {
        when(chargebackCaseRepository.findAllByUserId(new UUID(0L, userId))).thenReturn(List.of(new ChargebackCase()));

        int score = chargebackHistoryService.getChargebackRiskScore(userId);

        assertEquals(20, score);
    }

    @Test
    void testGetChargebackRiskScore_MultipleChargebacks() {
        when(chargebackCaseRepository.findAllByUserId(new UUID(0L, userId))).thenReturn(List.of(new ChargebackCase(), new ChargebackCase()));

        int score = chargebackHistoryService.getChargebackRiskScore(userId);

        assertEquals(40, score);
    }

    @Test
    void testGetChargebackRiskScore_CappedAt100() {
        when(chargebackCaseRepository.findAllByUserId(new UUID(0L, userId))).thenReturn(List.of(
                new ChargebackCase(), new ChargebackCase(), new ChargebackCase(),
                new ChargebackCase(), new ChargebackCase(), new ChargebackCase()));

        int score = chargebackHistoryService.getChargebackRiskScore(userId);

        assertEquals(100, score);
    }
}
