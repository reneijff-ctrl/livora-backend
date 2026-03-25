package com.joinlivora.backend.monetization;

import com.joinlivora.backend.analytics.AnalyticsEventPublisher;
import com.joinlivora.backend.analytics.AnalyticsEventType;
import com.joinlivora.backend.exception.PaymentLockedException;
import com.joinlivora.backend.fraud.FraudScoringService;
import com.joinlivora.backend.payment.PaymentService;
import com.joinlivora.backend.chargeback.ChargebackService;
import com.joinlivora.backend.payout.CreatorEarningsService;
import com.joinlivora.backend.streaming.Stream;
import com.joinlivora.backend.streaming.StreamRepository;
import com.joinlivora.backend.user.User;
import com.stripe.StripeClient;
import com.stripe.model.PaymentIntent;
import com.stripe.service.PaymentIntentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HighlightedMessageServiceTest {

    @Mock
    private HighlightedChatMessageRepository repository;
    @Mock
    private StripeClient stripeClient;
    @Mock
    private PaymentIntentService paymentIntentService;
    @Mock
    private StreamRepository streamRepository;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private TipValidationService tipValidationService;
    @Mock
    private CreatorEarningsService creatorEarningsService;
    @Mock
    private AnalyticsEventPublisher analyticsEventPublisher;
    @Mock
    private PaymentService paymentService;
    @Mock
    private com.joinlivora.backend.audit.service.AuditService auditService;
    @Mock
    private FraudScoringService fraudRiskService;
    @Mock
    private TipRepository tipRepository;
    @Mock
    private ChargebackService chargebackService;
    @Mock
    private com.joinlivora.backend.abuse.AbuseDetectionService abuseDetectionService;
    @Mock
    private com.joinlivora.backend.abuse.RestrictionService restrictionService;
    @Mock
    private com.joinlivora.backend.fraud.service.EnforcementService enforcementService;

    private HighlightedMessageService service;

    @BeforeEach
    void setUp() {
        service = new HighlightedMessageService(repository, stripeClient, streamRepository, messagingTemplate, tipValidationService, auditService, creatorEarningsService, analyticsEventPublisher, paymentService, fraudRiskService, tipRepository, chargebackService, abuseDetectionService, restrictionService, enforcementService);

        lenient().when(fraudRiskService.calculateRisk(any(), any(), anyInt(), any(), anyLong(), anyBoolean(), anyInt()))
                .thenReturn(new com.joinlivora.backend.fraud.model.FraudRiskResult(com.joinlivora.backend.fraud.model.FraudRiskLevel.LOW, 10, java.util.Collections.emptyList()));
        lenient().when(chargebackService.getChargebackCount(any())).thenReturn(0L);
    }

    @Test
    void createHighlightIntent_ValidRequest_ShouldReturnClientSecret() throws Exception {
        User user = new User();
        user.setId(1L);
        user.setEmail("test@test.com");
        user.setCreatedAt(java.time.Instant.now());
        
        User creator = new User();
        creator.setId(2L);
        
        UUID roomId = UUID.randomUUID();
        Stream room = new Stream();
        room.setId(roomId);
        room.setCreator(creator);

        when(streamRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(stripeClient.paymentIntents()).thenReturn(paymentIntentService);
        
        PaymentIntent mockIntent = mock(PaymentIntent.class);
        when(mockIntent.getId()).thenReturn("pi_123");
        when(mockIntent.getClientSecret()).thenReturn("secret_123");
        when(paymentIntentService.create(any())).thenReturn(mockIntent);

        String secret = service.createHighlightIntent(user, roomId, "msg1", "Hello world", HighlightType.COLOR, new BigDecimal("2.00"), "req1", "127.0.0.1", "US", "Test UA");

        assertEquals("secret_123", secret);
        verify(repository).save(any(HighlightedMessage.class));
    }

    @Test
    void createHighlightIntent_BelowMinimum_ShouldThrowException() {
        User user = new User();
        user.setId(1L);
        user.setCreatedAt(java.time.Instant.now());
        assertThrows(IllegalArgumentException.class, () -> 
            service.createHighlightIntent(user, UUID.randomUUID(), "msg1", "Hello", HighlightType.PINNED, new BigDecimal("1.00"), "req1", null, null, null)
        );
    }

    @Test
    void confirmHighlight_PaidMessage_ShouldUpdateStatusAndBroadcast() {
        HighlightedMessage msg = new HighlightedMessage();
        msg.setId(UUID.randomUUID());
        msg.setStripePaymentIntentId("pi_123");
        msg.setStatus(TipStatus.PENDING);
        Stream room = new Stream();
        room.setId(UUID.randomUUID());
        User creator = new User();
        creator.setId(2L);
        room.setCreator(creator);
        msg.setRoomId(room);
        User user = new User();
        user.setId(1L);
        user.setEmail("test@test.com");
        msg.setUserId(user);
        msg.setMessageId("msg123");
        msg.setHighlightType(HighlightType.COLOR);
        msg.setAmount(new BigDecimal("2.00"));
        msg.setContent("Test content");

        when(repository.findByStripePaymentIntentId("pi_123")).thenReturn(Optional.of(msg));

        service.confirmHighlight("pi_123");

        assertEquals(TipStatus.COMPLETED, msg.getStatus());
        verify(repository).save(msg);
        verify(analyticsEventPublisher).publishEvent(eq(AnalyticsEventType.HIGHLIGHTED_MESSAGE_SENT), eq(user), anyMap());
        verify(messagingTemplate).convertAndSend(eq("/exchange/amq.topic/chat." + creator.getId()), any(com.joinlivora.backend.websocket.RealtimeMessage.class));
    }

    @Test
    void removeHighlight_ShouldUpdateEntityAndBroadcast() {
        HighlightedMessage msg = new HighlightedMessage();
        msg.setId(UUID.randomUUID());
        Stream room = new Stream();
        room.setId(UUID.randomUUID());
        User creator = new User();
        creator.setId(2L);
        room.setCreator(creator);
        msg.setRoomId(room);
        msg.setMessageId("msg123");
        User moderator = new User();
        moderator.setId(10L);
        moderator.setEmail("mod@test.com");

        when(repository.findById(msg.getId())).thenReturn(Optional.of(msg));

        service.removeHighlight(msg.getId(), moderator, "Spam");

        assertTrue(msg.isModerated());
        assertEquals(moderator, msg.getModeratedBy());
        assertEquals("Spam", msg.getModerationReason());
        verify(repository).save(msg);
        verify(auditService).logEvent(eq(new UUID(0L, moderator.getId())), eq("CONTENT_TAKEDOWN"), eq("HIGHLIGHT"), eq(msg.getId()), any(), isNull(), isNull());
        verify(messagingTemplate).convertAndSend(eq("/exchange/amq.topic/chat." + creator.getId()), any(com.joinlivora.backend.websocket.RealtimeMessage.class));
    }

    @Test
    void refundHighlight_ShouldRefundMarkAsRefundedAndReverseEarnings() throws Exception {
        HighlightedMessage msg = new HighlightedMessage();
        msg.setId(UUID.randomUUID());
        msg.setStripePaymentIntentId("pi_123");
        msg.setStatus(TipStatus.COMPLETED);
        Stream room = new Stream();
        room.setId(UUID.randomUUID());
        User creator = new User();
        creator.setId(2L);
        room.setCreator(creator);
        msg.setRoomId(room);
        msg.setMessageId("msg123");
        User user = new User();
        user.setId(1L);
        msg.setUserId(user);
        msg.setAmount(new BigDecimal("5.00"));
        User moderator = new User();
        moderator.setId(10L);
        moderator.setEmail("mod@test.com");

        when(repository.findById(msg.getId())).thenReturn(Optional.of(msg));
        com.stripe.service.RefundService refundService = mock(com.stripe.service.RefundService.class);
        when(stripeClient.refunds()).thenReturn(refundService);

        service.refundHighlight(msg.getId(), moderator, "Accidental purchase");

        assertEquals(TipStatus.REFUNDED, msg.getStatus());
        assertTrue(msg.isModerated());
        verify(refundService).create(any(com.stripe.param.RefundCreateParams.class));
        verify(creatorEarningsService).reverseEarningByStripeId("pi_123");
        verify(analyticsEventPublisher).publishEvent(eq(AnalyticsEventType.HIGHLIGHTED_MESSAGE_REFUNDED), any(), anyMap());
        verify(repository).save(msg);
        verify(messagingTemplate).convertAndSend(eq("/exchange/amq.topic/chat." + creator.getId()), any(com.joinlivora.backend.websocket.RealtimeMessage.class));
    }

    @Test
    void createHighlightIntent_HighRisk_ShouldSetMetadataAndMarkPendingReview() throws Exception {
        User user = new User();
        user.setId(1L);
        user.setEmail("test@test.com");
        user.setCreatedAt(java.time.Instant.now());
        
        User creator = new User();
        creator.setId(2L);
        
        UUID roomId = UUID.randomUUID();
        Stream room = new Stream();
        room.setId(roomId);
        room.setCreator(creator);

        when(streamRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(stripeClient.paymentIntents()).thenReturn(paymentIntentService);
        when(paymentService.checkPaymentLock(any(), any(), any(), any(), any(), any()))
                .thenReturn(com.joinlivora.backend.fraud.model.RiskLevel.LOW);
        
        when(fraudRiskService.calculateRisk(any(), any(), anyInt(), any(), anyLong(), anyBoolean(), anyInt()))
                .thenReturn(new com.joinlivora.backend.fraud.model.FraudRiskResult(
                        com.joinlivora.backend.fraud.model.FraudRiskLevel.HIGH,
                        80, 
                        java.util.List.of("High risk reasons")));

        PaymentIntent mockIntent = mock(PaymentIntent.class);
        when(mockIntent.getId()).thenReturn("pi_123");
        when(paymentIntentService.create(any())).thenReturn(mockIntent);

        ArgumentCaptor<HighlightedMessage> msgCaptor = ArgumentCaptor.forClass(HighlightedMessage.class);
        when(repository.save(msgCaptor.capture())).thenReturn(null);

        service.createHighlightIntent(user, roomId, "msg1", "Hello", HighlightType.COLOR, new BigDecimal("2.00"), "req-high", "127.0.0.1", "US", "UA");

        assertEquals(TipStatus.PENDING_REVIEW, msgCaptor.getValue().getStatus());
        
        ArgumentCaptor<com.stripe.param.PaymentIntentCreateParams> paramsCaptor = ArgumentCaptor.forClass(com.stripe.param.PaymentIntentCreateParams.class);
        verify(paymentIntentService).create(paramsCaptor.capture());
        assertEquals("HIGH", paramsCaptor.getValue().getMetadata().get("fraud_risk_level"));
    }

    @Test
    void createHighlightIntent_CriticalRisk_ShouldBlockAndRecordIncident() throws Exception {
        User user = new User();
        user.setId(1L);
        user.setCreatedAt(java.time.Instant.now());
        
        when(paymentService.checkPaymentLock(any(), any(), any(), any(), any(), any()))
                .thenReturn(com.joinlivora.backend.fraud.model.RiskLevel.LOW);
        
        when(fraudRiskService.calculateRisk(any(), any(), anyInt(), any(), anyLong(), anyBoolean(), anyInt()))
                .thenReturn(new com.joinlivora.backend.fraud.model.FraudRiskResult(
                        com.joinlivora.backend.fraud.model.FraudRiskLevel.CRITICAL,
                        95, 
                        java.util.List.of("Critical risk reasons")));

        assertThrows(com.joinlivora.backend.fraud.exception.HighFraudRiskException.class, () ->
                service.createHighlightIntent(user, UUID.randomUUID(), "msg1", "Hello", HighlightType.COLOR, new BigDecimal("2.00"), "req-crit", "127.0.0.1", "US", "UA"));

        verify(enforcementService).recordFraudIncident(any(), contains("CRITICAL_FRAUD_RISK"), anyMap());
        verify(repository, never()).save(any());
    }

    @Test
    void confirmHighlight_ShouldNotOverwritePendingReview() {
        HighlightedMessage msg = new HighlightedMessage();
        msg.setId(UUID.randomUUID());
        msg.setStatus(TipStatus.PENDING_REVIEW);
        msg.setStripePaymentIntentId("pi_123");
        msg.setAmount(new BigDecimal("2.00"));
        msg.setMessageId("msg123");
        msg.setContent("Test content");
        Stream room = new Stream();
        room.setId(UUID.randomUUID());
        User creator = new User();
        creator.setId(2L);
        room.setCreator(creator);
        msg.setRoomId(room);
        User user = new User();
        user.setId(1L);
        user.setEmail("test@test.com");
        msg.setUserId(user);
        msg.setHighlightType(HighlightType.COLOR);
        
        when(repository.findByStripePaymentIntentId("pi_123")).thenReturn(Optional.of(msg));
        
        service.confirmHighlight("pi_123");
        
        assertEquals(TipStatus.PENDING_REVIEW, msg.getStatus());
        verify(repository).save(msg);
    }

    @Test
    void createHighlightIntent_WhenLocked_ShouldThrowException() {
        User user = new User();
        user.setId(1L);
        user.setCreatedAt(java.time.Instant.now());
        doThrow(new PaymentLockedException("Locked"))
                .when(paymentService).checkPaymentLock(eq(user), any(), any(), any(), any(), any());

        assertThrows(PaymentLockedException.class, () ->
                service.createHighlightIntent(user, UUID.randomUUID(), "msg1", "Hello", HighlightType.COLOR, new BigDecimal("2.00"), "req1", null, null, null)
        );

        verifyNoInteractions(stripeClient);
    }
}









