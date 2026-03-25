package com.joinlivora.backend.fraud.service;


import com.joinlivora.backend.fraud.Chargeback;
import com.joinlivora.backend.fraud.model.*;
import com.joinlivora.backend.fraud.repository.ChargebackRepository;
import com.joinlivora.backend.fraud.repository.FraudScoreRepository;
import com.joinlivora.backend.payment.PaymentRepository;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FraudEvaluationServiceTest {

    @Mock
    private ChargebackRepository chargebackRepository;
    @Mock
    private FraudScoreRepository fraudScoreRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private EnforcementService enforcementService;
    @Mock
    private RiskScoringService riskScoringService;

    @InjectMocks
    private FraudEvaluationService fraudEvaluationService;

    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        userId = new UUID(0L, 1L);
        user = new User();
        user.setId(1L);
        user.setEmail("test@example.com");
    }

    @Test
    void evaluateUser_LowRisk_ShouldNoEnforcement() {
        when(chargebackRepository.findByUser_Id(1L)).thenReturn(List.of());
        when(paymentRepository.countByUserIdAndSuccessAndCreatedAtAfter(eq(1L), eq(true), any())).thenReturn(10L);
        when(fraudScoreRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(riskScoringService.calculateAndPersist(eq(userId), any())).thenReturn(RiskScore.builder().score(0).build());
        when(riskScoringService.evaluateAction(0)).thenReturn(RiskAction.NO_ACTION);

        fraudEvaluationService.evaluateUser(userId);

        verify(fraudScoreRepository).save(argThat(score -> 
                score.getScore() == 0));
        verifyNoInteractions(enforcementService);
    }

    @Test
    void evaluateUser_OneChargeback_HighRisk_ShouldSuspendAccount() {
        Chargeback cb = Chargeback.builder().createdAt(Instant.now()).build();
        when(chargebackRepository.findByUser_Id(1L)).thenReturn(List.of(cb));
        when(paymentRepository.countByUserIdAndSuccessAndCreatedAtAfter(eq(1L), eq(true), any())).thenReturn(5L); // 1/5 = 20%
        when(fraudScoreRepository.findByUserId(1L)).thenReturn(Optional.empty());
        
        // 1 CB (25) + 1 Rate (40) = 65 -> SUSPENDED
        when(riskScoringService.calculateAndPersist(eq(userId), any())).thenReturn(RiskScore.builder().score(65).build());
        when(riskScoringService.evaluateAction(65)).thenReturn(RiskAction.ACCOUNT_SUSPENDED);

        fraudEvaluationService.evaluateUser(userId, "evt_123", "fraudulent", true, "1.2.3.4");

        verify(fraudScoreRepository).save(argThat(score -> 
                score.getScore() == 65));
        verify(enforcementService).recordChargeback(eq(userId), eq("fraudulent"), eq(1), eq(0.2), eq("evt_123"), eq("SYSTEM"), eq("STRIPE"), eq("1.2.3.4"));
        verify(enforcementService).suspendAccount(eq(userId), any(), eq(1), eq(0.2), eq("evt_123"), eq("SYSTEM"), eq("STRIPE"), eq("1.2.3.4"), eq(65), eq(60));
    }

    @Test
    void evaluateUser_TwoChargebacks_ShouldFreezePayouts() {
        Chargeback cb = Chargeback.builder().createdAt(Instant.now()).build();
        when(chargebackRepository.findByUser_Id(1L)).thenReturn(List.of(cb, cb));
        when(paymentRepository.countByUserIdAndSuccessAndCreatedAtAfter(eq(1L), eq(true), any())).thenReturn(200L); // 2/200 = 1%
        when(fraudScoreRepository.findByUserId(1L)).thenReturn(Optional.empty());
        
        // 2 CB (50) + 0 Rate (0) = 50 -> PAYOUT_FROZEN
        when(riskScoringService.calculateAndPersist(eq(userId), any())).thenReturn(RiskScore.builder().score(50).build());
        when(riskScoringService.evaluateAction(50)).thenReturn(RiskAction.PAYOUT_FROZEN);

        fraudEvaluationService.evaluateUser(userId, "evt_456");

        verify(enforcementService).freezePayouts(eq(userId), any(), eq(2), eq(0.01), eq("evt_456"), eq("SYSTEM"), eq("STRIPE"), eq(null), eq(50), eq(40));
    }

    @Test
    void evaluateUser_ThreeChargebacks_ShouldTerminateAccount() {
        Chargeback cb = Chargeback.builder().createdAt(Instant.now()).build();
        when(chargebackRepository.findByUser_Id(1L)).thenReturn(List.of(cb, cb, cb));
        when(paymentRepository.countByUserIdAndSuccessAndCreatedAtAfter(eq(1L), eq(true), any())).thenReturn(10L);
        when(fraudScoreRepository.findByUserId(1L)).thenReturn(Optional.empty());
        
        // 3 CB (75) + 1 Rate (40) = 100 -> TERMINATED
        when(riskScoringService.calculateAndPersist(eq(userId), any())).thenReturn(RiskScore.builder().score(100).build());
        when(riskScoringService.evaluateAction(100)).thenReturn(RiskAction.ACCOUNT_TERMINATED);

        fraudEvaluationService.evaluateUser(userId, "evt_789");

        verify(enforcementService).terminateAccount(eq(userId), any(), eq(3), eq(0.3), eq("evt_789"), eq("SYSTEM"), eq("STRIPE"), eq(null), eq(100), eq(80));
    }

    @Test
    void processSuccessfulPayment_ShouldRecordEventAndEvaluateUser() {
        when(chargebackRepository.findByUser_Id(1L)).thenReturn(List.of());
        when(paymentRepository.countByUserIdAndSuccessAndCreatedAtAfter(eq(1L), eq(true), any())).thenReturn(10L);
        when(fraudScoreRepository.findByUserId(1L)).thenReturn(Optional.empty());
        when(riskScoringService.calculateAndPersist(eq(userId), any())).thenReturn(RiskScore.builder().score(0).build());
        when(riskScoringService.evaluateAction(0)).thenReturn(RiskAction.NO_ACTION);

        fraudEvaluationService.processSuccessfulPayment(userId, "pi_123", 5000L, "usd", "evt_123", "1.1.1.1");

        verify(enforcementService).recordPaymentSuccess(userId, "pi_123", 5000L, "usd", "evt_123", "1.1.1.1");
        verify(fraudScoreRepository).save(any());
    }
}








