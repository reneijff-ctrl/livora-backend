package com.joinlivora.backend.monetization;

import com.joinlivora.backend.monetization.dto.TipResult;
import com.joinlivora.backend.streaming.Stream;
import com.joinlivora.backend.streaming.StreamRepository;
import com.joinlivora.backend.token.TokenWalletService;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import com.joinlivora.backend.payout.CreatorEarningsService;
import com.joinlivora.backend.token.TipRecordRepository;
import com.joinlivora.backend.analytics.AnalyticsEventPublisher;
import com.joinlivora.backend.fraud.dto.RiskDecisionResult;
import com.joinlivora.backend.fraud.model.RiskDecision;
import com.stripe.StripeClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TipIdempotencyTest {

    @Mock
    private TipRepository tipRepository;
    @Mock
    private TipValidationService tipValidationService;
    @Mock
    private StreamRepository StreamRepository;
    @Mock
    private TokenWalletService tokenWalletService;
    @Mock
    private CreatorEarningsService monetizationService;
    @Mock
    private TipRecordRepository tipRecordRepository;
    @Mock
    private AnalyticsEventPublisher analyticsEventPublisher;
    @Mock
    private UserService userService;
    @Mock
    private StripeClient stripeClient;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private com.joinlivora.backend.payment.PaymentService paymentService;
    @Mock
    private com.joinlivora.backend.fraud.service.VelocityTrackerService velocityTrackerService;
    @Mock
    private com.joinlivora.backend.fraud.service.TrustEvaluationService trustEvaluationService;
    @Mock
    private com.joinlivora.backend.fraud.service.FraudDetectionService fraudDetectionService;
    @Mock
    private com.joinlivora.backend.reputation.service.ReputationEventService reputationEventService;
    @Mock
    private com.joinlivora.backend.abuse.RestrictionService restrictionService;
    @Mock
    private com.joinlivora.backend.abuse.AbuseDetectionService abuseDetectionService;
    @Mock
    private com.joinlivora.backend.aml.service.AMLRulesEngine amlRulesEngine;
    @Mock
    private com.joinlivora.backend.audit.service.AuditService auditService;
    @Mock
    private com.joinlivora.backend.fraud.FraudScoringService fraudRiskService;
    @Mock
    private com.joinlivora.backend.chargeback.InternalChargebackService chargebackService;
    @Mock
    private com.joinlivora.backend.fraud.service.EnforcementService enforcementService;
    @Mock
    private WeeklyTipService weeklyTipService;
    @Mock
    private org.springframework.core.env.Environment environment;

    @InjectMocks
    private TipOrchestrationService tipService;

    private User viewer;
    private User creator;
    private Stream room;
    private UUID roomId;

    @BeforeEach
    void setUp() {
        viewer = new User();
        viewer.setId(1L);
        viewer.setEmail("viewer@test.com");
        viewer.setCreatedAt(java.time.Instant.now());

        creator = new User();
        creator.setId(2L);
        creator.setEmail("creator@test.com");
        creator.setCreatedAt(java.time.Instant.now());

        roomId = UUID.randomUUID();
        room = Stream.builder()
                .id(roomId)
                .creator(creator)
                .isLive(true)
                .build();

        lenient().when(fraudRiskService.calculateRisk(any(), any(), anyInt(), any(), anyLong(), anyBoolean(), anyInt()))
                .thenReturn(new com.joinlivora.backend.fraud.model.FraudRiskResult(
                        com.joinlivora.backend.fraud.model.FraudRiskLevel.LOW,
                        10,
                        java.util.Collections.emptyList()));
    }

    @Test
    void sendTokenTip_DuplicateRequestId_ShouldReturnExistingTip() {
        String requestId = "unique-req-creator";
        Tip existingTip = Tip.builder()
                .id(UUID.randomUUID())
                .senderUserId(viewer)
                .creatorUserId(creator)
                .amount(BigDecimal.valueOf(100))
                .currency("TOKEN")
                .clientRequestId(requestId)
                .status(TipStatus.COMPLETED)
                .build();

        when(tipRepository.findByClientRequestId(requestId)).thenReturn(Optional.of(existingTip));
        when(trustEvaluationService.evaluate(any(), any(), any()))
                .thenReturn(RiskDecisionResult.builder().decision(RiskDecision.ALLOW).build());

        TipResult result = tipService.sendTokenTip(viewer, roomId, 100, "Message", requestId, "127.0.0.1", "fp123", null);

        assertTrue(result.isDuplicate());
        assertEquals(existingTip.getId(), result.getTipId());
        
        // Ensure no actual processing happened
        verify(tokenWalletService, never()).deductTokens(anyLong(), anyLong(), any(), anyString());
        verify(monetizationService, never()).recordTokenTipEarning(any(), any(), anyLong(), any(), any());
        verify(tipRepository, never()).save(any());
    }

    @Test
    void sendTokenTip_FirstRequest_ShouldProcessNormally() {
        String requestId = "unique-req-creator";
        when(tipRepository.findByClientRequestId(requestId)).thenReturn(Optional.empty());
        when(StreamRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(tokenWalletService.getAvailableBalance(viewer.getId())).thenReturn(200L);
        when(monetizationService.getPlatformFeeRate()).thenReturn(new BigDecimal("0.30"));
        when(tipRepository.save(any())).thenAnswer(inv -> {
            Tip tip = inv.getArgument(0);
            tip.setId(UUID.randomUUID());
            tip.setCreatedAt(java.time.Instant.now());
            return tip;
        });
        when(trustEvaluationService.evaluate(any(), any(), any()))
                .thenReturn(RiskDecisionResult.builder().decision(RiskDecision.ALLOW).build());

        TipResult result = tipService.sendTokenTip(viewer, roomId, 100, "Message", requestId, "127.0.0.1", "fp123", null);

        assertFalse(result.isDuplicate());
        assertNotNull(result.getTipId());
        verify(tipRepository).save(any(Tip.class));
    }
}









