package com.joinlivora.backend.payout;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PayoutHoldReleaseJobTest {

    @Mock
    private CreatorEarningsService creatorEarningsService;

    @Mock
    private PayoutHoldService payoutHoldService;

    @InjectMocks
    private PayoutHoldReleaseJob payoutHoldReleaseJob;

    @Test
    void checkAndUnlock_ShouldCallServices() {
        when(payoutHoldService.releaseExpiredHolds()).thenReturn(5);
        when(creatorEarningsService.unlockExpiredEarnings()).thenReturn(10);

        payoutHoldReleaseJob.checkAndUnlock();

        verify(payoutHoldService).releaseExpiredHolds();
        verify(creatorEarningsService).unlockExpiredEarnings();
    }

    @Test
    void checkAndUnlock_WhenException_ShouldHandleIt() {
        when(payoutHoldService.releaseExpiredHolds()).thenThrow(new RuntimeException("DB Error"));

        // Should not throw exception
        payoutHoldReleaseJob.checkAndUnlock();

        verify(payoutHoldService).releaseExpiredHolds();
        verify(creatorEarningsService, never()).unlockExpiredEarnings();
    }
}








