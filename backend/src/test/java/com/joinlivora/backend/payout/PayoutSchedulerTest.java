package com.joinlivora.backend.payout;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PayoutSchedulerTest {

    @Mock
    private PayoutService payoutService;

    @Mock
    private com.joinlivora.backend.payouts.service.PayoutExecutionService payoutExecutionService;

    @Mock
    private CreatorPayoutSettingsRepository creatorPayoutSettingsRepository;

    @InjectMocks
    private PayoutScheduler payoutScheduler;

    @Test
    void executeDailyPayouts_ShouldExecuteEligiblePayouts() throws Exception {
        // Given
        UUID creatorId1 = UUID.randomUUID();
        CreatorPayoutSettings settings1 = CreatorPayoutSettings.builder()
                .creatorId(creatorId1)
                .minimumPayoutAmount(new BigDecimal("50.00"))
                .enabled(true)
                .build();

        UUID creatorId2 = UUID.randomUUID();
        CreatorPayoutSettings settings2 = CreatorPayoutSettings.builder()
                .creatorId(creatorId2)
                .minimumPayoutAmount(new BigDecimal("100.00"))
                .enabled(true)
                .build();

        when(creatorPayoutSettingsRepository.findAllByEnabledTrue()).thenReturn(List.of(settings1, settings2));
        
        // Creator 1 eligible: 60.00 > 50.00
        when(payoutService.calculateAvailablePayout(creatorId1)).thenReturn(new BigDecimal("60.00"));
        // Creator 2 not eligible: 30.00 < 100.00
        when(payoutService.calculateAvailablePayout(creatorId2)).thenReturn(new BigDecimal("30.00"));

        // When
        payoutScheduler.executeDailyPayouts();

        // Then
        verify(payoutExecutionService).executePayout(eq(creatorId1), eq(new BigDecimal("60.00")), eq("EUR"));
        verify(payoutExecutionService, never()).executePayout(eq(creatorId2), any(), any());
    }

    @Test
    void executeDailyPayouts_ShouldLogFailureAndContinueBatch() throws Exception {
        // Given
        UUID creatorId1 = UUID.randomUUID();
        CreatorPayoutSettings settings1 = CreatorPayoutSettings.builder()
                .creatorId(creatorId1)
                .enabled(true)
                .build();

        UUID creatorId2 = UUID.randomUUID();
        CreatorPayoutSettings settings2 = CreatorPayoutSettings.builder()
                .creatorId(creatorId2)
                .enabled(true)
                .build();

        when(creatorPayoutSettingsRepository.findAllByEnabledTrue()).thenReturn(List.of(settings1, settings2));
        
        when(payoutService.calculateAvailablePayout(any())).thenReturn(new BigDecimal("100.00"));

        // Fail first payout
        doThrow(new RuntimeException("Stripe error")).when(payoutExecutionService).executePayout(eq(creatorId1), any(), any());

        // When
        payoutScheduler.executeDailyPayouts();

        // Then
        verify(payoutExecutionService).executePayout(eq(creatorId1), any(), any());
        verify(payoutExecutionService).executePayout(eq(creatorId2), any(), any());
    }
}








