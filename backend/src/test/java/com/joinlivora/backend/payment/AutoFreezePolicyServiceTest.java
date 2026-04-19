package com.joinlivora.backend.payment;

import com.joinlivora.backend.chargeback.model.ChargebackCase;
import com.joinlivora.backend.chargeback.model.ChargebackStatus;
import com.joinlivora.backend.fraud.model.FraudDecisionLevel;
import com.joinlivora.backend.fraud.model.FraudSignalType;
import com.joinlivora.backend.fraud.model.FraudSource;
import com.joinlivora.backend.fraud.model.RiskLevel;
import com.joinlivora.backend.fraud.service.FraudDetectionService;
import com.joinlivora.backend.payment.dto.RiskEscalationResult;
import com.joinlivora.backend.payout.PayoutHoldService;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import com.joinlivora.backend.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AutoFreezePolicyServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private FraudDetectionService fraudDetectionService;
    @Mock
    private ChargebackCorrelationService chargebackCorrelationService;
    @Mock
    private ChargebackRiskEscalationService riskEscalationService;
    @Mock
    private PayoutHoldService payoutHoldService;

    @InjectMocks
    private AutoFreezePolicyService autoFreezePolicyService;

    private User user;
    private ChargebackCase chargeback;
    private UUID chargebackId = UUID.randomUUID();
    private Long userId = 1L;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(userId);
        user.setEmail("test@test.com");
        user.setStatus(UserStatus.ACTIVE);

        chargeback = ChargebackCase.builder()
                .id(chargebackId)
                .userId(new UUID(0L, userId))
                .deviceFingerprint("fp_123")
                .ipAddress("1.1.1.1")
                .paymentMethodFingerprint("pm_123")
                .status(ChargebackStatus.OPEN)
                .fraudScoreAtTime(0)
                .build();
        
        // Default mock for risk escalation (LOW)
        lenient().when(riskEscalationService.evaluateEscalation(any())).thenReturn(
                RiskEscalationResult.builder().riskLevel(RiskLevel.LOW).actions(List.of()).build()
        );
    }

    @Test
    void applyPayerPolicy_WithClusterSize1_ShouldFlagUser() {
        autoFreezePolicyService.applyPayerPolicy(user, 1);

        assertThat(user.getStatus()).isEqualTo(UserStatus.FLAGGED);
        verify(userRepository).save(user);
        verify(fraudDetectionService).logFraudSignal(
                eq(userId), eq(FraudDecisionLevel.MEDIUM), eq(FraudSource.SYSTEM),
                eq(FraudSignalType.CHARGEBACK_CORRELATION), anyString()
        );
    }

    @Test
    void applyCreatorEscalation_WithHighRisk_ShouldApplyHoldAndStatus() {
        Long creatorId = 200L;
        User creator = new User();
        creator.setId(creatorId);
        creator.setEmail("creator@test.com");
        creator.setStatus(UserStatus.ACTIVE);
        
        chargeback.setCreatorId(creatorId);
        chargeback.setTransactionId(UUID.randomUUID());
        
        when(userRepository.findById(creatorId)).thenReturn(Optional.of(creator));
        RiskEscalationResult result = RiskEscalationResult.builder()
                .riskLevel(RiskLevel.HIGH)
                .actions(List.of("PAYOUT_HOLD", "MANUAL_REVIEW"))
                .build();

        autoFreezePolicyService.applyCreatorEscalation(chargeback, result);

        assertThat(creator.getStatus()).isEqualTo(UserStatus.MANUAL_REVIEW);
        verify(userRepository).save(creator);
        verify(payoutHoldService).createHold(eq(creator), eq(RiskLevel.HIGH), eq(chargeback.getTransactionId()), anyString());
    }

    @Test
    void applyPayerPolicy_WithClusterSize2_ShouldFreezePayouts() {
        autoFreezePolicyService.applyPayerPolicy(user, 2);

        assertThat(user.getStatus()).isEqualTo(UserStatus.PAYOUTS_FROZEN);
        assertThat(user.isPayoutsEnabled()).isFalse();
        verify(userRepository).save(user);
        verify(fraudDetectionService).logFraudSignal(
                eq(userId), eq(FraudDecisionLevel.HIGH), eq(FraudSource.SYSTEM),
                eq(FraudSignalType.CHARGEBACK_CORRELATION), contains("2 correlated")
        );
    }

    @Test
    void validateUserStatus_WhenPayoutsDisabled_ShouldThrowException() {
        user.setRole(Role.CREATOR);
        user.setPayoutsEnabled(false);
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.security.access.AccessDeniedException.class, 
                () -> autoFreezePolicyService.validateUserStatus(user));
    }

    @Test
    void validateUserStatus_WhenPayoutsDisabledForNonCreator_ShouldNotThrow() {
        user.setRole(Role.USER);
        user.setPayoutsEnabled(false);
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> autoFreezePolicyService.validateUserStatus(user));
    }

    @Test
    void applyPayerPolicy_WithClusterSize3_ShouldSuspendAccount() {
        autoFreezePolicyService.applyPayerPolicy(user, 3);

        assertThat(user.getStatus()).isEqualTo(UserStatus.SUSPENDED);
        verify(userRepository).save(user);
        verify(fraudDetectionService).logFraudSignal(
                eq(userId), eq(FraudDecisionLevel.HIGH), eq(FraudSource.SYSTEM),
                eq(FraudSignalType.CHARGEBACK_CORRELATION), contains("3 or more")
        );
    }
    
    @Test
    void applyPayerPolicy_WhenAlreadySuspended_ShouldDoNothing() {
        user.setStatus(UserStatus.SUSPENDED);

        autoFreezePolicyService.applyPayerPolicy(user, 3);

        assertThat(user.getStatus()).isEqualTo(UserStatus.SUSPENDED);
        verify(userRepository, never()).save(user);
    }

    @Test
    void validateUserStatus_WhenActive_ShouldNotThrow() {
        user.setStatus(UserStatus.ACTIVE);
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> autoFreezePolicyService.validateUserStatus(user));
    }

    @Test
    void validateUserStatus_WhenFlagged_ShouldNotThrow() {
        user.setStatus(UserStatus.FLAGGED);
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> autoFreezePolicyService.validateUserStatus(user));
    }

    @Test
    void validateUserStatus_WhenFrozen_ShouldThrowException() {
        user.setStatus(UserStatus.PAYOUTS_FROZEN);
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.security.access.AccessDeniedException.class, 
                () -> autoFreezePolicyService.validateUserStatus(user));
    }

    @Test
    void validateUserStatus_WhenManualReview_ShouldThrowException() {
        user.setStatus(UserStatus.MANUAL_REVIEW);
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.security.access.AccessDeniedException.class, 
                () -> autoFreezePolicyService.validateUserStatus(user));
    }

    @Test
    void validateUserStatus_WhenSuspended_ShouldThrowException() {
        user.setStatus(UserStatus.SUSPENDED);
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.security.access.AccessDeniedException.class, 
                () -> autoFreezePolicyService.validateUserStatus(user));
    }
}








