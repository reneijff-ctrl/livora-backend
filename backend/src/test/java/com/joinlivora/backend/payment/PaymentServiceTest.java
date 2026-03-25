package com.joinlivora.backend.payment;

import com.joinlivora.backend.exception.PaymentLockedException;
import com.joinlivora.backend.exception.TrustChallengeException;
import com.joinlivora.backend.fraud.dto.RiskDecisionResult;
import com.joinlivora.backend.fraud.model.FraudDecisionLevel;
import com.joinlivora.backend.fraud.model.RiskDecision;
import com.joinlivora.backend.fraud.model.UserRiskState;
import com.joinlivora.backend.fraud.repository.UserRiskStateRepository;
import com.joinlivora.backend.fraud.service.FraudDetectionService;
import com.joinlivora.backend.fraud.service.TrustEvaluationService;
import com.joinlivora.backend.user.User;
import com.stripe.StripeClient;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.checkout.SessionCreateParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PaymentServiceTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private StripeClient stripeClient;

    @Mock
    private UserRiskStateRepository userRiskStateRepository;

    @Mock
    private com.joinlivora.backend.fraud.service.VelocityTrackerService velocityTrackerService;

    @Mock
    private TrustEvaluationService trustEvaluationService;

    @Mock
    private FraudDetectionService fraudDetectionService;

    @Mock
    private AutoFreezePolicyService autoFreezePolicyService;

    @Mock
    private com.joinlivora.backend.fraud.service.FraudRiskService fraudRiskService;

    @InjectMocks
    private PaymentService paymentService;

    private User user;
    private Long userId;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setEmail("test@test.com");
        userId = 1L;
        
        ReflectionTestUtils.setField(paymentService, "premiumPlanId", "price_123");
        ReflectionTestUtils.setField(paymentService, "successUrl", "http://success.com");
        ReflectionTestUtils.setField(paymentService, "cancelUrl", "http://cancel.com");

        lenient().when(fraudRiskService.calculateRisk(any(), any(), any(), any(), any()))
                .thenReturn(com.joinlivora.backend.fraud.model.FraudRiskAssessment.builder()
                        .riskLevel(com.joinlivora.backend.fraud.model.RiskLevel.LOW)
                        .build());
    }

    @Test
    void createCheckoutSession_WhenLocked_ShouldThrowException() {
        UserRiskState state = UserRiskState.builder()
                .userId(userId)
                .paymentLocked(true)
                .currentRisk(FraudDecisionLevel.HIGH)
                .build();
        
        when(userRiskStateRepository.findById(userId)).thenReturn(Optional.of(state));

        assertThrows(PaymentLockedException.class, () -> {
            paymentService.createCheckoutSession(user, null, "127.0.0.1", "US", "UA", "fp123");
        });

        verifyNoInteractions(velocityTrackerService);
        verifyNoInteractions(trustEvaluationService);
        verify(stripeClient, never()).checkout();
    }

    @Test
    void createTokenCheckoutSession_WhenLocked_ShouldThrowException() {
        UserRiskState state = UserRiskState.builder()
                .userId(userId)
                .paymentLocked(true)
                .currentRisk(FraudDecisionLevel.HIGH)
                .build();

        when(userRiskStateRepository.findById(userId)).thenReturn(Optional.of(state));

        assertThrows(PaymentLockedException.class, () -> {
            paymentService.createTokenCheckoutSession(user, "price_token", UUID.randomUUID(), "127.0.0.1", "US", "UA", "fp123");
        });

        verify(stripeClient, never()).checkout();
    }

    @Test
    void createCheckoutSession_WhenNotLocked_ShouldProceed() throws Exception {
        when(userRiskStateRepository.findById(userId)).thenReturn(Optional.empty());
        when(trustEvaluationService.evaluate(any(), any(), any()))
                .thenReturn(RiskDecisionResult.builder().decision(RiskDecision.ALLOW).build());

        Session session = mock(Session.class);
        when(session.getUrl()).thenReturn("http://stripe.url");
        when(stripeClient.checkout().sessions().create(any(SessionCreateParams.class), any(RequestOptions.class))).thenReturn(session);

        paymentService.createCheckoutSession(user, null, "127.0.0.1", "US", "UA", "fp123");

        verify(trustEvaluationService).evaluate(user, "fp123", "127.0.0.1");
        verify(velocityTrackerService).trackAction(eq(userId), eq(com.joinlivora.backend.fraud.model.VelocityActionType.PAYMENT));
        verify(stripeClient.checkout().sessions()).create(any(SessionCreateParams.class), any(RequestOptions.class));
    }

    @Test
    void createCheckoutSession_WhenTrustChallenge_ShouldThrow() {
        when(userRiskStateRepository.findById(userId)).thenReturn(Optional.empty());
        when(trustEvaluationService.evaluate(any(), any(), any()))
                .thenReturn(RiskDecisionResult.builder().decision(RiskDecision.REVIEW).build());

        assertThrows(TrustChallengeException.class, () ->
                paymentService.createCheckoutSession(user, null, "1.1.1.1", "US", "UA", "fp123")
        );

        verify(stripeClient, never()).checkout();
    }

    @Test
    void createCheckoutSession_WhenTrustBlock_ShouldLogSignalAndThrow() {
        when(userRiskStateRepository.findById(userId)).thenReturn(Optional.empty());
        when(trustEvaluationService.evaluate(any(), any(), any()))
                .thenReturn(RiskDecisionResult.builder().decision(RiskDecision.BLOCK).build());

        assertThrows(org.springframework.security.access.AccessDeniedException.class, () ->
                paymentService.createCheckoutSession(user, null, "1.1.1.1", "US", "UA", "fp123")
        );

        verify(fraudDetectionService).logFraudSignal(eq(userId), eq(FraudDecisionLevel.HIGH), eq(com.joinlivora.backend.fraud.model.FraudSource.PAYMENT), eq(com.joinlivora.backend.fraud.model.FraudSignalType.TRUST_EVALUATION_BLOCK), anyString());
        verify(stripeClient, never()).checkout();
    }

    @Test
    void createCheckoutSession_WhenUserSuspended_ShouldThrowException() {
        doThrow(new org.springframework.security.access.AccessDeniedException("Account is suspended."))
                .when(autoFreezePolicyService).validateUserStatus(user);

        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> {
            paymentService.createCheckoutSession(user, null, "127.0.0.1", "US", "UA", "fp123");
        });

        verify(stripeClient, never()).checkout();
    }
}








