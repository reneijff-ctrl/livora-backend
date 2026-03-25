package com.joinlivora.backend.payout.policy;

import com.joinlivora.backend.creator.verification.KycAccessService;
import com.joinlivora.backend.exception.BusinessException;
import com.joinlivora.backend.fraud.model.UserRiskState;
import com.joinlivora.backend.fraud.repository.UserRiskStateRepository;
import com.joinlivora.backend.common.exception.PayoutBlockedException;
import com.joinlivora.backend.payout.freeze.PayoutFreeze;
import com.joinlivora.backend.payout.freeze.PayoutFreezeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PayoutEligibilityServiceTest {

    @Mock
    private KycAccessService kycAccessService;

    @Mock
    private PayoutFreezeService payoutFreezeService;

    @Mock
    private UserRiskStateRepository userRiskStateRepository;

    private PayoutEligibilityService payoutEligibilityService;

    @BeforeEach
    void setUp() {
        payoutEligibilityService = new PayoutEligibilityService(
                kycAccessService,
                payoutFreezeService,
                userRiskStateRepository
        );
    }

    @Test
    void assertEligibleForPayout_WhenAllChecksPass_ShouldNotThrow() {
        Long creatorId = 1L;
        BigDecimal amount = new BigDecimal("100.00");

        when(payoutFreezeService.findActiveFreeze(creatorId)).thenReturn(Optional.empty());
        when(userRiskStateRepository.findById(creatorId)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> payoutEligibilityService.assertEligibleForPayout(creatorId, amount));

        verify(kycAccessService).assertCreatorCanReceivePayout(creatorId);
    }

    @Test
    void assertEligibleForPayout_WhenPayoutFrozen_ShouldThrowPayoutBlockedException() {
        Long creatorId = 1L;
        BigDecimal amount = new BigDecimal("100.00");

        PayoutFreeze freeze = PayoutFreeze.builder()
                .creatorId(creatorId)
                .reason("Fraud suspected")
                .active(true)
                .build();
        when(payoutFreezeService.findActiveFreeze(creatorId)).thenReturn(Optional.of(freeze));

        var exception = assertThrows(PayoutBlockedException.class, () -> payoutEligibilityService.assertEligibleForPayout(creatorId, amount));
        org.junit.jupiter.api.Assertions.assertTrue(exception.getMessage().contains("Active payout freeze: Fraud suspected"));
    }

    @Test
    void assertEligibleForPayout_WhenRiskStateLocked_ShouldThrowPayoutBlockedException() {
        Long creatorId = 1L;
        BigDecimal amount = new BigDecimal("100.00");

        when(payoutFreezeService.findActiveFreeze(creatorId)).thenReturn(Optional.empty());
        
        UserRiskState riskState = UserRiskState.builder()
                .userId(creatorId)
                .paymentLocked(true)
                .build();
        when(userRiskStateRepository.findById(creatorId)).thenReturn(Optional.of(riskState));

        assertThrows(PayoutBlockedException.class, () -> payoutEligibilityService.assertEligibleForPayout(creatorId, amount));
    }

    @Test
    void assertEligibleForPayout_WhenRiskStateBlockedUntil_ShouldThrowPayoutBlockedException() {
        Long creatorId = 1L;
        BigDecimal amount = new BigDecimal("100.00");

        when(payoutFreezeService.findActiveFreeze(creatorId)).thenReturn(Optional.empty());

        UserRiskState riskState = UserRiskState.builder()
                .userId(creatorId)
                .blockedUntil(Instant.now().plusSeconds(3600))
                .build();
        when(userRiskStateRepository.findById(creatorId)).thenReturn(Optional.of(riskState));

        assertThrows(PayoutBlockedException.class, () -> payoutEligibilityService.assertEligibleForPayout(creatorId, amount));
    }

    @Test
    void assertEligibleForPayout_WhenBelowMinimumThreshold_ShouldThrowBusinessException() {
        Long creatorId = 1L;
        BigDecimal amount = new BigDecimal("49.99");

        when(payoutFreezeService.findActiveFreeze(creatorId)).thenReturn(Optional.empty());
        when(userRiskStateRepository.findById(creatorId)).thenReturn(Optional.empty());

        assertThrows(BusinessException.class, () -> payoutEligibilityService.assertEligibleForPayout(creatorId, amount));
    }
}










