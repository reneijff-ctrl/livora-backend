package com.joinlivora.backend.payout;

import com.stripe.StripeClient;
import com.stripe.model.Transfer;
import com.stripe.param.TransferCreateParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StripePayoutServiceTest {

    @Mock
    private CreatorPayoutRepository payoutRepository;
    @Mock
    private CreatorEarningRepository earningRepository;
    @Mock
    private CreatorPayoutSettingsRepository settingsRepository;
    @Mock
    private StripeClient stripeClient;
    @Mock
    private PayoutAuditService payoutAuditService;

    @InjectMocks
    private StripePayoutService stripePayoutService;

    private UUID creatorId;
    private CreatorPayout payout;
    private CreatorPayoutSettings settings;

    @BeforeEach
    void setUp() {
        creatorId = UUID.randomUUID();
        payout = CreatorPayout.builder()
                .id(UUID.randomUUID())
                .creatorId(creatorId)
                .amount(new BigDecimal("100.00"))
                .currency("EUR")
                .status(PayoutStatus.PENDING)
                .build();

        settings = CreatorPayoutSettings.builder()
                .creatorId(creatorId)
                .stripeAccountId("acct_123")
                .build();
    }

    @Test
    void triggerPayout_Success() throws Exception {
        when(payoutRepository.findById(payout.getId())).thenReturn(Optional.of(payout));
        when(settingsRepository.findByCreatorId(creatorId)).thenReturn(Optional.of(settings));

        com.stripe.service.TransferService transferService = mock(com.stripe.service.TransferService.class);
        when(stripeClient.transfers()).thenReturn(transferService);
        Transfer transfer = mock(Transfer.class);
        when(transfer.getId()).thenReturn("tr_123");
        when(transferService.create(any(TransferCreateParams.class))).thenReturn(transfer);

        stripePayoutService.triggerPayout(payout.getId());

        assertEquals(PayoutStatus.COMPLETED, payout.getStatus());
        assertEquals("tr_123", payout.getStripeTransferId());
        assertNotNull(payout.getCompletedAt());
        verify(payoutRepository, atLeastOnce()).save(payout);
        verify(payoutAuditService).logStatusChange(eq(payout.getId()), any(), eq(PayoutStatus.PROCESSING), any(), any(), any());
        verify(payoutAuditService).logStatusChange(eq(payout.getId()), any(), eq(PayoutStatus.COMPLETED), any(), any(), any());
    }

    @Test
    void triggerPayout_StripeError_ShouldFailAndUnlock() throws Exception {
        when(payoutRepository.findById(payout.getId())).thenReturn(Optional.of(payout));
        when(settingsRepository.findByCreatorId(creatorId)).thenReturn(Optional.of(settings));

        com.stripe.service.TransferService transferService = mock(com.stripe.service.TransferService.class);
        when(stripeClient.transfers()).thenReturn(transferService);
        when(transferService.create(any(TransferCreateParams.class))).thenThrow(new RuntimeException("Stripe error"));

        CreatorEarning earning = CreatorEarning.builder().locked(true).payout(payout).build();
        when(earningRepository.findAllByPayout(payout)).thenReturn(Collections.singletonList(earning));

        stripePayoutService.triggerPayout(payout.getId());

        assertEquals(PayoutStatus.FAILED, payout.getStatus());
        assertEquals("Stripe error", payout.getFailureReason());
        assertFalse(earning.isLocked());
        // payout referenceId is kept
        verify(earningRepository).saveAll(anyList());
        verify(payoutRepository, atLeastOnce()).save(payout);
        verify(payoutAuditService).logStatusChange(eq(payout.getId()), any(), eq(PayoutStatus.FAILED), any(), any(), any());
    }

    @Test
    void triggerPayout_NoStripeAccount_ShouldFail() {
        settings.setStripeAccountId(null);
        when(payoutRepository.findById(payout.getId())).thenReturn(Optional.of(payout));
        when(settingsRepository.findByCreatorId(creatorId)).thenReturn(Optional.of(settings));

        assertThrows(IllegalStateException.class, () -> 
                stripePayoutService.triggerPayout(payout.getId()));

        assertEquals(PayoutStatus.FAILED, payout.getStatus());
        verify(payoutRepository, atLeastOnce()).save(payout);
    }

    @Test
    void triggerPayout_NoPendingPayout_ShouldThrowException() {
        when(payoutRepository.findById(any())).thenReturn(Optional.empty());

        assertThrows(com.joinlivora.backend.exception.ResourceNotFoundException.class, () -> 
                stripePayoutService.triggerPayout(payout.getId()));
    }
}








