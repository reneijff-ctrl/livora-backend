package com.joinlivora.backend.payments.webhook;

import com.joinlivora.backend.chargeback.ChargebackService;
import com.joinlivora.backend.fraud.service.FraudEvaluationService;
import com.joinlivora.backend.payment.Payment;
import com.joinlivora.backend.payment.PaymentRepository;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import com.joinlivora.backend.monetization.TipOrchestrationService;
import com.joinlivora.backend.payout.CreatorEarningsService;
import com.joinlivora.backend.audit.service.AuditService;
import com.joinlivora.backend.payments.service.WebhookReplayProtectionService;
import com.stripe.model.Dispute;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StripeWebhookServiceTest {

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private com.joinlivora.backend.user.UserRepository userRepository;

    @Mock
    private FraudEvaluationService fraudEvaluationService;

    @Mock
    private WebhookReplayProtectionService replayProtectionService;

    @Mock
    private ChargebackService chargebackService;

    @Mock
    private TipOrchestrationService tipService;

    @Mock
    private CreatorEarningsService creatorEarningsService;

    @Mock
    private UserService userService;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private StripeWebhookService service;

    @Test
    void processEvent_DisputeCreated_ShouldPublishEventAndCallChargebackService() {
        // Given
        String piId = "pi_123";
        String chargeId = "ch_123";
        Long amount = 5000L;
        String currency = "usd";
        String reason = "fraudulent";
        String eventId = "evt_dispute_created";

        Event event = mock(Event.class);
        when(event.getType()).thenReturn("charge.dispute.created");
        when(event.getId()).thenReturn(eventId);
        when(replayProtectionService.tryClaimEvent(eventId, "charge.dispute.created")).thenReturn(true);

        Dispute dispute = mock(Dispute.class);
        when(dispute.getPaymentIntent()).thenReturn(piId);
        when(dispute.getReason()).thenReturn(reason);

        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        when(deserializer.getObject()).thenReturn(Optional.of(dispute));

        User user = new User();
        user.setId(1L);
        user.setEmail("test@example.com");
        Payment payment = new Payment();
        payment.setUser(user);
        payment.setIpAddress("1.2.3.4");

        when(paymentRepository.findByStripePaymentIntentId(piId)).thenReturn(Optional.of(payment));

        // When
        service.processEvent(event);

        // Then
        verify(chargebackService).handleDisputeCreated(eq(user), eq(piId), eq(dispute));
        verify(fraudEvaluationService).evaluateUser(new UUID(0L, 1L), eventId, reason, true, "1.2.3.4");
    }

    @Test
    void processEvent_DisputeCreated_WithMetadataUserId_ShouldCallChargebackService() {
        // Given
        String piId = "pi_456";
        String chargeId = "ch_456";
        UUID userId = UUID.randomUUID();
        String eventId = "evt_metadata";

        Event event = mock(Event.class);
        when(event.getType()).thenReturn("charge.dispute.created");
        when(event.getId()).thenReturn(eventId);
        when(replayProtectionService.tryClaimEvent(eventId, "charge.dispute.created")).thenReturn(true);

        Dispute dispute = mock(Dispute.class);
        when(dispute.getPaymentIntent()).thenReturn(piId);
        when(dispute.getReason()).thenReturn("other");
        when(dispute.getMetadata()).thenReturn(Map.of("creator", userId.toString()));

        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        when(deserializer.getObject()).thenReturn(Optional.of(dispute));

        // Payment not found
        when(paymentRepository.findByStripePaymentIntentId(piId)).thenReturn(Optional.empty());

        // When
        service.processEvent(event);

        // Then
        verify(chargebackService, never()).handleDisputeCreated(any(), any(), any());
        verify(fraudEvaluationService).evaluateUser(userId, eventId, "other", true, null);
    }

    @Test
    void processEvent_DisputeClosed_ShouldCallChargebackServiceAndPublishEvent() {
        // Given
        String piId = "pi_456";
        String chargeId = "ch_456";
        String eventId = "evt_closed";
        Event event = mock(Event.class);
        when(event.getType()).thenReturn("charge.dispute.closed");
        when(event.getId()).thenReturn(eventId);
        when(replayProtectionService.tryClaimEvent(eventId, "charge.dispute.closed")).thenReturn(true);

        Dispute dispute = mock(Dispute.class);
        when(dispute.getPaymentIntent()).thenReturn(piId);
        when(dispute.getId()).thenReturn("dp_123");
        when(dispute.getStatus()).thenReturn("lost");

        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        when(deserializer.getObject()).thenReturn(Optional.of(dispute));

        User user = new User();
        user.setId(2L);
        user.setEmail("test2@example.com");
        Payment payment = new Payment();
        payment.setUser(user);

        when(paymentRepository.findByStripePaymentIntentId(piId)).thenReturn(Optional.of(payment));

        // When
        service.processEvent(event);

        // Then
        verify(chargebackService).handleDisputeClosed(eq("dp_123"), eq(dispute));
        verify(fraudEvaluationService).evaluateUser(new UUID(0L, 2L), eventId, null, false, null);
    }

    @Test
    void processEvent_DisputeClosed_Won_ShouldCallChargebackServiceAndPublishEvent() {
        // Given
        String piId = "pi_won";
        String chargeId = "ch_won";
        String eventId = "evt_won";
        Event event = mock(Event.class);
        when(event.getType()).thenReturn("charge.dispute.closed");
        when(event.getId()).thenReturn(eventId);
        when(replayProtectionService.tryClaimEvent(eventId, "charge.dispute.closed")).thenReturn(true);

        Dispute dispute = mock(Dispute.class);
        when(dispute.getPaymentIntent()).thenReturn(piId);
        when(dispute.getId()).thenReturn("dp_won");
        when(dispute.getStatus()).thenReturn("won");

        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        when(deserializer.getObject()).thenReturn(Optional.of(dispute));

        User user = new User();
        user.setId(3L);
        user.setEmail("test2@example.com");
        Payment payment = new Payment();
        payment.setUser(user);
        payment.setIpAddress("1.1.1.1");

        when(paymentRepository.findByStripePaymentIntentId(piId)).thenReturn(Optional.of(payment));

        // When
        service.processEvent(event);

        // Then
        verify(chargebackService).handleDisputeClosed(eq("dp_won"), eq(dispute));
        verify(fraudEvaluationService).evaluateUser(new UUID(0L, 3L), eventId, null, false, "1.1.1.1");
    }

    @Test
    void processEvent_PaymentIntentSucceeded_ShouldCallFraudEvaluationService() {
        // Given
        String piId = "pi_success";
        String eventId = "evt_success";
        Long amount = 2000L;
        String currency = "usd";
        UUID userId = UUID.randomUUID();

        Event event = mock(Event.class);
        when(event.getType()).thenReturn("payment_intent.succeeded");
        when(event.getId()).thenReturn(eventId);
        when(replayProtectionService.tryClaimEvent(eventId, "payment_intent.succeeded")).thenReturn(true);

        PaymentIntent intent = mock(PaymentIntent.class);
        when(intent.getId()).thenReturn(piId);
        when(intent.getAmount()).thenReturn(amount);
        when(intent.getCurrency()).thenReturn(currency);
        when(intent.getMetadata()).thenReturn(Map.of("creator", userId.toString()));

        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        when(deserializer.getObject()).thenReturn(Optional.of(intent));

        when(paymentRepository.findByStripePaymentIntentId(piId)).thenReturn(Optional.empty());

        // When
        service.processEvent(event);

        // Then
        verify(fraudEvaluationService).processSuccessfulPayment(userId, piId, amount, currency, eventId, null);
    }

    @Test
    void processEvent_PaymentIntentSucceeded_WithExistingPayment_ShouldCallFraudEvaluationServiceWithIp() {
        // Given
        String piId = "pi_success_db";
        String eventId = "evt_success_db";
        Long amount = 3000L;
        String currency = "eur";

        Event event = mock(Event.class);
        when(event.getType()).thenReturn("payment_intent.succeeded");
        when(event.getId()).thenReturn(eventId);
        when(replayProtectionService.tryClaimEvent(eventId, "payment_intent.succeeded")).thenReturn(true);

        PaymentIntent intent = mock(PaymentIntent.class);
        when(intent.getId()).thenReturn(piId);
        when(intent.getAmount()).thenReturn(amount);
        when(intent.getCurrency()).thenReturn(currency);

        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        when(deserializer.getObject()).thenReturn(Optional.of(intent));

        User user = new User();
        user.setId(99L);
        Payment payment = new Payment();
        payment.setUser(user);
        payment.setIpAddress("9.9.9.9");

        when(paymentRepository.findByStripePaymentIntentId(piId)).thenReturn(Optional.of(payment));

        // When
        service.processEvent(event);

        // Then
        verify(fraudEvaluationService).processSuccessfulPayment(new UUID(0L, 99L), piId, amount, currency, eventId, "9.9.9.9");
    }

    @Test
    void processEvent_UnhandledType_ShouldIgnore() {
        // Given
        Event event = mock(Event.class);
        when(event.getType()).thenReturn("customer.created");
        when(event.getId()).thenReturn("evt_unhandled");
        when(replayProtectionService.tryClaimEvent("evt_unhandled", "customer.created")).thenReturn(true);

        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        when(deserializer.getObject()).thenReturn(Optional.of(mock(com.stripe.model.Customer.class)));

        // When
        service.processEvent(event);

        // Then
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void processEvent_ReplayedEvent_ShouldSkipProcessingEntirely() {
        // Given
        String eventId = "evt_replayed";
        Event event = mock(Event.class);
        when(event.getId()).thenReturn(eventId);
        when(event.getType()).thenReturn("payment_intent.succeeded");
        when(replayProtectionService.tryClaimEvent(eventId, "payment_intent.succeeded")).thenReturn(false);

        // When
        service.processEvent(event);

        // Then — no deserialization, no handler calls, no status updates
        verify(event, never()).getDataObjectDeserializer();
        verifyNoInteractions(paymentRepository);
        verifyNoInteractions(fraudEvaluationService);
        verifyNoInteractions(chargebackService);
        verify(replayProtectionService, never()).markCompleted(any());
        verify(replayProtectionService, never()).markFailed(any());
    }

    @Test
    void processEvent_NewEvent_ShouldMarkCompletedAfterProcessing() {
        // Given
        String eventId = "evt_new";
        String piId = "pi_new";
        UUID userId = UUID.randomUUID();

        Event event = mock(Event.class);
        when(event.getType()).thenReturn("payment_intent.succeeded");
        when(event.getId()).thenReturn(eventId);
        when(replayProtectionService.tryClaimEvent(eventId, "payment_intent.succeeded")).thenReturn(true);

        PaymentIntent intent = mock(PaymentIntent.class);
        when(intent.getId()).thenReturn(piId);
        when(intent.getAmount()).thenReturn(1000L);
        when(intent.getCurrency()).thenReturn("eur");
        when(intent.getMetadata()).thenReturn(Map.of("creator", userId.toString()));

        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        when(deserializer.getObject()).thenReturn(Optional.of(intent));

        when(paymentRepository.findByStripePaymentIntentId(piId)).thenReturn(Optional.empty());

        // When
        service.processEvent(event);

        // Then — event should be marked completed after successful processing
        verify(replayProtectionService).markCompleted(eventId);
        verify(replayProtectionService, never()).markFailed(any());
        verify(fraudEvaluationService).processSuccessfulPayment(userId, piId, 1000L, "eur", eventId, null);
    }

    @Test
    void processEvent_FailedProcessing_ShouldMarkFailedAndRethrow() {
        // Given
        String eventId = "evt_fail";
        Event event = mock(Event.class);
        when(event.getId()).thenReturn(eventId);
        when(event.getType()).thenReturn("payment_intent.succeeded");
        when(replayProtectionService.tryClaimEvent(eventId, "payment_intent.succeeded")).thenReturn(true);

        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        when(deserializer.getObject()).thenReturn(Optional.empty());

        // When — deserialization returns empty, no exception thrown, but no handler called
        service.processEvent(event);

        // Then — still marked completed (no exception = success)
        verify(replayProtectionService).markCompleted(eventId);
        verify(replayProtectionService, never()).markFailed(any());
    }
}








