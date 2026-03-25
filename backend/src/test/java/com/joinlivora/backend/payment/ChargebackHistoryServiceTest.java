package com.joinlivora.backend.payment;

import org.junit.jupiter.api.BeforeEach;
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
    private ChargebackRepository chargebackRepository;

    @InjectMocks
    private ChargebackHistoryService chargebackHistoryService;

    private Long userId = 1L;

    @Test
    void testGetChargebackRiskScore_NoHistory() {
        when(chargebackRepository.findAllByUserId(new UUID(0L, userId))).thenReturn(Collections.emptyList());
        
        int score = chargebackHistoryService.getChargebackRiskScore(userId);
        
        assertEquals(0, score);
    }

    @Test
    void testGetChargebackRiskScore_OneChargeback() {
        when(chargebackRepository.findAllByUserId(new UUID(0L, userId))).thenReturn(List.of(new Chargeback()));
        
        int score = chargebackHistoryService.getChargebackRiskScore(userId);
        
        assertEquals(20, score);
    }

    @Test
    void testGetChargebackRiskScore_MultipleChargebacks() {
        when(chargebackRepository.findAllByUserId(new UUID(0L, userId))).thenReturn(List.of(new Chargeback(), new Chargeback()));
        
        int score = chargebackHistoryService.getChargebackRiskScore(userId);
        
        assertEquals(40, score);
    }

    @Test
    void testGetChargebackRiskScore_CappedAt100() {
        when(chargebackRepository.findAllByUserId(new UUID(0L, userId))).thenReturn(List.of(
                new Chargeback(), new Chargeback(), new Chargeback(), 
                new Chargeback(), new Chargeback(), new Chargeback()));
        
        int score = chargebackHistoryService.getChargebackRiskScore(userId);
        
        assertEquals(100, score);
    }
}








