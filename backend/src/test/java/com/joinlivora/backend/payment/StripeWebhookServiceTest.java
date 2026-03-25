package com.joinlivora.backend.payment;

import com.joinlivora.backend.analytics.AnalyticsEventPublisher;
import com.joinlivora.backend.analytics.AnalyticsEventType;
import com.joinlivora.backend.aml.service.AMLRulesEngine;
import com.joinlivora.backend.audit.service.AuditService;
import com.joinlivora.backend.fraud.service.FraudDetectionService;
import com.joinlivora.backend.monetization.TipOrchestrationService;
import com.joinlivora.backend.payout.PayoutAuditService;
import com.joinlivora.backend.payout.StripeConnectService;
import com.joinlivora.backend.payments.webhook.ChargebackEvent;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import com.stripe.model.Dispute;
import com.stripe.model.Invoice;
import com.stripe.model.StripeError;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import com.stripe.model.checkout.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StripeWebhookServiceTest {

    @Mock
    private UserService userService;
    @Mock
    private AnalyticsEventPublisher analyticsEventPublisher;
    @Mock
    private FraudDetectionService fraudDetectionService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private UserSubscriptionRepository subscriptionRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private WebhookEventRepository webhookEventRepository;
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private io.micrometer.core.instrument.MeterRegistry meterRegistry;
    @Mock
    private com.joinlivora.backend.token.TokenService tokenService;
    @Mock
    private com.joinlivora.backend.token.TokenPackageRepository tokenPackageRepository;
    @Mock
    private com.joinlivora.backend.payout.CreatorConnectService creatorConnectService;
    @Mock
    private StripeConnectService stripeConnectService;
    @Mock
    private com.joinlivora.backend.creator.service.CreatorStripeAccountService creatorStripeAccountService;
    @Mock
    private com.joinlivora.backend.creator.service.CreatorStripeService creatorStripeService;
    @Mock
    private com.joinlivora.backend.payout.PayoutRepository payoutRepository;
    @Mock
    private com.joinlivora.backend.payout.CreatorPayoutRepository creatorPayoutRepository;
    @Mock
    private com.joinlivora.backend.payout.CreatorEarningsService creatorEarningsService;
    @Mock
    private TipOrchestrationService tipService;
    @Mock
    private com.joinlivora.backend.tip.TipService directTipService;
    @Mock
    private com.joinlivora.backend.monetization.PPVPurchaseService ppvPurchaseService;
    @Mock
    private com.joinlivora.backend.monetization.HighlightedMessageService highlightedMessageService;
    @Mock
    private com.joinlivora.backend.streaming.StreamService LiveStreamService;
    @Mock
    private InvoiceService invoiceService;
    @Mock
    private AMLRulesEngine amlRulesEngine;
    @Mock
    private AuditService auditService;
    @Mock
    private PayoutAuditService payoutAuditService;
    @Mock
    private com.joinlivora.backend.payout.PayoutRequestRepository payoutRequestRepository;
    @Mock
    private com.joinlivora.backend.payout.CreatorEarningRepository creatorEarningRepository;
    @Mock
    private com.stripe.StripeClient stripeClient;
    @Mock
    private org.springframework.beans.factory.ObjectProvider<StripeWebhookService> selfProvider;

    @InjectMocks
    private StripeWebhookService service;

    private MockedStatic<Webhook> mockedWebhook;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "stripeEnabled", true);
        ReflectionTestUtils.setField(service, "endpointSecret", "whsec_test_secret");
        mockedWebhook = mockStatic(Webhook.class);
    }

    @AfterEach
    void tearDown() {
        mockedWebhook.close();
    }

    @Test
    void handleInvoicePaymentFailed_ShouldPersistFailedPaymentAndCallFraudDetection() {
        // Given
        String email = "creator@example.com";
        Invoice invoice = mock(Invoice.class);
        when(invoice.getCustomerEmail()).thenReturn(email);
        when(invoice.getAmountDue()).thenReturn(1000L); // $10.00
        when(invoice.getCurrency()).thenReturn("usd");
        when(invoice.getMetadata()).thenReturn(Map.of("ip_address", "1.2.3.4", "country", "US"));

        User user = new User();
        user.setId(1L);
        user.setEmail(email);
        when(userService.getByEmail(email)).thenReturn(user);

        // When
        service.handleInvoicePaymentFailed(invoice);

        // Then
        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        
        Payment savedPayment = paymentCaptor.getValue();
        assertEquals(user, savedPayment.getUser());
        assertEquals(0, new BigDecimal("10.00").compareTo(savedPayment.getAmount()));
        assertEquals(false, savedPayment.isSuccess());
        assertEquals("1.2.3.4", savedPayment.getIpAddress());
        assertEquals("US", savedPayment.getCountry());

        verify(analyticsEventPublisher).publishEvent(
                eq(AnalyticsEventType.PAYMENT_FAILED),
                eq(user),
                any(Map.class)
        );
        verify(fraudDetectionService).evaluate(eq(user), eq("1.2.3.4"), eq("US"));
        verify(amlRulesEngine).evaluateRules(user, BigDecimal.ZERO);
    }

    @Test
    void handleInvoicePaymentFailed_ShouldSkipIfNoEmail() {
        // Given
        Invoice invoice = mock(Invoice.class);
        when(invoice.getCustomerEmail()).thenReturn(null);

        // When
        service.handleInvoicePaymentFailed(invoice);

        // Then
        verifyNoInteractions(userService);
        verifyNoInteractions(analyticsEventPublisher);
        verifyNoInteractions(fraudDetectionService);
    }

    @Test
    void handlePaymentIntentFailed_ShouldPersistFailedPayment() {
        // Given
        String piId = "pi_failed";
        PaymentIntent intent = mock(PaymentIntent.class);
        when(intent.getId()).thenReturn(piId);
        when(intent.getAmount()).thenReturn(5000L); // $50.00
        when(intent.getCurrency()).thenReturn("eur");
        when(intent.getMetadata()).thenReturn(Map.of("user_id", "123", "ip_address", "8.8.8.8", "country", "FR"));
        
        StripeError error = mock(StripeError.class);
        when(error.getMessage()).thenReturn("Insufficient funds");
        when(intent.getLastPaymentError()).thenReturn(error);

        User user = new User();
        user.setId(123L);
        when(userService.getById(123L)).thenReturn(user);

        // When
        service.handlePaymentIntentFailed(intent);

        // Then
        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        
        Payment savedPayment = paymentCaptor.getValue();
        assertEquals(user, savedPayment.getUser());
        assertEquals(0, new BigDecimal("50.00").compareTo(savedPayment.getAmount()));
        assertEquals(false, savedPayment.isSuccess());
        assertEquals("Insufficient funds", savedPayment.getFailureReason());
        assertEquals("8.8.8.8", savedPayment.getIpAddress());
        assertEquals("FR", savedPayment.getCountry());
        
        verify(fraudDetectionService).evaluate(eq(user), eq("8.8.8.8"), eq("FR"));
        verify(amlRulesEngine).evaluateRules(user, BigDecimal.ZERO);
    }

    @Test
    void handleChargeDisputeCreated_ShouldPublishChargebackEvent() {
        // Given
        String piId = "pi_123";
        Dispute dispute = mock(Dispute.class);
        when(dispute.getPaymentIntent()).thenReturn(piId);

        User user = new User();
        user.setEmail("fraudster@test.com");
        
        Payment payment = new Payment();
        payment.setUser(user);
        payment.setStripePaymentIntentId(piId);

        when(paymentRepository.findByStripePaymentIntentId(piId)).thenReturn(Optional.of(payment));

        // When
        service.handleChargeDisputeCreated(dispute);

        // Then
        ArgumentCaptor<ChargebackEvent> captor = ArgumentCaptor.forClass(ChargebackEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        
        ChargebackEvent event = captor.getValue();
        assertEquals(user, event.getUser());
        assertEquals(piId, event.getPaymentIntentId());
        assertEquals(dispute, event.getDispute());
    }

    @Test
    void handleChargeDisputeClosed_ShouldPublishChargebackEvent() {
        // Given
        String piId = "pi_123";
        Dispute dispute = mock(Dispute.class);
        when(dispute.getPaymentIntent()).thenReturn(piId);
        when(dispute.getId()).thenReturn("dp_123");
        when(dispute.getStatus()).thenReturn("won");

        User user = new User();
        user.setEmail("fraudster@test.com");
        
        Payment payment = new Payment();
        payment.setUser(user);
        payment.setStripePaymentIntentId(piId);

        when(paymentRepository.findByStripePaymentIntentId(piId)).thenReturn(Optional.of(payment));

        // When
        service.handleChargeDisputeClosed(dispute);

        // Then
        ArgumentCaptor<ChargebackEvent> captor = ArgumentCaptor.forClass(ChargebackEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        
        ChargebackEvent event = captor.getValue();
        assertEquals(user, event.getUser());
        assertEquals(piId, event.getPaymentIntentId());
        assertEquals(dispute, event.getDispute());
    }

    @Test
    void handleChargeDisputeCreated_ShouldWarnIfPaymentNotFound() {
        // Given
        String piId = "pi_unknown";
        Dispute dispute = mock(Dispute.class);
        when(dispute.getPaymentIntent()).thenReturn(piId);

        when(paymentRepository.findByStripePaymentIntentId(piId)).thenReturn(Optional.empty());

        // When
        service.handleChargeDisputeCreated(dispute);

        // Then
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void handleWebhook_ShouldPersistAndDelegate() throws Exception {
        // Given
        String payload = "{\"id\": \"evt_123\", \"type\": \"payment_intent.succeeded\"}";
        String sigHeader = "valid_sig";

        Event event = com.stripe.net.ApiResource.GSON.fromJson(payload, Event.class);
        mockedWebhook.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString())).thenReturn(event);

        when(webhookEventRepository.existsByStripeEventId("evt_123")).thenReturn(false);
        WebhookEvent webhookEvent = new WebhookEvent();
        webhookEvent.setId(UUID.randomUUID());
        when(webhookEventRepository.save(any(WebhookEvent.class))).thenReturn(webhookEvent);
        when(webhookEventRepository.findByIdWithLock(webhookEvent.getId())).thenReturn(Optional.of(webhookEvent));
        when(selfProvider.getIfAvailable()).thenReturn(service);
        
        io.micrometer.core.instrument.Counter counter = mock(io.micrometer.core.instrument.Counter.class);
        when(meterRegistry.counter(anyString(), any(String[].class))).thenReturn(counter);

        // When
        service.handleWebhook(payload, sigHeader);

        // Then
        verify(webhookEventRepository).save(any(WebhookEvent.class));
        verify(meterRegistry).counter(eq("stripe_webhook_events"), eq("type"), eq("payment_intent.succeeded"));
    }

    @Test
    void handleWebhook_ShouldIgnoreDuplicate() throws Exception {
        // Given
        String payload = "{\"id\": \"evt_123\", \"type\": \"payment_intent.succeeded\"}";
        String sigHeader = "valid_sig";

        Event event = com.stripe.net.ApiResource.GSON.fromJson(payload, Event.class);
        mockedWebhook.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString())).thenReturn(event);

        when(webhookEventRepository.existsByStripeEventId("evt_123")).thenReturn(true);

        // When
        service.handleWebhook(payload, sigHeader);

        // Then
        verify(webhookEventRepository, never()).save(any());
        verify(meterRegistry, never()).counter(anyString(), anyString(), anyString());
    }

    @Test
    void handleCheckoutSession_ShouldPersistPaymentAndEarningsWithCamelCaseMetadata() {
        // Given
        String email = "user@example.com";
        Session session = mock(Session.class);
        when(session.getCustomerEmail()).thenReturn(email);
        when(session.getAmountTotal()).thenReturn(5000L);
        when(session.getCurrency()).thenReturn("eur");
        when(session.getPaymentIntent()).thenReturn("pi_123");
        when(session.getSubscription()).thenReturn("sub_123");
        when(session.getMetadata()).thenReturn(Map.of("creator", "456"));

        User user = new User();
        user.setId(123L);
        user.setEmail(email);
        when(userService.getByEmail(email)).thenReturn(user);

        User creator = new User();
        creator.setId(456L);
        when(userService.getById(456L)).thenReturn(creator);

        // Prepare Stripe client mocks to avoid NPE when fetching payment method details
        com.stripe.service.PaymentIntentService piService = mock(com.stripe.service.PaymentIntentService.class);
        when(stripeClient.paymentIntents()).thenReturn(piService);
        try {
            when(piService.retrieve(anyString())).thenReturn(new PaymentIntent());
        } catch (com.stripe.exception.StripeException e) {
            throw new RuntimeException(e);
        }

        // When
        service.handleCheckoutSession(session);

        // Then
        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository, atLeastOnce()).save(paymentCaptor.capture());
        Payment savedPayment = paymentCaptor.getValue();
        assertEquals(0, savedPayment.getAmount().compareTo(new BigDecimal("50.00")));
        assertEquals(creator, savedPayment.getCreator());

        verify(creatorEarningsService).recordSubscriptionEarning(eq(savedPayment), eq(creator));
    }

    @Test
    void handleTipCheckoutSession_ShouldHandleCamelCaseMetadata() {
        // Given
        Session session = mock(Session.class);
        when(session.getMetadata()).thenReturn(Map.of("creator", "456", "userId", "123"));
        when(session.getAmountTotal()).thenReturn(2000L);
        when(session.getCurrency()).thenReturn("eur");
        when(session.getId()).thenReturn("sess_tip");

        User user = new User();
        user.setId(123L);
        when(userService.getById(123L)).thenReturn(user);

        User creator = new User();
        creator.setId(456L);
        when(userService.getById(456L)).thenReturn(creator);

        // Mock direct tip service behavior
        com.joinlivora.backend.tip.DirectTip pendingTip = com.joinlivora.backend.tip.DirectTip.builder()
                .id(java.util.UUID.randomUUID())
                .user(user)
                .creator(creator)
                .amount(new BigDecimal("20.00"))
                .currency("eur")
                .stripeSessionId("sess_tip")
                .status(com.joinlivora.backend.tip.TipStatus.PENDING)
                .build();
        when(directTipService.saveTip(any(com.joinlivora.backend.tip.DirectTip.class))).thenReturn(pendingTip);

        // When
        service.handleTipCheckoutSession(session);

        // Then
        verify(paymentRepository).save(any(Payment.class));
        verify(userService).getById(123L);
        verify(userService).getById(456L);
        verify(directTipService).saveTip(any(com.joinlivora.backend.tip.DirectTip.class));
        verify(directTipService).completeTip(pendingTip.getId());
    }
    
    @Test
    void processEventAsync_AccountUpdated_ShouldCallAllServices() {
        // Given
        Event event = mock(Event.class);
        when(event.getType()).thenReturn("account.updated");
        
        com.stripe.model.Account account = mock(com.stripe.model.Account.class);
        when(account.getId()).thenReturn("acct_123");
        when(account.getChargesEnabled()).thenReturn(true);
        when(account.getPayoutsEnabled()).thenReturn(true);
        when(account.getDetailsSubmitted()).thenReturn(true);

        com.stripe.model.EventDataObjectDeserializer deserializer = mock(com.stripe.model.EventDataObjectDeserializer.class);
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);
        when(deserializer.getObject()).thenReturn(Optional.of(account));

        io.micrometer.core.instrument.Counter counter = mock(io.micrometer.core.instrument.Counter.class);
        when(meterRegistry.counter(anyString(), any(String[].class))).thenReturn(counter);

        UUID internalId = UUID.randomUUID();
        WebhookEvent webhookEvent = new WebhookEvent();
        webhookEvent.setId(internalId);
        webhookEvent.setProcessed(false);
        when(webhookEventRepository.findByIdWithLock(internalId)).thenReturn(Optional.of(webhookEvent));
        when(selfProvider.getIfAvailable()).thenReturn(service);

        // When
        service.processEventAsync(event, internalId);

        // Then
        verify(stripeConnectService).updateAccountStatus("acct_123", true, true, true);
        verify(creatorStripeAccountService).updateAccountStatus("acct_123", true);
        verify(creatorStripeService).updateAccountStatus("acct_123", true, true);
    }
    
    @Test
    void handleTokenCheckoutSession_ShouldCreditTokensAndRecordPayment() throws com.stripe.exception.StripeException {
        // Given
        String email = "creator@test.com";
        UUID packageId = UUID.randomUUID();
        Session session = mock(Session.class);
        when(session.getCustomerEmail()).thenReturn(email);
        when(session.getMetadata()).thenReturn(Map.of("package_id", packageId.toString()));
        when(session.getAmountTotal()).thenReturn(1000L); // $10.00
        when(session.getCurrency()).thenReturn("eur");
        when(session.getPaymentIntent()).thenReturn("pi_token_123");
        when(session.getId()).thenReturn("cs_token_123");

        User user = new User();
        user.setId(1L);
        user.setEmail(email);
        when(userService.getByEmail(email)).thenReturn(user);

        com.joinlivora.backend.token.TokenPackage tokenPackage = new com.joinlivora.backend.token.TokenPackage();
        tokenPackage.setTokenAmount(100);
        when(tokenPackageRepository.findById(packageId)).thenReturn(Optional.of(tokenPackage));

        // Mock Stripe client services to avoid NPE
        com.stripe.service.PaymentIntentService piService = mock(com.stripe.service.PaymentIntentService.class);
        when(stripeClient.paymentIntents()).thenReturn(piService);
        when(piService.retrieve(anyString())).thenReturn(mock(PaymentIntent.class));

        // When
        service.handleTokenCheckoutSession(session);

        // Then
        verify(tokenService).creditTokens(user, 100, "Stripe Session: cs_token_123");
        verify(paymentRepository).save(any(Payment.class));
        verify(auditService).logEvent(
                eq(new UUID(0L, 1L)),
                eq("TOKENS_PURCHASED"),
                eq("USER"),
                eq(new UUID(0L, 1L)),
                any(),
                isNull(),
                isNull()
        );
    }
    @Test
    void handleTokenCheckoutSession_ShouldCreditTokensUsingClientReferenceId() throws com.stripe.exception.StripeException {
        // Given
        String email = "viewer@test.com";
        Long userId = 123L;
        UUID packageId = UUID.randomUUID();
        Session session = mock(Session.class);
        when(session.getCustomerEmail()).thenReturn(email);
        when(session.getClientReferenceId()).thenReturn(userId.toString());
        when(session.getMetadata()).thenReturn(Map.of("package_id", packageId.toString()));
        when(session.getAmountTotal()).thenReturn(1000L); 
        when(session.getCurrency()).thenReturn("eur");
        when(session.getPaymentIntent()).thenReturn("pi_token_123");
        when(session.getId()).thenReturn("cs_token_123");

        User user = new User();
        user.setId(userId);
        user.setEmail(email);
        // Prioritize userId resolution
        when(userService.getById(userId)).thenReturn(user);

        com.joinlivora.backend.token.TokenPackage tokenPackage = new com.joinlivora.backend.token.TokenPackage();
        tokenPackage.setTokenAmount(500);
        when(tokenPackageRepository.findById(packageId)).thenReturn(Optional.of(tokenPackage));

        com.stripe.service.PaymentIntentService piService = mock(com.stripe.service.PaymentIntentService.class);
        when(stripeClient.paymentIntents()).thenReturn(piService);
        when(piService.retrieve(anyString())).thenReturn(mock(PaymentIntent.class));

        // When
        service.handleTokenCheckoutSession(session);

        // Then
        verify(userService).getById(userId);
        verify(tokenService).creditTokens(user, 500, "Stripe Session: cs_token_123");
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    void handleTokenCheckoutSession_ShouldReturnIfAlreadyProcessed() {
        // Given
        Session session = mock(Session.class);
        when(session.getId()).thenReturn("cs_token_123");
        when(paymentRepository.existsByStripeSessionId("cs_token_123")).thenReturn(true);

        // When
        service.handleTokenCheckoutSession(session);

        // Then
        verify(paymentRepository, never()).save(any(Payment.class));
        verify(tokenService, never()).creditTokens(any(), anyInt(), any());
    }

    // ---- Transfer webhook tests for payoutId (CreatorPayout) and payoutRequestId (PayoutRequest) ----

    @Test
    void handleTransferPaid_ShouldMatchAndUpdateCreatorPayout() {
        // Given
        UUID payoutId = UUID.randomUUID();
        com.stripe.model.Transfer transfer = mock(com.stripe.model.Transfer.class);
        when(transfer.getId()).thenReturn("tr_paid_123");
        when(transfer.getMetadata()).thenReturn(Map.of("payoutId", payoutId.toString()));

        com.joinlivora.backend.payout.CreatorPayout payout = com.joinlivora.backend.payout.CreatorPayout.builder()
                .id(payoutId)
                .creatorId(new UUID(0L, 42L))
                .amount(new BigDecimal("100.00"))
                .currency("EUR")
                .status(com.joinlivora.backend.payout.PayoutStatus.PENDING)
                .build();
        when(creatorPayoutRepository.findById(payoutId)).thenReturn(Optional.of(payout));

        // When
        service.handleTransferPaid(transfer);

        // Then
        assertEquals(com.joinlivora.backend.payout.PayoutStatus.COMPLETED, payout.getStatus());
        assertEquals("tr_paid_123", payout.getStripeTransferId());
        verify(creatorPayoutRepository).save(payout);
        verify(payoutAuditService).logStatusChange(eq(payoutId), eq(com.joinlivora.backend.payout.PayoutStatus.PENDING),
                eq(com.joinlivora.backend.payout.PayoutStatus.COMPLETED), any(), any(), any());
    }

    @Test
    void handleTransferFailed_ShouldMatchAndUpdateCreatorPayout() {
        // Given
        UUID payoutId = UUID.randomUUID();
        com.stripe.model.Transfer transfer = mock(com.stripe.model.Transfer.class);
        when(transfer.getId()).thenReturn("tr_fail_123");
        when(transfer.getMetadata()).thenReturn(Map.of("payoutId", payoutId.toString()));

        com.joinlivora.backend.payout.CreatorPayout payout = com.joinlivora.backend.payout.CreatorPayout.builder()
                .id(payoutId)
                .creatorId(new UUID(0L, 42L))
                .amount(new BigDecimal("100.00"))
                .currency("EUR")
                .status(com.joinlivora.backend.payout.PayoutStatus.PENDING)
                .build();
        when(creatorPayoutRepository.findById(payoutId)).thenReturn(Optional.of(payout));

        // When
        service.handleTransferFailed(transfer);

        // Then
        assertEquals(com.joinlivora.backend.payout.PayoutStatus.FAILED, payout.getStatus());
        assertEquals("tr_fail_123", payout.getStripeTransferId());
        verify(creatorPayoutRepository).save(payout);
        verify(payoutAuditService).logStatusChange(eq(payoutId), eq(com.joinlivora.backend.payout.PayoutStatus.PENDING),
                eq(com.joinlivora.backend.payout.PayoutStatus.FAILED), any(), any(), any());
    }

    @Test
    void handleTransferPaid_ShouldMatchAdminApprovedPayoutRequest() {
        // Given — admin-approved payout uses payoutRequestId metadata key
        UUID requestId = UUID.randomUUID();
        com.stripe.model.Transfer transfer = mock(com.stripe.model.Transfer.class);
        when(transfer.getId()).thenReturn("tr_admin_paid_123");
        when(transfer.getMetadata()).thenReturn(Map.of("payoutRequestId", requestId.toString()));

        com.joinlivora.backend.payout.PayoutRequest request = com.joinlivora.backend.payout.PayoutRequest.builder()
                .id(requestId)
                .creatorId(UUID.randomUUID())
                .amount(new BigDecimal("200.00"))
                .currency("EUR")
                .status(com.joinlivora.backend.payout.PayoutRequestStatus.APPROVED)
                .build();
        when(payoutRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

        // When
        service.handleTransferPaid(transfer);

        // Then — webhook moves APPROVED → COMPLETED
        assertEquals(com.joinlivora.backend.payout.PayoutRequestStatus.COMPLETED, request.getStatus());
        assertEquals("tr_admin_paid_123", request.getStripeTransferId());
        verify(payoutRequestRepository).save(request);
    }

    @Test
    void handleTransferFailed_ShouldMatchAdminApprovedPayoutRequest() {
        // Given — admin-approved payout uses payoutRequestId metadata key
        UUID requestId = UUID.randomUUID();
        com.stripe.model.Transfer transfer = mock(com.stripe.model.Transfer.class);
        when(transfer.getId()).thenReturn("tr_admin_fail_123");
        when(transfer.getMetadata()).thenReturn(Map.of("payoutRequestId", requestId.toString()));

        com.joinlivora.backend.payout.PayoutRequest request = com.joinlivora.backend.payout.PayoutRequest.builder()
                .id(requestId)
                .creatorId(UUID.randomUUID())
                .amount(new BigDecimal("200.00"))
                .currency("EUR")
                .status(com.joinlivora.backend.payout.PayoutRequestStatus.APPROVED)
                .build();
        when(payoutRequestRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(creatorEarningRepository.findAllByPayoutRequest(request)).thenReturn(java.util.List.of());

        // When
        service.handleTransferFailed(transfer);

        // Then — webhook moves APPROVED → FAILED
        assertEquals(com.joinlivora.backend.payout.PayoutRequestStatus.FAILED, request.getStatus());
        assertEquals("tr_admin_fail_123", request.getStripeTransferId());
        assertEquals("Stripe transfer failed: tr_admin_fail_123", request.getRejectionReason());
        verify(payoutRequestRepository).save(request);
        verify(creatorEarningRepository).findAllByPayoutRequest(request);
    }

    @Test
    void handleTransferFailed_ShouldUnlockEarningsForFailedPayoutRequest() {
        // Given — admin-approved payout with locked earnings
        UUID requestId = UUID.randomUUID();
        com.stripe.model.Transfer transfer = mock(com.stripe.model.Transfer.class);
        when(transfer.getId()).thenReturn("tr_admin_fail_unlock_123");
        when(transfer.getMetadata()).thenReturn(Map.of("payoutRequestId", requestId.toString()));

        com.joinlivora.backend.payout.PayoutRequest request = com.joinlivora.backend.payout.PayoutRequest.builder()
                .id(requestId)
                .creatorId(UUID.randomUUID())
                .amount(new BigDecimal("150.00"))
                .currency("EUR")
                .status(com.joinlivora.backend.payout.PayoutRequestStatus.APPROVED)
                .build();
        when(payoutRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

        // Two earnings locked for this payout request
        com.joinlivora.backend.payout.CreatorEarning earning1 = com.joinlivora.backend.payout.CreatorEarning.builder()
                .locked(true).payoutRequest(request).netAmount(new BigDecimal("100.00")).build();
        com.joinlivora.backend.payout.CreatorEarning earning2 = com.joinlivora.backend.payout.CreatorEarning.builder()
                .locked(true).payoutRequest(request).netAmount(new BigDecimal("50.00")).build();
        when(creatorEarningRepository.findAllByPayoutRequest(request)).thenReturn(java.util.List.of(earning1, earning2));

        // When
        service.handleTransferFailed(transfer);

        // Then — earnings are unlocked
        assertEquals(false, earning1.isLocked());
        assertEquals(null, earning1.getPayoutRequest());
        assertEquals(false, earning2.isLocked());
        assertEquals(null, earning2.getPayoutRequest());
        verify(creatorEarningRepository).saveAll(java.util.List.of(earning1, earning2));
    }

    @Test
    void handleTransferFailed_CreatorPayout_ShouldNotPermanentlyReduceBalance() {
        // Given — a PENDING CreatorPayout that fails via webhook
        // After marking FAILED, calculateAvailablePayout should NOT subtract it
        // (it only subtracts COMPLETED and PENDING, not FAILED)
        UUID payoutId = UUID.randomUUID();
        com.stripe.model.Transfer transfer = mock(com.stripe.model.Transfer.class);
        when(transfer.getId()).thenReturn("tr_fail_balance_123");
        when(transfer.getMetadata()).thenReturn(Map.of("payoutId", payoutId.toString()));

        com.joinlivora.backend.payout.CreatorPayout payout = com.joinlivora.backend.payout.CreatorPayout.builder()
                .id(payoutId)
                .creatorId(new UUID(0L, 42L))
                .amount(new BigDecimal("75.00"))
                .currency("EUR")
                .status(com.joinlivora.backend.payout.PayoutStatus.PENDING)
                .build();
        when(creatorPayoutRepository.findById(payoutId)).thenReturn(Optional.of(payout));

        // When
        service.handleTransferFailed(transfer);

        // Then — status is FAILED, so it won't be included in COMPLETED or PENDING sums
        assertEquals(com.joinlivora.backend.payout.PayoutStatus.FAILED, payout.getStatus());
        // Verify the payout was saved with FAILED status (not COMPLETED or PENDING)
        // This means calculateAvailablePayout (which only subtracts COMPLETED + PENDING) will exclude it
        verify(creatorPayoutRepository).save(payout);
        verify(payoutAuditService).logStatusChange(eq(payoutId), eq(com.joinlivora.backend.payout.PayoutStatus.PENDING),
                eq(com.joinlivora.backend.payout.PayoutStatus.FAILED), any(), any(), any());
    }
}










