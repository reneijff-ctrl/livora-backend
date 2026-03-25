package com.joinlivora.backend.monetization;

import com.joinlivora.backend.analytics.AnalyticsEventPublisher;
import com.joinlivora.backend.analytics.AnalyticsEventType;
import com.joinlivora.backend.chargeback.InternalChargebackService;
import com.joinlivora.backend.exception.*;
import com.joinlivora.backend.fraud.FraudScoringService;
import com.joinlivora.backend.fraud.exception.HighFraudRiskException;
import com.joinlivora.backend.aml.service.AMLRulesEngine;
import com.joinlivora.backend.fraud.dto.RiskDecisionResult;
import com.joinlivora.backend.fraud.model.FraudDecisionLevel;
import com.joinlivora.backend.fraud.model.RiskDecision;
import com.joinlivora.backend.fraud.service.FraudDetectionService;
import com.joinlivora.backend.fraud.service.TrustEvaluationService;
import com.joinlivora.backend.payment.PaymentService;
import com.joinlivora.backend.monetization.dto.TipResult;
import com.joinlivora.backend.payout.CreatorEarningsService;
import com.joinlivora.backend.streaming.Stream;
import com.joinlivora.backend.streaming.StreamRepository;
import com.joinlivora.backend.token.TipRecordRepository;
import com.joinlivora.backend.token.TokenWalletService;
import com.joinlivora.backend.wallet.WalletTransactionType;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.payout.dto.CreatorEarningsDTO;
import com.joinlivora.backend.streaming.service.StreamModerationService;
import com.joinlivora.backend.chat.ChatModerationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TipServiceTest {

    @Mock
    private TipRepository tipRepository;
    @Mock
    private TokenWalletService tokenWalletService;
    @Mock
    private StreamRepository streamRepository;
    @Mock
    private TipRecordRepository tipRecordRepository;
    @Mock
    private AnalyticsEventPublisher analyticsEventPublisher;
    @Mock
    private TipValidationService tipValidationService;
    @Mock
    private com.joinlivora.backend.user.UserService userService;
    @Mock
    private com.stripe.StripeClient stripeClient;
    @Mock
    private CreatorEarningsService creatorEarningsService;
    @Mock
    private org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;
    @Mock
    private PaymentService paymentService;
    @Mock
    private com.joinlivora.backend.fraud.service.VelocityTrackerService velocityTrackerService;
    @Mock
    private TrustEvaluationService trustEvaluationService;
    @Mock
    private FraudDetectionService fraudDetectionService;
    @Mock
    private com.joinlivora.backend.reputation.service.ReputationEventService reputationEventService;
    @Mock
    private AMLRulesEngine amlRulesEngine;
    @Mock
    private com.joinlivora.backend.audit.service.AuditService auditService;
    @Mock
    private FraudScoringService fraudRiskService;
    @Mock
    private InternalChargebackService chargebackService;
    @Mock
    private com.joinlivora.backend.abuse.AbuseDetectionService abuseDetectionService;
    @Mock
    private com.joinlivora.backend.abuse.RestrictionService restrictionService;
    @Mock
    private com.joinlivora.backend.fraud.service.EnforcementService enforcementService;
    @Mock
    private WeeklyTipService weeklyTipService;
    @Mock
    private ChatModerationService chatModerationService;
    @Mock
    private StreamModerationService liveStreamModerationService;
    @Mock
    private com.joinlivora.backend.streaming.service.StreamAssistantBotService liveStreamAssistantBotService;
    @Mock
    private SuperTipHighlightTracker highlightTracker;
    @Mock
    private com.joinlivora.backend.websocket.PresenceService presenceService;
    @Mock
    private org.springframework.data.redis.core.StringRedisTemplate redisTemplate;
    @Mock
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    @Mock
    private org.springframework.core.env.Environment environment;
    @Mock
    private TipGoalService tipGoalService;
    @Mock
    private com.joinlivora.backend.admin.service.AdminRealtimeEventService adminRealtimeEventService;
    @Mock
    private TipActionService tipActionService;

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
        viewer.setUsername("viewer");
        viewer.setRole(com.joinlivora.backend.user.Role.USER);
        viewer.setCreatedAt(java.time.Instant.now());

        creator = new User();
        creator.setId(2L);
        creator.setEmail("creator@test.com");
        creator.setUsername("creator");
        creator.setRole(com.joinlivora.backend.user.Role.CREATOR);
        creator.setCreatedAt(java.time.Instant.now());

        roomId = UUID.randomUUID();
        room = Stream.builder()
                .id(roomId)
                .creator(creator)
                .isLive(true)
                .build();

        // Default redis mock
        org.springframework.data.redis.core.SetOperations setOps = mock(org.springframework.data.redis.core.SetOperations.class);
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOps);
        lenient().when(setOps.add(any(), any())).thenReturn(1L);
        lenient().when(setOps.size(any())).thenReturn(1L);

        org.springframework.data.redis.core.ZSetOperations zSetOps = mock(org.springframework.data.redis.core.ZSetOperations.class);
        lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        lenient().when(zSetOps.add(any(), any(), anyDouble())).thenReturn(true);
        lenient().when(zSetOps.zCard(any())).thenReturn(1L);

        // Default mocks
        lenient().when(userService.getById(anyLong())).thenReturn(creator);
        lenient().when(streamRepository.findById(roomId)).thenReturn(Optional.of(room));
        lenient().when(fraudRiskService.calculateRisk(any(), any(), anyInt(), any(), anyLong(), anyBoolean(), anyInt()))
                .thenReturn(new com.joinlivora.backend.fraud.model.FraudRiskResult(
                        com.joinlivora.backend.fraud.model.FraudRiskLevel.LOW,
                        10, 
                        java.util.Collections.emptyList()));
        lenient().when(chargebackService.getChargebackCount(any())).thenReturn(0L);
        lenient().when(creatorEarningsService.getPlatformFeeRate()).thenReturn(new BigDecimal("0.30"));
        lenient().when(creatorEarningsService.getAggregatedEarnings(any()))
                .thenReturn(CreatorEarningsDTO.builder().totalTokens(500L).build());
        lenient().when(trustEvaluationService.evaluate(any(), any(), any()))
                .thenReturn(RiskDecisionResult.builder().decision(RiskDecision.ALLOW).build());
        lenient().when(chatModerationService.isShadowMuted(anyLong(), anyString())).thenReturn(false);
        lenient().when(liveStreamModerationService.isShadowMuted(anyLong(), anyLong())).thenReturn(false);
        lenient().when(environment.getActiveProfiles()).thenReturn(new String[]{"test"});
    }

    @Test
    void sendTokenTip_Success_ShouldProcessEverything() throws Exception {
        long amount = 101; // > 100 to trigger pin
        String message = "Great liveStream!";
        when(streamRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(tokenWalletService.getAvailableBalance(1L)).thenReturn(200L);
        doNothing().when(reputationEventService).recordEvent(any(), any(), anyInt(), any(), any());
        when(tipRepository.save(any())).thenAnswer(inv -> {
            Tip t = inv.getArgument(0);
            t.setId(UUID.randomUUID());
            t.setCreatedAt(java.time.Instant.now());
            return t;
        });

        // For pinning
        org.springframework.data.redis.core.ValueOperations valueOps = mock(org.springframework.data.redis.core.ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        TipResult result = tipService.sendTokenTip(viewer, roomId, amount, message, "req-1", "127.0.0.1", "fp123", null);

        assertNotNull(result);
        assertEquals(BigDecimal.valueOf(amount), result.getAmount());
        assertEquals("TOKEN", result.getCurrency());
        assertEquals(message, result.getMessage());
        assertEquals("COMPLETED", result.getStatus());

        verify(trustEvaluationService).evaluate(viewer, "fp123", "127.0.0.1");
        verify(velocityTrackerService).trackAction(eq(1L), eq(com.joinlivora.backend.fraud.model.VelocityActionType.TIP));
        verify(tokenWalletService).deductTokens(eq(1L), eq(amount), eq(WalletTransactionType.TIP), anyString());
        verify(creatorEarningsService).recordTokenTipEarning(eq(viewer), eq(creator), eq(amount), eq(roomId), eq(com.joinlivora.backend.fraud.model.RiskLevel.LOW));
        verify(tipRecordRepository).save(any());
        verify(tipRepository).save(any());
        verify(analyticsEventPublisher).publishEvent(eq(AnalyticsEventType.PAYMENT_SUCCEEDED), eq(viewer), anyMap());
        verify(amlRulesEngine).evaluateRules(creator, BigDecimal.ZERO);
        verify(auditService).logEvent(eq(new UUID(0L, viewer.getId())), eq(com.joinlivora.backend.audit.service.AuditService.TIP_CREATED), eq("TIP"), any(), any(), eq("127.0.0.1"), isNull());
        
        // Verify pinning
        verify(valueOps).set(eq(String.format("stream:%d:pinned", creator.getId())), anyString());
        verify(messagingTemplate, atLeastOnce()).convertAndSend(eq("/exchange/amq.topic/chat." + creator.getId()), any(com.joinlivora.backend.chat.dto.ChatMessageDto.class));

        // Verify Tip Goal and Actions
        verify(tipGoalService).processTip(eq(creator.getId()), eq(amount));
        verify(tipActionService).checkAction(eq(creator.getId()), eq(amount), anyString());
    }

    @Test
    void sendTokenTip_InsufficientBalance_ShouldThrowException() {
        when(streamRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(tokenWalletService.getAvailableBalance(1L)).thenReturn(50L);
        when(trustEvaluationService.evaluate(any(), any(), any()))
                .thenReturn(RiskDecisionResult.builder().decision(RiskDecision.ALLOW).build());

        assertThrows(com.joinlivora.backend.exception.InsufficientBalanceException.class, () -> 
                tipService.sendTokenTip(viewer, roomId, 100, "Test", "req-2", null, null, null));
        
        verify(tokenWalletService, never()).deductTokens(anyLong(), anyLong(), any(), anyString());
    }

    @Test
    void sendTokenTip_StreamNotLive_ShouldThrowException() {
        room.setLive(false);
        when(streamRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(trustEvaluationService.evaluate(any(), any(), any()))
                .thenReturn(RiskDecisionResult.builder().decision(RiskDecision.ALLOW).build());

        assertThrows(IllegalStateException.class, () -> 
                tipService.sendTokenTip(viewer, roomId, 100, "Test", "req-3", null, null, null));
    }

    @Test
    void sendTokenTip_RoomNotFound_ShouldThrowException() {
        lenient().when(streamRepository.findById(roomId)).thenReturn(Optional.empty());
        lenient().when(streamRepository.findByMediasoupRoomId(roomId)).thenReturn(Optional.empty());
        lenient().when(trustEvaluationService.evaluate(any(), any(), any()))
                .thenReturn(RiskDecisionResult.builder().decision(RiskDecision.ALLOW).build());

        assertThrows(ResourceNotFoundException.class, () -> 
                tipService.sendTokenTip(viewer, roomId, 100, "Test", "req-4", null, null, null));
        
        verify(streamRepository).findById(roomId);
        verify(streamRepository).findByMediasoupRoomId(roomId);
    }

    @Test
    void sendTokenTip_InvalidAmount_ShouldThrowException() {
        when(trustEvaluationService.evaluate(any(), any(), any()))
                .thenReturn(RiskDecisionResult.builder().decision(RiskDecision.ALLOW).build());
        doThrow(new IllegalArgumentException("Minimum tip amount is 1 tokens"))
                .when(tipValidationService).validateTokenTip(any(), eq(0L), any());
        
        assertThrows(IllegalArgumentException.class, () -> 
                tipService.sendTokenTip(viewer, roomId, 0, "Test", "req-5", null, null, null));
        
        doThrow(new IllegalArgumentException("Minimum tip amount is 1 tokens"))
                .when(tipValidationService).validateTokenTip(any(), eq(-10L), any());

        assertThrows(IllegalArgumentException.class, () -> 
                tipService.sendTokenTip(viewer, roomId, -10, "Test", "req-6", null, null, null));
    }

    @Test
    void sendTokenTip_HighRisk_ShouldMarkPendingReview() {
        long amount = 100;
        when(streamRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(tokenWalletService.getAvailableBalance(1L)).thenReturn(200L);
        
        when(fraudRiskService.calculateRisk(any(), any(), anyInt(), any(), anyLong(), anyBoolean(), anyInt()))
                .thenReturn(new com.joinlivora.backend.fraud.model.FraudRiskResult(
                        com.joinlivora.backend.fraud.model.FraudRiskLevel.HIGH,
                        80, 
                        java.util.List.of("High risk reasons")));

        when(tipRepository.save(any())).thenAnswer(inv -> {
            Tip t = inv.getArgument(0);
            t.setId(UUID.randomUUID());
            t.setCreatedAt(java.time.Instant.now());
            return t;
        });

        TipResult result = tipService.sendTokenTip(viewer, roomId, amount, "High risk", "req-high", "127.0.0.1", "fp", null);

        assertEquals("PENDING_REVIEW", result.getStatus());
        verify(creatorEarningsService).recordTokenTipEarning(eq(viewer), eq(creator), eq(amount), eq(roomId), eq(com.joinlivora.backend.fraud.model.RiskLevel.HIGH));
    }

    @Test
    void sendTokenTip_CriticalRisk_ShouldBlockAndRecordIncident() {
        long amount = 100;
        when(streamRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(trustEvaluationService.evaluate(any(), any(), any()))
                .thenReturn(RiskDecisionResult.builder().decision(RiskDecision.ALLOW).build());
        
        when(fraudRiskService.calculateRisk(any(), any(), anyInt(), any(), anyLong(), anyBoolean(), anyInt()))
                .thenReturn(new com.joinlivora.backend.fraud.model.FraudRiskResult(
                        com.joinlivora.backend.fraud.model.FraudRiskLevel.CRITICAL,
                        95, 
                        java.util.List.of("Critical risk reasons")));

        assertThrows(HighFraudRiskException.class, () ->
                tipService.sendTokenTip(viewer, roomId, amount, "Critical risk", "req-crit", "127.0.0.1", "fp", null));

        verify(enforcementService).recordFraudIncident(any(), contains("CRITICAL_FRAUD_RISK"), anyMap());
        verify(tipRepository, never()).save(any());
    }

    @Test
    void createTipIntent_HighRisk_ShouldSetMetadataAndMarkPendingReview() throws Exception {
        BigDecimal amount = new BigDecimal("10.00");
        when(userService.getById(anyLong())).thenReturn(creator);
        when(paymentService.checkPaymentLock(any(), any(), any(), any(), any(), any()))
                .thenReturn(com.joinlivora.backend.fraud.model.RiskLevel.LOW);
        
        when(fraudRiskService.calculateRisk(any(), any(), anyInt(), any(), anyLong(), anyBoolean(), anyInt()))
                .thenReturn(new com.joinlivora.backend.fraud.model.FraudRiskResult(
                        com.joinlivora.backend.fraud.model.FraudRiskLevel.HIGH,
                        80, 
                        java.util.List.of("High risk reasons")));

        com.stripe.model.PaymentIntent mockIntent = mock(com.stripe.model.PaymentIntent.class);
        when(mockIntent.getId()).thenReturn("pi_123");
        when(stripeClient.paymentIntents()).thenReturn(mock(com.stripe.service.PaymentIntentService.class));
        when(stripeClient.paymentIntents().create(any(com.stripe.param.PaymentIntentCreateParams.class))).thenReturn(mockIntent);

        ArgumentCaptor<Tip> tipCaptor = ArgumentCaptor.forClass(Tip.class);
        when(tipRepository.save(tipCaptor.capture())).thenReturn(null);

        tipService.createTipIntent(viewer, 2L, amount, "Msg", "req-high-stripe", "127.0.0.1", "US", "UA", "fp");

        assertEquals(TipStatus.PENDING_REVIEW, tipCaptor.getValue().getStatus());
        
        ArgumentCaptor<com.stripe.param.PaymentIntentCreateParams> paramsCaptor = ArgumentCaptor.forClass(com.stripe.param.PaymentIntentCreateParams.class);
        verify(stripeClient.paymentIntents()).create(paramsCaptor.capture());
        assertEquals("HIGH", paramsCaptor.getValue().getMetadata().get("fraud_risk_level"));
    }

    @Test
    void confirmTip_ShouldNotOverwritePendingReview() {
        Tip tip = Tip.builder()
                .id(UUID.randomUUID())
                .status(TipStatus.PENDING_REVIEW)
                .stripePaymentIntentId("pi_123")
                .creatorUserId(creator)
                .senderUserId(viewer)
                .amount(BigDecimal.TEN)
                .currency("EUR")
                .build();
        
        when(tipRepository.findByStripePaymentIntentId("pi_123")).thenReturn(Optional.of(tip));
        
        tipService.confirmTip("pi_123");
        
        assertEquals(TipStatus.PENDING_REVIEW, tip.getStatus());
        verify(tipRepository).save(tip);
    }

    @Test
    void createTipIntent_WhenLocked_ShouldThrowException() throws Exception {
        BigDecimal amount = new BigDecimal("10.00");
        doThrow(new PaymentLockedException("Locked"))
                .when(paymentService).checkPaymentLock(eq(viewer), any(), any(), any(), any(), any());

        assertThrows(PaymentLockedException.class, () ->
                tipService.createTipIntent(viewer, 2L, amount, "Test", "req-locked", "127.0.0.1", "US", "UA", "fp"));

        verify(stripeClient, never()).paymentIntents();
    }

    @Test
    void sendTokenTip_WhenLocked_ShouldThrowException() {
        doThrow(new PaymentLockedException("Locked"))
                .when(paymentService).checkPaymentLock(eq(viewer), any(), any(), any(), any(), any());

        assertThrows(PaymentLockedException.class, () ->
                tipService.sendTokenTip(viewer, roomId, 100, "Test", "req-locked-token", null, null, null));

        verifyNoInteractions(velocityTrackerService);
        verifyNoInteractions(tokenWalletService);
    }

    @Test
    void createTipIntent_WhenTrustChallenge_ShouldThrow() {
        when(trustEvaluationService.evaluate(any(), any(), any()))
                .thenReturn(RiskDecisionResult.builder().decision(RiskDecision.REVIEW).build());

        assertThrows(TrustChallengeException.class, () ->
                tipService.createTipIntent(viewer, 2L, BigDecimal.TEN, "msg", "req1", "1.1.1.1", "US", "UA", "fp123")
        );

        verify(stripeClient, never()).paymentIntents();
    }

    @Test
    void createTipIntent_WhenTrustBlock_ShouldLogSignalAndThrow() {
        when(trustEvaluationService.evaluate(any(), any(), any()))
                .thenReturn(RiskDecisionResult.builder().decision(RiskDecision.BLOCK).build());

        assertThrows(org.springframework.security.access.AccessDeniedException.class, () ->
                tipService.createTipIntent(viewer, 2L, BigDecimal.TEN, "msg", "req1", "1.1.1.1", "US", "UA", "fp123")
        );

        verify(fraudDetectionService).logFraudSignal(eq(1L), eq(FraudDecisionLevel.HIGH), eq(com.joinlivora.backend.fraud.model.FraudSource.PAYMENT), eq(com.joinlivora.backend.fraud.model.FraudSignalType.TRUST_EVALUATION_BLOCK), anyString());
        verify(stripeClient, never()).paymentIntents();
    }
    @Test
    void confirmTip_ShouldUpdateStatusAndTriggerAML() throws Exception {
        Tip tip = Tip.builder()
                .id(UUID.randomUUID())
                .stripePaymentIntentId("pi_123")
                .status(TipStatus.PENDING)
                .senderUserId(viewer)
                .creatorUserId(creator)
                .amount(new BigDecimal("150.00")) // > 100
                .currency("EUR")
                .room(room)
                .build();
        when(tipRepository.findByStripePaymentIntentId("pi_123")).thenReturn(Optional.of(tip));

        // For pinning
        org.springframework.data.redis.core.ValueOperations valueOps = mock(org.springframework.data.redis.core.ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        tipService.confirmTip("pi_123");

        assertEquals(TipStatus.COMPLETED, tip.getStatus());
        verify(tipRepository).save(tip);
        verify(amlRulesEngine).evaluateRules(creator, BigDecimal.ZERO);

        // Verify pinning
        verify(valueOps).set(eq(String.format("stream:%d:pinned", creator.getId())), anyString());
        verify(messagingTemplate, atLeastOnce()).convertAndSend(eq("/exchange/amq.topic/chat." + creator.getId()), any(com.joinlivora.backend.chat.dto.ChatMessageDto.class));
    }

    @Test
    void createTipIntent_HighRisk_ShouldSetPendingReviewAndMetadata() throws Exception {
        when(userService.getById(anyLong())).thenReturn(creator);
        when(trustEvaluationService.evaluate(any(), any(), any()))
                .thenReturn(RiskDecisionResult.builder().decision(RiskDecision.ALLOW).build());
        when(paymentService.checkPaymentLock(any(), any(), any(), any(), any(), any()))
                .thenReturn(com.joinlivora.backend.fraud.model.RiskLevel.LOW);
        
        when(fraudRiskService.calculateRisk(any(), any(), anyInt(), any(), anyLong(), anyBoolean(), anyInt()))
                .thenReturn(new com.joinlivora.backend.fraud.model.FraudRiskResult(
                        com.joinlivora.backend.fraud.model.FraudRiskLevel.HIGH,
                        80,
                        java.util.List.of("High risk reasons")));

        com.stripe.model.PaymentIntent mockIntent = mock(com.stripe.model.PaymentIntent.class);
        when(mockIntent.getId()).thenReturn("pi_123");
        when(stripeClient.paymentIntents()).thenReturn(mock(com.stripe.service.PaymentIntentService.class));
        when(stripeClient.paymentIntents().create(any(com.stripe.param.PaymentIntentCreateParams.class))).thenReturn(mockIntent);

        ArgumentCaptor<Tip> tipCaptor = ArgumentCaptor.forClass(Tip.class);
        tipService.createTipIntent(viewer, 2L, BigDecimal.TEN, "msg", "req123", "1.1.1.1", "US", "ua", "fp");
        
        verify(tipRepository).save(tipCaptor.capture());
        assertEquals(TipStatus.PENDING_REVIEW, tipCaptor.getValue().getStatus());
        
        ArgumentCaptor<com.stripe.param.PaymentIntentCreateParams> paramsCaptor = ArgumentCaptor.forClass(com.stripe.param.PaymentIntentCreateParams.class);
        verify(stripeClient.paymentIntents()).create(paramsCaptor.capture());
        assertEquals("HIGH", paramsCaptor.getValue().getMetadata().get("fraud_risk_level"));
    }

    @Test
    void createTipIntent_CriticalRisk_ShouldBlockAndRecordIncident() throws Exception {
        when(userService.getById(anyLong())).thenReturn(creator);
        when(paymentService.checkPaymentLock(any(), any(), any(), any(), any(), any()))
                .thenReturn(com.joinlivora.backend.fraud.model.RiskLevel.LOW);
        
        when(fraudRiskService.calculateRisk(any(), any(), anyInt(), any(), anyLong(), anyBoolean(), anyInt()))
                .thenReturn(new com.joinlivora.backend.fraud.model.FraudRiskResult(
                        com.joinlivora.backend.fraud.model.FraudRiskLevel.CRITICAL,
                        95,
                        java.util.List.of("Critical risk reasons")));

        assertThrows(HighFraudRiskException.class, () ->
                tipService.createTipIntent(viewer, 2L, BigDecimal.TEN, "msg", "req-crit", "1.1.1.1", "US", "ua", "fp")
        );

        verify(enforcementService).recordFraudIncident(any(), contains("CRITICAL_FRAUD_RISK"), anyMap());
        verify(tipRepository, never()).save(any());
    }

    @Test
    void sendTokenTip_MediumFraudRisk_ShouldSetPendingReview() {
        when(fraudRiskService.calculateRisk(any(), any(), anyInt(), any(), anyLong(), anyBoolean(), anyInt()))
                .thenReturn(new com.joinlivora.backend.fraud.model.FraudRiskResult(
                        com.joinlivora.backend.fraud.model.FraudRiskLevel.MEDIUM,
                        50,
                        java.util.List.of("Medium risk reasons")));

        when(streamRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(tokenWalletService.getAvailableBalance(anyLong())).thenReturn(1000L);
        when(creatorEarningsService.getPlatformFeeRate()).thenReturn(new BigDecimal("0.30"));
        when(trustEvaluationService.evaluate(any(), any(), any()))
                .thenReturn(RiskDecisionResult.builder().decision(RiskDecision.ALLOW).build());
        when(tipRepository.save(any())).thenAnswer(inv -> {
            Tip t = inv.getArgument(0);
            t.setId(UUID.randomUUID());
            t.setCreatedAt(java.time.Instant.now());
            return t;
        });

        tipService.sendTokenTip(viewer, roomId, 100L, "msg", "req456", "1.1.1.1", "fp", null);

        verify(tipRepository).save(argThat(tip -> tip.getStatus() == TipStatus.PENDING_REVIEW));
    }
    @Test
    void sendTokenTip_WithTipCooldown_ShouldThrowException() {
        java.time.Instant expiresAt = java.time.Instant.now().plusSeconds(300);
        doThrow(new UserRestrictedException(com.joinlivora.backend.abuse.model.RestrictionLevel.TIP_COOLDOWN, "Restricted", expiresAt))
                .when(restrictionService).validateTippingAccess(any(), any());

        assertThrows(UserRestrictedException.class, () ->
                tipService.sendTokenTip(viewer, roomId, 100L, "Tip", "req-1", "127.0.0.1", "fp", null)
        );
    }

    @Test
    void checkSuspiciousTippingPatterns_NewAccountHighRisk_ShouldRecordSignal() {
        viewer.setCreatedAt(java.time.Instant.now().minus(12, java.time.temporal.ChronoUnit.HOURS)); // < 24h
        when(streamRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(tokenWalletService.getAvailableBalance(anyLong())).thenReturn(1000L);
        Tip savedTip = Tip.builder().id(UUID.randomUUID()).amount(BigDecimal.valueOf(100L)).currency("TOKEN").createdAt(java.time.Instant.now()).status(TipStatus.COMPLETED).build();
        when(tipRepository.save(any())).thenReturn(savedTip);

        tipService.sendTokenTip(viewer, roomId, 100L, "msg", "req-high", "1.1.1.1", "fp", null);

        verify(fraudRiskService).recordSignal(eq(com.joinlivora.backend.fraud.model.FraudSignalType.NEW_ACCOUNT_TIPPING_HIGH), eq(viewer.getId()), eq(creator.getId()), any());
    }

    @Test
    void checkSuspiciousTippingPatterns_NewAccountMediumRisk_ShouldRecordSignal() {
        viewer.setCreatedAt(java.time.Instant.now().minus(3, java.time.temporal.ChronoUnit.DAYS)); // > 24h, < 7d
        when(streamRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(tokenWalletService.getAvailableBalance(anyLong())).thenReturn(1000L);
        Tip savedTip = Tip.builder().id(UUID.randomUUID()).amount(BigDecimal.valueOf(100L)).currency("TOKEN").createdAt(java.time.Instant.now()).status(TipStatus.COMPLETED).build();
        when(tipRepository.save(any())).thenReturn(savedTip);

        tipService.sendTokenTip(viewer, roomId, 100L, "msg", "req-med", "1.1.1.1", "fp", null);

        verify(fraudRiskService).recordSignal(eq(com.joinlivora.backend.fraud.model.FraudSignalType.NEW_ACCOUNT_TIPPING_MEDIUM), eq(viewer.getId()), eq(creator.getId()), any());
    }

    @Test
    void checkSuspiciousTippingPatterns_NewAccountCluster_ShouldRecordSignal() {
        // Given
        viewer.setCreatedAt(java.time.Instant.now().minus(1, java.time.temporal.ChronoUnit.HOURS)); // < 24h
        when(streamRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(tokenWalletService.getAvailableBalance(anyLong())).thenReturn(1000L);
        Tip savedTip = Tip.builder().id(UUID.randomUUID()).amount(BigDecimal.valueOf(100L)).currency("TOKEN").createdAt(java.time.Instant.now()).status(TipStatus.COMPLETED).build();
        when(tipRepository.save(any())).thenReturn(savedTip);

        org.springframework.data.redis.core.ZSetOperations zSetOps = mock(org.springframework.data.redis.core.ZSetOperations.class);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        when(zSetOps.add(anyString(), anyString(), anyDouble())).thenReturn(true); // New user added
        when(zSetOps.zCard(anyString())).thenReturn(5L); // Threshold reached

        // When
        tipService.sendTokenTip(viewer, roomId, 100L, "msg", "req-cluster", "1.1.1.1", "fp", null);

        // Then
        verify(redisTemplate).expire(eq("cluster:" + creator.getId()), eq(java.time.Duration.ofMinutes(15)));
        verify(fraudRiskService).recordSignal(eq(com.joinlivora.backend.fraud.model.FraudSignalType.NEW_ACCOUNT_TIPPING_HIGH), eq(viewer.getId()), eq(creator.getId()), any());
        verify(fraudRiskService).recordSignal(eq(com.joinlivora.backend.fraud.model.FraudSignalType.NEW_ACCOUNT_TIP_CLUSTER), eq(viewer.getId()), eq(creator.getId()), any());
        verify(adminRealtimeEventService).publishAbuseEvent(eq("TIP_CLUSTER"), any(), eq("creator"), any());
    }

    @Test
    void checkSuspiciousTippingPatterns_ClusterUpdate_ShouldRefreshTTL() {
        // Given
        viewer.setCreatedAt(java.time.Instant.now().minus(1, java.time.temporal.ChronoUnit.HOURS));
        when(streamRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(tokenWalletService.getAvailableBalance(anyLong())).thenReturn(1000L);
        Tip savedTip = Tip.builder().id(UUID.randomUUID()).amount(BigDecimal.valueOf(100L)).currency("TOKEN").createdAt(java.time.Instant.now()).status(TipStatus.COMPLETED).build();
        when(tipRepository.save(any())).thenReturn(savedTip);

        org.springframework.data.redis.core.ZSetOperations zSetOps = mock(org.springframework.data.redis.core.ZSetOperations.class);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        when(zSetOps.add(anyString(), anyString(), anyDouble())).thenReturn(false); // Already in cluster

        // When
        tipService.sendTokenTip(viewer, roomId, 100L, "msg", "req-update", "1.1.1.1", "fp", null);

        // Then
        // Should refresh TTL regardless of whether it was a new addition
        verify(redisTemplate).expire(eq("cluster:" + creator.getId()), eq(java.time.Duration.ofMinutes(15)));
    }

    @Test
    void checkSuspiciousTippingPatterns_RapidRepeats_ShouldRecordSignal() {
        // Given
        when(streamRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(tokenWalletService.getAvailableBalance(anyLong())).thenReturn(1000L);
        Tip savedTip = Tip.builder().id(UUID.randomUUID()).amount(BigDecimal.valueOf(100L)).currency("TOKEN").createdAt(java.time.Instant.now()).status(TipStatus.COMPLETED).build();
        when(tipRepository.save(any())).thenReturn(savedTip);

        org.springframework.data.redis.core.ZSetOperations zSetOps = mock(org.springframework.data.redis.core.ZSetOperations.class);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);
        when(zSetOps.add(anyString(), anyString(), anyDouble())).thenReturn(true);
        when(zSetOps.zCard(anyString())).thenReturn(6L); // 6th tip in 2m window

        // When
        tipService.sendTokenTip(viewer, roomId, 100L, "msg", "req-repeats", "1.1.1.1", "fp", null);

        // Then
        String repeatKey = "rapid-tip:" + viewer.getId() + ":" + creator.getId();
        verify(redisTemplate).expire(eq(repeatKey), eq(java.time.Duration.ofMinutes(5)));
        verify(fraudRiskService).recordSignal(eq(com.joinlivora.backend.fraud.model.FraudSignalType.RAPID_TIP_REPEATS), eq(viewer.getId()), eq(creator.getId()), any());
    }

    @Test
    void checkSuspiciousTippingPatterns_RapidRepeats_ShouldEnforceSizeLimit() {
        // Given
        when(streamRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(tokenWalletService.getAvailableBalance(anyLong())).thenReturn(1000L);
        Tip savedTip = Tip.builder().id(UUID.randomUUID()).amount(BigDecimal.valueOf(100L)).currency("TOKEN").createdAt(java.time.Instant.now()).status(TipStatus.COMPLETED).build();
        when(tipRepository.save(any())).thenReturn(savedTip);

        org.springframework.data.redis.core.ZSetOperations zSetOps = mock(org.springframework.data.redis.core.ZSetOperations.class);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);

        // When
        tipService.sendTokenTip(viewer, roomId, 100L, "msg", "req-limit", "1.1.1.1", "fp", null);

        // Then
        String repeatKey = "rapid-tip:" + viewer.getId() + ":" + creator.getId();
        verify(zSetOps).removeRange(eq(repeatKey), eq(0L), eq(-51L));
        verify(redisTemplate).expire(eq(repeatKey), eq(java.time.Duration.ofMinutes(5)));
    }

    @Test
    void checkSuspiciousTippingPatterns_RapidRepeats_ShouldUseCounterInMember() {
        // Given
        when(streamRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(tokenWalletService.getAvailableBalance(anyLong())).thenReturn(1000L);
        Tip savedTip = Tip.builder().id(UUID.randomUUID()).amount(BigDecimal.valueOf(100L)).currency("TOKEN").createdAt(java.time.Instant.now()).status(TipStatus.COMPLETED).build();
        when(tipRepository.save(any())).thenReturn(savedTip);

        org.springframework.data.redis.core.ZSetOperations zSetOps = mock(org.springframework.data.redis.core.ZSetOperations.class);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOps);

        // When
        tipService.sendTokenTip(viewer, roomId, 100L, "msg", "req-repeats-counter", "1.1.1.1", "fp", null);

        // Then
        ArgumentCaptor<String> memberCaptor = ArgumentCaptor.forClass(String.class);
        String repeatKey = "rapid-tip:" + viewer.getId() + ":" + creator.getId();
        verify(zSetOps).add(eq(repeatKey), memberCaptor.capture(), anyDouble());
        verify(redisTemplate).expire(eq(repeatKey), eq(java.time.Duration.ofMinutes(5)));

        String member = memberCaptor.getValue();
        String[] parts = member.split(":");
        assertEquals(3, parts.length, "Member should have 3 parts: userId, timestamp, counter");
        assertEquals(viewer.getId().toString(), parts[0]);
        assertTrue(parts[1].matches("\\d+"), "Timestamp should be numeric");
        assertTrue(parts[2].matches("\\d+"), "Counter should be numeric");
    }
}










