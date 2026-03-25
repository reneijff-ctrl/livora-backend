package com.joinlivora.backend.payment;

import com.joinlivora.backend.admin.service.AdminRealtimeEventService;
import com.joinlivora.backend.audit.service.AuditService;
import com.joinlivora.backend.monetization.TipOrchestrationService;
import com.joinlivora.backend.payout.CreatorEarningsService;
import com.joinlivora.backend.creator.service.CreatorStripeService;
import com.joinlivora.backend.aml.service.AMLRulesEngine;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import com.joinlivora.backend.payout.PayoutAuditService;
import com.joinlivora.backend.payout.PayoutActorType;
import com.joinlivora.backend.payout.PayoutStatus;
import com.stripe.StripeClient;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Dispute;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.Invoice;
import com.stripe.model.PaymentIntent;
import com.stripe.model.PaymentMethod;
import com.stripe.model.StripeObject;
import com.stripe.model.Subscription;
import com.stripe.model.Transfer;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.joinlivora.backend.exception.ResourceNotFoundException;
import com.joinlivora.backend.tip.DirectTip;
import com.joinlivora.backend.tip.TipStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service("paymentStripeWebhookService")
@RequiredArgsConstructor
@Slf4j
public class StripeWebhookService {

    private final StripeClient stripeClient;
    private final UserService userService;
    private final UserSubscriptionRepository subscriptionRepository;
    private final PaymentRepository paymentRepository;
    private final WebhookEventRepository webhookEventRepository;
    private final SubscriptionService subscriptionService;
    private final MeterRegistry meterRegistry;
    private final com.joinlivora.backend.analytics.AnalyticsEventPublisher analyticsEventPublisher;
    private final com.joinlivora.backend.token.TokenService tokenService;
    private final com.joinlivora.backend.token.TokenPackageRepository tokenPackageRepository;
    private final com.joinlivora.backend.payout.CreatorConnectService creatorConnectService;
    private final com.joinlivora.backend.payout.StripeConnectService stripeConnectService;
    private final CreatorStripeService creatorStripeService;
    private final com.joinlivora.backend.creator.service.CreatorStripeAccountService creatorStripeAccountService;
    private final com.joinlivora.backend.payout.PayoutRepository payoutRepository;
    private final com.joinlivora.backend.payout.CreatorPayoutRepository creatorPayoutRepository;
    private final CreatorEarningsService creatorEarningsService;
    private final AMLRulesEngine amlRulesEngine;
    private final TipOrchestrationService tipService;
    private final com.joinlivora.backend.tip.TipService directTipService;
    private final com.joinlivora.backend.monetization.PPVPurchaseService ppvPurchaseService;
    private final com.joinlivora.backend.monetization.HighlightedMessageService highlightedMessageService;
    private final com.joinlivora.backend.streaming.StreamService streamService;
    private final InvoiceService invoiceService;
    private final AuditService auditService;
    private final com.joinlivora.backend.fraud.service.FraudDetectionService fraudDetectionService;
    private final ApplicationEventPublisher eventPublisher;
    private final PayoutAuditService payoutAuditService;
    private final AdminRealtimeEventService adminRealtimeEventService;
    private final PaymentService paymentService;
    private final com.joinlivora.backend.chargeback.ChargebackService canonicalChargebackService;
    private final com.joinlivora.backend.payout.StripeAccountRepository stripeAccountRepository;
    private final com.joinlivora.backend.payout.CreatorPayoutSettingsRepository creatorPayoutSettingsRepository;
    private final com.joinlivora.backend.payout.PayoutRequestRepository payoutRequestRepository;
    private final com.joinlivora.backend.payout.CreatorEarningRepository creatorEarningRepository;
    
    private final ObjectProvider<StripeWebhookService> selfProvider;

    @Value("${stripe.webhook.secret}")
    private String endpointSecret;

    @Value("${payments.stripe.enabled:true}")
    private boolean stripeEnabled;

    /**
     * Handles the raw Stripe webhook payload.
     * Verifies the signature, persists the event for idempotency, and delegates to async processing.
     *
     * @param payload   The raw request body
     * @param sigHeader The Stripe-Signature header
     * @throws SignatureVerificationException if signature validation fails
     */
    public void handleWebhook(String payload, String sigHeader) throws SignatureVerificationException {
        if (!stripeEnabled) {
            log.info("Stripe disabled: handleWebhook short-circuited");
            return;
        }
        if (sigHeader == null || sigHeader.isEmpty()) {
            throw new SignatureVerificationException("Missing Stripe-Signature header", null);
        }

        if (endpointSecret == null || endpointSecret.trim().isEmpty()) {
            throw new IllegalStateException("Stripe webhook secret is not configured");
        }

        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, endpointSecret);
        } catch (SignatureVerificationException e) {
            log.error("SECURITY: Invalid Stripe signature: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("SECURITY: Error constructing Stripe event", e);
            throw new RuntimeException("Error processing event", e);
        }

        String stripeEventId = event.getId();
        if (webhookEventRepository.existsByStripeEventId(stripeEventId)) {
            log.info("SECURITY: Duplicate Stripe webhook event ignored: {}", stripeEventId);
            return;
        }

        WebhookEvent webhookEvent = new WebhookEvent();
        webhookEvent.setStripeEventId(stripeEventId);
        webhookEvent.setEventType(event.getType());
        webhookEvent.setPayload(payload);
        
        try {
            webhookEvent = webhookEventRepository.save(webhookEvent);
        } catch (DataIntegrityViolationException e) {
            log.info("SECURITY: Duplicate Stripe webhook event detected during save: {}", stripeEventId);
            return;
        } catch (Exception e) {
            log.error("SECURITY: Failed to persist Stripe webhook event: {}", stripeEventId, e);
            throw new RuntimeException("Database error", e);
        }

        StripeWebhookService self = selfProvider.getIfAvailable();
        if (self == null) self = this;
        self.processEventAsync(event, webhookEvent.getId());
    }

    @Async
    public void processEventAsync(Event event, UUID internalEventId) {
        if (!stripeEnabled) {
            log.info("Stripe disabled: processEventAsync short-circuited");
            return;
        }

        StripeWebhookService self = selfProvider.getIfAvailable();
        if (self == null) self = this;

        try {
            self.processEventInTransaction(event, internalEventId);
        } catch (Exception e) {
            log.error("SECURITY: Error processing Stripe webhook event: {} ID: {} [InternalID: {}]", 
                    event.getType(), event.getId(), internalEventId, e);
            
            // Non-transactional update to record failure reason since the main transaction rolled back
            try {
                webhookEventRepository.findById(internalEventId).ifPresent(we -> {
                    we.setProcessed(false);
                    we.setErrorMessage(e.getMessage());
                    webhookEventRepository.save(we);
                });
            } catch (Exception fatal) {
                log.error("SECURITY: Fatal error recording webhook failure for {}: {}", internalEventId, fatal.getMessage());
            }
        }
    }

    @Transactional
    public void processEventInTransaction(Event event, UUID internalEventId) {
        WebhookEvent we = webhookEventRepository.findByIdWithLock(internalEventId)
                .orElseThrow(() -> new ResourceNotFoundException("WebhookEvent not found: " + internalEventId));

        if (we.isProcessed()) {
            log.info("SECURITY: WebhookEvent already processed (locked): {} ({})", internalEventId, event.getId());
            return;
        }

        String eventType = event.getType();
        log.info("SECURITY: Processing Stripe webhook event (Atomic): {} (InternalID: {})", eventType, internalEventId);
        meterRegistry.counter("stripe_webhook_events", "type", eventType).increment();

        EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();
        StripeObject stripeObject = dataObjectDeserializer.getObject().orElse(null);
        if (stripeObject == null) {
            log.warn("WEBHOOK_DEBUG: getObject() returned empty for event type={}, attempting deserializeUnsafe()", eventType);
            try {
                stripeObject = dataObjectDeserializer.deserializeUnsafe();
                log.info("WEBHOOK_DEBUG: deserializeUnsafe() succeeded, class={}", stripeObject != null ? stripeObject.getClass().getName() : "null");
            } catch (Exception deserEx) {
                log.error("WEBHOOK_DEBUG: deserializeUnsafe() also failed for event type={}", eventType, deserEx);
            }
        } else {
            log.info("WEBHOOK_DEBUG: getObject() succeeded, class={}", stripeObject.getClass().getName());
        }

        StripeWebhookService self = selfProvider.getIfAvailable();
        if (self == null) self = this;

        if ("checkout.session.completed".equalsIgnoreCase(eventType)) {
            if (stripeObject instanceof Session session) {
                log.info("SECURITY: Payment event: checkout.session.completed for customer: {}", session.getCustomerEmail());
                
                // Re-fetch session from Stripe API if metadata is missing (webhook payloads may omit it)
                if (session.getMetadata() == null || session.getMetadata().isEmpty()) {
                    log.warn("WEBHOOK_DEBUG: Session metadata is null/empty, re-fetching from Stripe API for session={}", session.getId());
                    try {
                        session = stripeClient.checkout().sessions().retrieve(session.getId());
                        log.info("WEBHOOK_DEBUG: Re-fetched session={}, metadata={}", session.getId(), session.getMetadata());
                    } catch (Exception refetchEx) {
                        log.error("WEBHOOK_DEBUG: Failed to re-fetch session={} from Stripe API", session.getId(), refetchEx);
                    }
                } else {
                    log.info("WEBHOOK_DEBUG: Session metadata present: {}", session.getMetadata());
                }
                
                String type = session.getMetadata() != null ? session.getMetadata().get("type") : null;
                log.info("WEBHOOK_DEBUG: metadata type={}, routing to: {}", type,
                    "token_purchase".equalsIgnoreCase(type) ? "handleTokenCheckoutSession" :
                    "TIP".equalsIgnoreCase(type) ? "handleTipCheckoutSession" : "handleCheckoutSession(GENERIC)");
                if ("token_purchase".equalsIgnoreCase(type)) {
                    self.handleTokenCheckoutSession(session);
                } else if ("TIP".equalsIgnoreCase(type)) {
                    self.handleTipCheckoutSession(session);
                } else {
                    self.handleCheckoutSession(session);
                }
                meterRegistry.counter("payments_total", "currency", session.getCurrency()).increment();
                meterRegistry.counter("subscriptions_active").increment();
            }
        } else if ("payment_intent.succeeded".equalsIgnoreCase(eventType)) {
            if (stripeObject instanceof PaymentIntent intent) {
                log.info("SECURITY: Payment event: payment_intent.succeeded for ID: {}", intent.getId());
                self.handlePaymentIntentSucceeded(intent);
            }
        } else if ("customer.subscription.deleted".equals(eventType)) {
            if (stripeObject instanceof Subscription stripeSubscription) {
                log.info("SECURITY: Payment event: customer.subscription.deleted for sub: {}", stripeSubscription.getId());
                self.handleSubscriptionDeleted(stripeSubscription);
                meterRegistry.counter("subscriptions_canceled").increment();
            }
        } else if ("customer.subscription.updated".equals(eventType)) {
            if (stripeObject instanceof Subscription stripeSubscription) {
                self.handleSubscriptionUpdated(stripeSubscription);
            }
        } else if ("invoice.payment_succeeded".equals(eventType)) {
            if (stripeObject instanceof Invoice invoice) {
                self.handleInvoicePaymentSucceeded(invoice);
            }
        } else if ("invoice.payment_failed".equals(eventType)) {
            if (stripeObject instanceof Invoice invoice) {
                self.handleInvoicePaymentFailed(invoice);
            }
        } else if ("payment_intent.payment_failed".equals(eventType)) {
            if (stripeObject instanceof PaymentIntent intent) {
                self.handlePaymentIntentFailed(intent);
            }
        } else if ("charge.dispute.created".equals(eventType)) {
            if (stripeObject instanceof Dispute dispute) {
                self.handleChargeDisputeCreated(dispute);
            }
        } else if ("charge.dispute.closed".equals(eventType)) {
            if (stripeObject instanceof Dispute dispute) {
                self.handleChargeDisputeClosed(dispute);
            }
        } else if ("charge.dispute.updated".equals(eventType)) {
            if (stripeObject instanceof Dispute dispute) {
                self.handleChargeDisputeUpdated(dispute);
            }
        } else if ("account.updated".equals(eventType)) {
            if (stripeObject instanceof com.stripe.model.Account account) {
                log.info("STRIPE: Processing account.updated for account: {}", account.getId());
                stripeConnectService.updateAccountStatus(
                        account.getId(),
                        Boolean.TRUE.equals(account.getChargesEnabled()),
                        Boolean.TRUE.equals(account.getPayoutsEnabled()),
                        Boolean.TRUE.equals(account.getDetailsSubmitted())
                );
                creatorStripeAccountService.updateAccountStatus(
                        account.getId(),
                        Boolean.TRUE.equals(account.getDetailsSubmitted())
                );
                creatorStripeService.updateAccountStatus(
                        account.getId(),
                        Boolean.TRUE.equals(account.getDetailsSubmitted()),
                        Boolean.TRUE.equals(account.getPayoutsEnabled())
                );

                // Create or update CreatorPayoutSettings when fully enabled
                boolean chargesOk = Boolean.TRUE.equals(account.getChargesEnabled());
                boolean payoutsOk = Boolean.TRUE.equals(account.getPayoutsEnabled());
                if (chargesOk && payoutsOk) {
                    log.info("PAYOUT_DEBUG: account.updated chargesEnabled && payoutsEnabled for {}", account.getId());
                    Long resolvedUserId = null;

                    // Try LegacyCreatorStripeAccount first
                    Optional<com.joinlivora.backend.payout.LegacyCreatorStripeAccount> legacyOpt =
                            stripeConnectService.getAccountByStripeId(account.getId());
                    if (legacyOpt.isPresent()) {
                        resolvedUserId = legacyOpt.get().getCreatorId();
                        log.info("PAYOUT_DEBUG: resolved userId={} via LegacyCreatorStripeAccount", resolvedUserId);
                    }

                    // Fallback to StripeAccount repository
                    if (resolvedUserId == null) {
                        Optional<com.joinlivora.backend.payout.StripeAccount> saOpt =
                                stripeAccountRepository.findByStripeAccountId(account.getId());
                        if (saOpt.isPresent()) {
                            resolvedUserId = saOpt.get().getUser().getId();
                            log.info("PAYOUT_DEBUG: resolved userId={} via StripeAccount fallback", resolvedUserId);
                        }
                    }

                    if (resolvedUserId != null) {
                        UUID creatorId = new UUID(0L, resolvedUserId);
                        com.joinlivora.backend.payout.CreatorPayoutSettings settings =
                                creatorPayoutSettingsRepository.findByCreatorId(creatorId)
                                        .orElse(com.joinlivora.backend.payout.CreatorPayoutSettings.builder()
                                                .creatorId(creatorId)
                                                .payoutMethod(com.joinlivora.backend.payout.PayoutMethod.STRIPE_SEPA)
                                                .minimumPayoutAmount(java.math.BigDecimal.valueOf(50.00))
                                                .build());
                        settings.setStripeAccountId(account.getId());
                        settings.setEnabled(true);
                        creatorPayoutSettingsRepository.save(settings);
                        log.info("PAYOUT_DEBUG: created/updated CreatorPayoutSettings for creatorId={}, enabled=true", creatorId);
                    } else {
                        log.warn("PAYOUT_DEBUG: could not resolve user for stripe account {}", account.getId());
                    }
                }
            }
        } else if ("transfer.paid".equals(eventType)) {
            if (stripeObject instanceof Transfer transfer) {
                self.handleTransferPaid(transfer);
            }
        } else if ("transfer.failed".equals(eventType)) {
            if (stripeObject instanceof Transfer transfer) {
                self.handleTransferFailed(transfer);
            }
        }

        // Atomically mark as processed in the same transaction
        we.setProcessed(true);
        we.setErrorMessage(null);
        webhookEventRepository.save(we);
        log.info("SECURITY: Successfully processed and marked Stripe event as processed: {} ({})", internalEventId, event.getId());
    }

    @Transactional
    public void handleSubscriptionDeleted(Subscription stripeSubscription) {
        String stripeSubscriptionId = stripeSubscription.getId();
        log.info("SECURITY: Handling subscription deletion for stripeSubscriptionId: {}", stripeSubscriptionId);

        subscriptionRepository.findByStripeSubscriptionId(stripeSubscriptionId).ifPresent(userSub -> {
            userSub.setStatus(SubscriptionStatus.EXPIRED);
            subscriptionRepository.save(userSub);

            User user = userSub.getUser();
            userService.downgradeToUser(user.getEmail());
            subscriptionService.evictSubscriptionCache(user);
            log.info("SECURITY: User {} downgraded after subscription expiration (deleted in Stripe)", user.getEmail());

            analyticsEventPublisher.publishEvent(
                    com.joinlivora.backend.analytics.AnalyticsEventType.SUBSCRIPTION_CANCELED,
                    user,
                    Map.of("stripeSubscriptionId", stripeSubscriptionId, "status", "EXPIRED")
            );
        });
    }

    @Transactional
    public void handleSubscriptionUpdated(Subscription stripeSubscription) {
        String stripeSubscriptionId = stripeSubscription.getId();
        String status = stripeSubscription.getStatus();
        log.info("SECURITY: Handling subscription update for stripeSubscriptionId: {}, new status: {}", stripeSubscriptionId, status);

        subscriptionRepository.findByStripeSubscriptionId(stripeSubscriptionId).ifPresent(userSub -> {
            userSub.setCurrentPeriodStart(Instant.ofEpochSecond(stripeSubscription.getCurrentPeriodStart()));
            userSub.setCurrentPeriodEnd(Instant.ofEpochSecond(stripeSubscription.getCurrentPeriodEnd()));
            userSub.setCancelAtPeriodEnd(Boolean.TRUE.equals(stripeSubscription.getCancelAtPeriodEnd()));
            
            String paymentMethodId = stripeSubscription.getDefaultPaymentMethod();
            if (paymentMethodId != null) {
                try {
                    PaymentMethod pm = stripeClient.paymentMethods().retrieve(paymentMethodId);
                    if (pm.getCard() != null) {
                        userSub.setPaymentMethodBrand(pm.getCard().getBrand());
                        userSub.setLast4(pm.getCard().getLast4());
                    }
                } catch (Exception e) {
                    log.error("SECURITY: Failed to fetch PaymentMethod details from Stripe: {}", paymentMethodId, e);
                }
            }
            
            SubscriptionStatus oldStatus = userSub.getStatus();
            if ("active".equals(status)) {
                userSub.setStatus(SubscriptionStatus.ACTIVE);
            } else if ("trialing".equals(status)) {
                userSub.setStatus(SubscriptionStatus.TRIAL);
            } else if ("past_due".equals(status)) {
                userSub.setStatus(SubscriptionStatus.PAST_DUE);
            } else if ("canceled".equals(status)) {
                userSub.setStatus(SubscriptionStatus.EXPIRED);
                userService.downgradeToUser(userSub.getUser().getEmail());
            } else if ("unpaid".equals(status)) {
                userSub.setStatus(SubscriptionStatus.EXPIRED);
                userService.downgradeToUser(userSub.getUser().getEmail());
            }
            
            if (userSub.getStatus() == SubscriptionStatus.ACTIVE && userSub.isCancelAtPeriodEnd()) {
                userSub.setStatus(SubscriptionStatus.CANCELED);
            }

            subscriptionRepository.save(userSub);
            subscriptionService.evictSubscriptionCache(userSub.getUser());

            if (oldStatus != userSub.getStatus()) {
                analyticsEventPublisher.publishEvent(
                        com.joinlivora.backend.analytics.AnalyticsEventType.SUBSCRIPTION_STARTED,
                        userSub.getUser(),
                        Map.of("status", userSub.getStatus().name(), "stripeSubscriptionId", stripeSubscriptionId)
                );
            }
        });
    }

    @Transactional
    public void handleInvoicePaymentSucceeded(Invoice invoice) {
        String customerEmail = invoice.getCustomerEmail();
        String stripeSubscriptionId = invoice.getSubscription();
        String stripeInvoiceId = invoice.getId();
        log.info("SECURITY: Invoice payment succeeded for customer: {}, subscription: {}, invoice: {}", customerEmail, stripeSubscriptionId, stripeInvoiceId);

        if (stripeInvoiceId != null && paymentRepository.existsByStripeInvoiceId(stripeInvoiceId)) {
            log.info("SECURITY: Payment already recorded for invoice: {}. Skipping duplicate.", stripeInvoiceId);
            return;
        }

        if (customerEmail != null) {
            User user = userService.getByEmail(customerEmail);
            Payment payment = new Payment();
            payment.setUser(user);
            payment.setAmount(BigDecimal.valueOf(invoice.getAmountPaid()).divide(BigDecimal.valueOf(100)));
            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setCurrency(invoice.getCurrency());
            payment.setStripePaymentIntentId(invoice.getPaymentIntent());
            payment.setStripeInvoiceId(invoice.getId());
            payment.setReceiptUrl(invoice.getHostedInvoiceUrl());
            
            populatePaymentMetadata(payment, invoice.getMetadata());
            
            String creatorIdStr = null;
            if (invoice.getMetadata() != null) {
                creatorIdStr = invoice.getMetadata().get("creator");
                if (creatorIdStr == null) creatorIdStr = invoice.getMetadata().get("creator_id");
            }
            if (creatorIdStr != null) {
                User creator = userService.getById(Long.parseLong(creatorIdStr));
                payment.setCreator(creator);
            }
            
            paymentRepository.save(payment);
            paymentService.notifyPaymentCompleted(payment);

            invoiceService.createInvoice(
                    user,
                    payment.getAmount(),
                    payment.getCurrency(),
                    getCountryFromInvoice(invoice),
                    InvoiceType.SUBSCRIPTION,
                    invoice.getId(),
                    getBillingNameFromInvoice(invoice),
                    getBillingAddressFromInvoice(invoice)
            );
            
            if (payment.getCreator() != null) {
                creatorEarningsService.recordSubscriptionEarning(payment, payment.getCreator());
            }

            subscriptionService.evictSubscriptionCache(user);

            analyticsEventPublisher.publishEvent(
                    com.joinlivora.backend.analytics.AnalyticsEventType.PAYMENT_SUCCEEDED,
                    user,
                    Map.of(
                            "amount", payment.getAmount(),
                            "currency", payment.getCurrency(),
                            "stripePaymentIntentId", payment.getStripePaymentIntentId() != null ? payment.getStripePaymentIntentId() : "N/A"
                    )
            );
        }
    }

    @Transactional
    public void handleInvoicePaymentFailed(Invoice invoice) {
        String customerEmail = invoice.getCustomerEmail();
        log.warn("SECURITY: Invoice payment failed for customer: {}", customerEmail);
        
        if (customerEmail != null) {
            User user = userService.getByEmail(customerEmail);
            Payment payment = new Payment();
            payment.setUser(user);
            payment.setSuccess(false);
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Invoice payment failed");
            payment.setAmount(BigDecimal.valueOf(invoice.getAmountDue()).divide(BigDecimal.valueOf(100)));
            payment.setCurrency(invoice.getCurrency());
            payment.setStripePaymentIntentId(invoice.getPaymentIntent());
            payment.setStripeInvoiceId(invoice.getId());
            
            populatePaymentMetadata(payment, invoice.getMetadata());
            
            String creatorIdStr = null;
            if (invoice.getMetadata() != null) {
                creatorIdStr = invoice.getMetadata().get("creator");
                if (creatorIdStr == null) creatorIdStr = invoice.getMetadata().get("creator_id");
            }
            if (creatorIdStr != null) {
                User creator = userService.getById(Long.parseLong(creatorIdStr));
                payment.setCreator(creator);
            }
            
            paymentRepository.save(payment);

            analyticsEventPublisher.publishEvent(
                    com.joinlivora.backend.analytics.AnalyticsEventType.PAYMENT_FAILED,
                    user,
                    Map.of(
                            "amount", payment.getAmount(),
                            "currency", payment.getCurrency(),
                            "type", payment.getFailureReason()
                    )
            );

            // Evaluate fraud risk (Log RuleFraudSignal when suspicious activity is detected)
            fraudDetectionService.evaluate(user, payment.getIpAddress(), payment.getCountry());

            // Re-evaluate AML risk for creator after failed payment
            amlRulesEngine.evaluateRules(user, BigDecimal.ZERO);
        }
    }

    @Transactional
    public void handleChargeDisputeCreated(Dispute dispute) {
        String paymentIntentId = dispute.getPaymentIntent();
        log.info("SECURITY: Received charge.dispute.created for PaymentIntent: {}", paymentIntentId);
        
        if (paymentIntentId == null || paymentIntentId.isEmpty()) {
            log.warn("SECURITY: Received dispute {} without payment_intent ID", dispute.getId());
            return;
        }

        Optional<Payment> paymentOpt = paymentRepository.findByStripePaymentIntentId(paymentIntentId);
        if (paymentOpt.isPresent()) {
            Payment payment = paymentOpt.get();
            User user = payment.getUser();
            log.info("SECURITY: Redirecting to canonical ChargebackService (CREATED) for creator: {}", user.getEmail());
            canonicalChargebackService.handleDisputeCreated(user, paymentIntentId, dispute);
        } else {
            log.warn("SECURITY: Could not find Payment record for disputed PaymentIntent: {}", paymentIntentId);
        }
    }

    @Transactional
    public void handleChargeDisputeClosed(Dispute dispute) {
        String paymentIntentId = dispute.getPaymentIntent();
        log.info("SECURITY: Received charge.dispute.closed for PaymentIntent: {}, Dispute: {}, Status: {}", 
                paymentIntentId, dispute.getId(), dispute.getStatus());
        
        if (paymentIntentId == null || paymentIntentId.isEmpty()) {
            log.warn("SECURITY: Received closed dispute {} without payment_intent ID", dispute.getId());
            return;
        }

        canonicalChargebackService.handleDisputeClosed(dispute.getId(), dispute);
    }

    @Transactional
    public void handleChargeDisputeUpdated(Dispute dispute) {
        String paymentIntentId = dispute.getPaymentIntent();
        log.info("SECURITY: Received charge.dispute.updated for PaymentIntent: {}, Dispute: {}, Status: {}", 
                paymentIntentId, dispute.getId(), dispute.getStatus());
        
        if (paymentIntentId == null || paymentIntentId.isEmpty()) {
            return;
        }

        // For updates, we just treat them like a creation (upsert logic in ChargebackService)
        Optional<Payment> paymentOpt = paymentRepository.findByStripePaymentIntentId(paymentIntentId);
        if (paymentOpt.isPresent()) {
            canonicalChargebackService.handleDisputeCreated(paymentOpt.get().getUser(), paymentIntentId, dispute);
        }
    }

    @Transactional
    public void handleCheckoutSession(Session session) {
        String customerEmail = session.getCustomerEmail();
        if (customerEmail == null) {
            log.error("SECURITY: No customer email in checkout session");
            return;
        }

        User user = userService.getByEmail(customerEmail);
        log.info("SECURITY: Upgrading creator {} to PREMIUM after successful Stripe payment", customerEmail);

        userService.upgradeToPremium(customerEmail);

        UserSubscription subscription = new UserSubscription();
        subscription.setUser(user);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setStripeSubscriptionId(session.getSubscription());
        subscription.setCurrentPeriodStart(Instant.now());
        subscription.setCurrentPeriodEnd(Instant.now().plus(java.time.Duration.ofDays(30))); 
        
        subscriptionRepository.save(subscription);

        Payment payment = new Payment();
        payment.setUser(user);
        if (session.getAmountTotal() != null) {
            payment.setAmount(BigDecimal.valueOf(session.getAmountTotal()).divide(BigDecimal.valueOf(100)));
        }
        payment.setCurrency(session.getCurrency());
        payment.setStripePaymentIntentId(session.getPaymentIntent());
        
        populatePaymentMetadata(payment, session.getMetadata());
        populatePaymentMethodDetailsFromSession(payment, session);
        
        paymentRepository.save(payment);
        paymentService.notifyPaymentCompleted(payment);
        
        String creatorIdStr = null;
        if (session.getMetadata() != null) {
            creatorIdStr = session.getMetadata().get("creator");
            if (creatorIdStr == null) creatorIdStr = session.getMetadata().get("creator_id");
        }
        
        if (creatorIdStr != null) {
            User creator = userService.getById(Long.parseLong(creatorIdStr));
            payment.setCreator(creator);
            paymentRepository.save(payment);
            creatorEarningsService.recordSubscriptionEarning(payment, creator);
        }
        
        subscriptionService.evictSubscriptionCache(user);
        log.info("SECURITY: UserSubscription and Payment persisted for creator: {}", customerEmail);

        analyticsEventPublisher.publishEvent(
                com.joinlivora.backend.analytics.AnalyticsEventType.SUBSCRIPTION_STARTED,
                user,
                Map.of("stripeSubscriptionId", Optional.ofNullable(session.getSubscription()).orElse("N/A"))
        );

        auditService.logEvent(
                new UUID(0L, user.getId()),
                "SUBSCRIPTION_PURCHASED",
                "USER",
                new UUID(0L, user.getId()),
                Map.of("amount", payment.getAmount(), "currency", payment.getCurrency(), "stripeSubscriptionId", Optional.ofNullable(session.getSubscription()).orElse("")),
                null,
                null
        );
    }

    @Transactional
    public void handleTipCheckoutSession(Session session) {
        String creatorIdStr = null;
        String userIdStr = null;
        if (session.getMetadata() != null) {
            creatorIdStr = session.getMetadata().get("creator");
            if (creatorIdStr == null) creatorIdStr = session.getMetadata().get("creator_id");
            userIdStr = session.getMetadata().get("userId");
            if (userIdStr == null) userIdStr = session.getMetadata().get("user_id");
        }

        if (creatorIdStr == null || userIdStr == null) {
            log.error("SECURITY: Missing creator or userId in tip checkout session");
            return;
        }

        User user = userService.getById(Long.parseLong(userIdStr));
        User creator = userService.getById(Long.parseLong(creatorIdStr));

        Payment payment = new Payment();
        payment.setUser(user);
        payment.setCreator(creator);
        if (session.getAmountTotal() != null) {
            payment.setAmount(BigDecimal.valueOf(session.getAmountTotal()).divide(BigDecimal.valueOf(100)));
        }
        payment.setCurrency(session.getCurrency());
        payment.setStripePaymentIntentId(session.getPaymentIntent());
        payment.setStripeSessionId(session.getId());
        payment.setSuccess(true);
        payment.setStatus(PaymentStatus.COMPLETED);
        
        populatePaymentMetadata(payment, session.getMetadata());
        populatePaymentMethodDetailsFromSession(payment, session);
        
        paymentRepository.save(payment);
        paymentService.notifyPaymentCompleted(payment);

        DirectTip tip = DirectTip.builder()
                .user(user)
                .creator(creator)
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .stripeSessionId(session.getId())
                .status(TipStatus.PENDING)
                .build();
        
        tip = directTipService.saveTip(tip);
        directTipService.completeTip(tip.getId());

        invoiceService.createInvoice(
                user,
                payment.getAmount(),
                payment.getCurrency(),
                getCountryFromSession(session),
                InvoiceType.TIPS,
                session.getInvoice(),
                getBillingNameFromSession(session),
                getBillingAddressFromSession(session)
        );

        log.info("SECURITY: Tip payment handled for session: {}", session.getId());
    }

    @Transactional
    public void handlePaymentIntentSucceeded(PaymentIntent intent) {
        String type = Optional.ofNullable(intent.getMetadata()).map(m -> m.get("type")).orElse(null);
        
        if (paymentRepository.existsByStripePaymentIntentId(intent.getId())) {
            log.info("SECURITY: Payment already recorded for PaymentIntent: {}. Ensuring services are confirmed.", intent.getId());
            // We still call these confirm methods because they are idempotent and we want to be sure.
            if ("tip".equalsIgnoreCase(type)) {
                tipService.confirmTip(intent.getId());
            } else if ("ppv".equalsIgnoreCase(type)) {
                ppvPurchaseService.confirmPurchase(intent.getId());
            } else if ("highlighted_message".equalsIgnoreCase(type)) {
                highlightedMessageService.confirmHighlight(intent.getId());
            }
            return;
        }

        if ("tip".equalsIgnoreCase(type)) {
            tipService.confirmTip(intent.getId());
            
            String creatorIdStr = intent.getMetadata().get("creator");
            if (creatorIdStr == null) creatorIdStr = intent.getMetadata().get("creator_id");
            String fromUserIdStr = intent.getMetadata().get("userId");
            if (fromUserIdStr == null) fromUserIdStr = intent.getMetadata().get("from_user_id");
            
            if (creatorIdStr != null && fromUserIdStr != null) {
                User creator = userService.getById(Long.parseLong(creatorIdStr));
                User fromUser = userService.getById(Long.parseLong(fromUserIdStr));
                
                Payment payment = new Payment();
                payment.setUser(fromUser);
                payment.setCreator(creator);
                payment.setAmount(BigDecimal.valueOf(intent.getAmount()).divide(BigDecimal.valueOf(100)));
                payment.setCurrency(intent.getCurrency());
                payment.setStripePaymentIntentId(intent.getId());
                
                populatePaymentMetadata(payment, intent.getMetadata());
                populatePaymentMethodDetails(payment, intent.getPaymentMethod());
                
                paymentRepository.save(payment);
                paymentService.notifyPaymentCompleted(payment);
                
                invoiceService.createInvoice(
                    fromUser,
                    payment.getAmount(),
                        payment.getCurrency(),
                        getCountryFromPaymentIntent(intent),
                        InvoiceType.TIPS,
                        intent.getInvoice(),
                        getBillingNameFromPaymentIntent(intent),
                        getBillingAddressFromPaymentIntent(intent)
                );
                
                creatorEarningsService.recordTipEarning(payment, creator);
                // TODO(livora-payments): If we adopt separate charges & transfers instead of destination charges,
                // schedule a Stripe Transfer here to move the creator's net share from the platform account
                // to the connected account (creator.getStripeAccountId()). This project currently records all
                // creator earnings in the internal platform balance and defers real payouts to a later phase.

                auditService.logEvent(
                        new UUID(0L, fromUser.getId()),
                        AuditService.TIP_CREATED, // Reuse TIP_CREATED or add TIP_PAID
                        "TIP",
                        null, // Tip ID is not easily available here unless returned by confirmTip
                        Map.of("amount", payment.getAmount(), "currency", payment.getCurrency(), "creator", creator.getId(), "status", "COMPLETED"),
                        null,
                        null
                );
            }
        } else if ("ppv".equalsIgnoreCase(type)) {
            ppvPurchaseService.confirmPurchase(intent.getId());
            
            String creatorIdStr = intent.getMetadata().get("creator");
            if (creatorIdStr == null) creatorIdStr = intent.getMetadata().get("creator_id");
            String userIdStr = intent.getMetadata().get("userId");
            if (userIdStr == null) userIdStr = intent.getMetadata().get("user_id");
            
            if (creatorIdStr != null && userIdStr != null) {
                User creator = userService.getById(Long.parseLong(creatorIdStr));
                User user = userService.getById(Long.parseLong(userIdStr));
                
                Payment payment = new Payment();
                payment.setUser(user);
                payment.setCreator(creator);
                payment.setAmount(BigDecimal.valueOf(intent.getAmount()).divide(BigDecimal.valueOf(100)));
                payment.setCurrency(intent.getCurrency());
                payment.setStripePaymentIntentId(intent.getId());
                
                populatePaymentMetadata(payment, intent.getMetadata());
                populatePaymentMethodDetails(payment, intent.getPaymentMethod());
                
                paymentRepository.save(payment);
                paymentService.notifyPaymentCompleted(payment);
                
                invoiceService.createInvoice(
                    user,
                    payment.getAmount(),
                    payment.getCurrency(),
                    getCountryFromPaymentIntent(intent),
                    InvoiceType.PPV,
                        intent.getInvoice(),
                        getBillingNameFromPaymentIntent(intent),
                        getBillingAddressFromPaymentIntent(intent)
                );
                
                creatorEarningsService.recordPPVEarning(payment, creator);
                // TODO(livora-payments): If we adopt separate charges & transfers instead of destination charges,
                // schedule a Stripe Transfer here to move the creator's net share from the platform account
                // to the connected account (creator.getStripeAccountId()). This project currently records all
                // creator earnings in the internal platform balance and defers real payouts to a later phase.

                auditService.logEvent(
                        new UUID(0L, user.getId()),
                        "PPV_PURCHASED",
                        "CONTENT",
                        null,
                        Map.of("amount", payment.getAmount(), "currency", payment.getCurrency(), "creator", creator.getId()),
                        null,
                        null
                );
            }
        } else if ("highlighted_message".equalsIgnoreCase(type)) {
            highlightedMessageService.confirmHighlight(intent.getId());
            
            String roomIdStr = intent.getMetadata().get("room_id");
            String userIdStr = intent.getMetadata().get("user_id");
            
            if (roomIdStr != null && userIdStr != null) {
                com.joinlivora.backend.streaming.StreamRoom room = streamService.getRoom(UUID.fromString(roomIdStr));
                User creator = room.getCreator();
                User user = userService.getById(Long.parseLong(userIdStr));
                
                Payment payment = new Payment();
                payment.setUser(user);
                payment.setCreator(creator);
                payment.setAmount(BigDecimal.valueOf(intent.getAmount()).divide(BigDecimal.valueOf(100)));
                payment.setCurrency(intent.getCurrency());
                payment.setStripePaymentIntentId(intent.getId());
                
                populatePaymentMetadata(payment, intent.getMetadata());
                populatePaymentMethodDetails(payment, intent.getPaymentMethod());
                
                paymentRepository.save(payment);
                paymentService.notifyPaymentCompleted(payment);
                
                invoiceService.createInvoice(
                    user,
                    payment.getAmount(),
                    payment.getCurrency(),
                    getCountryFromPaymentIntent(intent),
                    InvoiceType.TIPS,
                    intent.getInvoice(),
                        getBillingNameFromPaymentIntent(intent),
                        getBillingAddressFromPaymentIntent(intent)
                );
                
                creatorEarningsService.recordHighlightedChatEarning(payment, creator);
                // TODO(livora-payments): If we adopt separate charges & transfers instead of destination charges,
                // schedule a Stripe Transfer here to move the creator's net share from the platform account
                // to the connected account (creator.getStripeAccountId()). This project currently records all
                // creator earnings in the internal platform balance and defers real payouts to a later phase.

                auditService.logEvent(
                        new UUID(0L, user.getId()),
                        "HIGHLIGHT_PURCHASED",
                        "STREAM",
                        UUID.fromString(roomIdStr),
                        Map.of("amount", payment.getAmount(), "currency", payment.getCurrency()),
                        null,
                        null
                );
            }
        }
    }

    @Transactional
    public void handleTokenCheckoutSession(Session session) {
        log.info("WEBHOOK_DEBUG: handleTokenCheckoutSession ENTERED for session={}", session.getId());
        if (paymentRepository.existsByStripeSessionId(session.getId())) {
            log.info("SECURITY: Token purchase already processed for session: {}", session.getId());
            return;
        }

        String customerEmail = session.getCustomerEmail();
        String clientReferenceId = session.getClientReferenceId();
        String packageIdStr = null;
        if (session.getMetadata() != null) {
            packageIdStr = session.getMetadata().get("packageId");
            if (packageIdStr == null) packageIdStr = session.getMetadata().get("package_id");
        }

        if (packageIdStr == null) {
            log.error("SECURITY: Missing packageId in token checkout session metadata");
            return;
        }

        User user = null;
        if (clientReferenceId != null) {
            try {
                user = userService.getById(Long.parseLong(clientReferenceId));
            } catch (Exception e) {
                log.warn("SECURITY: Could not resolve user by clientReferenceId: {}", clientReferenceId);
            }
        }

        if (user == null && customerEmail != null) {
            user = userService.getByEmail(customerEmail);
        }

        if (user == null) {
            log.error("SECURITY: Could not resolve user for token checkout session. email={}, clientRef={}", 
                    customerEmail, clientReferenceId);
            return;
        }
        
        UUID packageId = UUID.fromString(packageIdStr);
        
        com.joinlivora.backend.token.TokenPackage tokenPackage = tokenPackageRepository.findById(packageId)
                .orElseThrow(() -> new RuntimeException("Token package not found"));

        Payment payment = new Payment();
        payment.setUser(user);
        if (session.getAmountTotal() != null) {
            payment.setAmount(BigDecimal.valueOf(session.getAmountTotal()).divide(BigDecimal.valueOf(100)));
        }
        payment.setCurrency(session.getCurrency());
        payment.setStripePaymentIntentId(session.getPaymentIntent());
        payment.setStripeSessionId(session.getId());
        
        populatePaymentMetadata(payment, session.getMetadata());
        populatePaymentMethodDetailsFromSession(payment, session);

        String creatorIdStr = null;
        String type = null;
        if (session.getMetadata() != null) {
            creatorIdStr = session.getMetadata().get("creator");
            if (creatorIdStr == null) creatorIdStr = session.getMetadata().get("creator_id");
            type = session.getMetadata().get("type");
        }
        
        if (creatorIdStr != null) {
            User creator = userService.getById(Long.parseLong(creatorIdStr));
            payment.setCreator(creator);
        }
        
        paymentRepository.save(payment);
        paymentService.notifyPaymentCompleted(payment);

        log.info("SECURITY: Crediting {} tokens to user {} after successful payment", tokenPackage.getTokenAmount(), user.getEmail());
        tokenService.creditTokens(user, tokenPackage.getTokenAmount(), "Stripe Session: " + session.getId());
        
        invoiceService.createInvoice(
                user,
                payment.getAmount(),
                payment.getCurrency(),
                getCountryFromSession(session),
                InvoiceType.TOKENS,
                session.getInvoice(),
                getBillingNameFromSession(session),
                getBillingAddressFromSession(session)
        );

        if (payment.getCreator() != null) {
            if ("tip".equalsIgnoreCase(type)) {
                creatorEarningsService.recordTipEarning(payment, payment.getCreator());
            } else if ("ppv".equalsIgnoreCase(type)) {
                creatorEarningsService.recordPPVEarning(payment, payment.getCreator());
            }
        }
        
        auditService.logEvent(
                new UUID(0L, user.getId()),
                "TOKENS_PURCHASED",
                "USER",
                new UUID(0L, user.getId()),
                Map.of("amount", payment.getAmount(), "currency", payment.getCurrency(), "tokenAmount", tokenPackage.getTokenAmount()),
                null,
                null
        );
        
        log.info("SECURITY: Token purchase persisted for user: {}", user.getEmail());
    }

    @Transactional
    public void handleTransferPaid(Transfer transfer) {
        String transferId = transfer.getId();
        log.info("SECURITY: Handling successful transfer: {}", transferId);
        
        String payoutIdStr = transfer.getMetadata() != null ? transfer.getMetadata().get("payoutId") : null;
        if (payoutIdStr != null) {
            UUID payoutId = UUID.fromString(payoutIdStr);
            log.info("PAYOUT_DEBUG: handleTransferPaid found payoutId={} in metadata", payoutId);
            
            creatorPayoutRepository.findById(payoutId).ifPresent(payout -> {
                PayoutStatus oldStatus = payout.getStatus();
                payout.setStatus(PayoutStatus.COMPLETED);
                payout.setStripeTransferId(transferId);
                creatorPayoutRepository.save(payout);
                payoutAuditService.logStatusChange(payoutId, oldStatus, PayoutStatus.COMPLETED, PayoutActorType.STRIPE, null, "Stripe transfer paid: " + transferId);
                log.info("SECURITY: CreatorPayout {} marked as COMPLETED", payoutId);
            });

            payoutRepository.findById(payoutId).ifPresent(payout -> {
                payout.setStatus(com.joinlivora.backend.payout.PayoutStatus.COMPLETED);
                payout.setStripeTransferId(transferId);
                payoutRepository.save(payout);
                log.info("SECURITY: Payout {} marked as COMPLETED", payoutId);
            });
        }

        // Also handle admin-approved payouts which use payoutRequestId metadata key
        String payoutRequestIdStr = transfer.getMetadata() != null ? transfer.getMetadata().get("payoutRequestId") : null;
        if (payoutRequestIdStr != null) {
            UUID payoutRequestId = UUID.fromString(payoutRequestIdStr);
            log.info("PAYOUT_DEBUG: handleTransferPaid found payoutRequestId={} in metadata", payoutRequestId);

            payoutRequestRepository.findById(payoutRequestId).ifPresent(request -> {
                com.joinlivora.backend.payout.PayoutRequestStatus oldStatus = request.getStatus();
                request.setStatus(com.joinlivora.backend.payout.PayoutRequestStatus.COMPLETED);
                request.setStripeTransferId(transferId);
                request.setUpdatedAt(java.time.Instant.now());
                payoutRequestRepository.save(request);
                log.info("SECURITY: PayoutRequest {} marked as COMPLETED (was {})", payoutRequestId, oldStatus);
            });
        }
    }

    @Transactional
    public void handleTransferFailed(Transfer transfer) {
        String transferId = transfer.getId();
        log.error("SECURITY: Handling failed transfer: {}", transferId);

        String payoutIdStr = transfer.getMetadata() != null ? transfer.getMetadata().get("payoutId") : null;
        if (payoutIdStr != null) {
            UUID payoutId = UUID.fromString(payoutIdStr);
            log.info("PAYOUT_DEBUG: handleTransferFailed found payoutId={} in metadata", payoutId);
            
            creatorPayoutRepository.findById(payoutId).ifPresent(payout -> {
                PayoutStatus oldStatus = payout.getStatus();
                payout.setStatus(PayoutStatus.FAILED);
                payout.setStripeTransferId(transferId);
                creatorPayoutRepository.save(payout);
                payoutAuditService.logStatusChange(payoutId, oldStatus, PayoutStatus.FAILED, PayoutActorType.STRIPE, null, "Stripe transfer failed: " + transferId);
                log.info("SECURITY: CreatorPayout {} marked as FAILED due to Stripe transfer failure", payoutId);
                log.info("PAYOUT_DEBUG: CreatorPayout {} FAILED — balance auto-restored because calculateAvailablePayout only subtracts COMPLETED and PENDING", payoutId);
            });

            payoutRepository.findById(payoutId).ifPresent(payout -> {
                payout.setStatus(com.joinlivora.backend.payout.PayoutStatus.FAILED);
                payout.setErrorMessage("Transfer failed according to Stripe webhook");
                payoutRepository.save(payout);
                
                User user = payout.getUser();
                com.joinlivora.backend.token.CreatorEarnings earnings = tokenService.getCreatorEarnings(user);
                earnings.setAvailableTokens(earnings.getAvailableTokens() + payout.getTokenAmount());
                tokenService.updateCreatorEarnings(earnings);
                
                log.info("SECURITY: Restoring {} tokens to creator {} due to failed legacy payout {}",
                        payout.getTokenAmount(), user.getEmail(), payoutId);
            });
        }

        // Also handle admin-approved payouts which use payoutRequestId metadata key
        String payoutRequestIdStr = transfer.getMetadata() != null ? transfer.getMetadata().get("payoutRequestId") : null;
        if (payoutRequestIdStr != null) {
            UUID payoutRequestId = UUID.fromString(payoutRequestIdStr);
            log.info("PAYOUT_DEBUG: handleTransferFailed found payoutRequestId={} in metadata", payoutRequestId);

            payoutRequestRepository.findById(payoutRequestId).ifPresent(request -> {
                com.joinlivora.backend.payout.PayoutRequestStatus oldStatus = request.getStatus();
                request.setStatus(com.joinlivora.backend.payout.PayoutRequestStatus.FAILED);
                request.setStripeTransferId(transferId);
                request.setRejectionReason("Stripe transfer failed: " + transferId);
                request.setUpdatedAt(java.time.Instant.now());
                payoutRequestRepository.save(request);
                log.info("SECURITY: PayoutRequest {} marked as FAILED (was {})", payoutRequestId, oldStatus);

                // Unlock earnings that were locked for this failed payout request
                List<com.joinlivora.backend.payout.CreatorEarning> lockedEarnings = creatorEarningRepository.findAllByPayoutRequest(request);
                for (com.joinlivora.backend.payout.CreatorEarning earning : lockedEarnings) {
                    earning.setLocked(false);
                    earning.setPayoutRequest(null);
                }
                creatorEarningRepository.saveAll(lockedEarnings);
                log.info("PAYOUT_DEBUG: handleTransferFailed unlocked {} earnings for failed PayoutRequest {}", lockedEarnings.size(), payoutRequestId);
            });
        }
    }

    private String getCountryFromInvoice(Invoice invoice) {
        if (invoice.getCustomerAddress() != null && invoice.getCustomerAddress().getCountry() != null) {
            return invoice.getCustomerAddress().getCountry();
        }
        return "XX";
    }

    private String getCountryFromSession(Session session) {
        if (session.getCustomerDetails() != null && session.getCustomerDetails().getAddress() != null) {
            return session.getCustomerDetails().getAddress().getCountry();
        }
        return "XX";
    }

    private String getCountryFromPaymentIntent(PaymentIntent intent) {
        if (intent.getShipping() != null && intent.getShipping().getAddress() != null) {
            return intent.getShipping().getAddress().getCountry();
        }
        return "XX";
    }

    private String getBillingNameFromInvoice(Invoice invoice) {
        return invoice.getCustomerName();
    }

    private String getBillingAddressFromInvoice(Invoice invoice) {
        if (invoice.getCustomerAddress() != null) {
            return formatAddress(invoice.getCustomerAddress());
        }
        return null;
    }

    private String getBillingNameFromSession(Session session) {
        if (session.getCustomerDetails() != null) {
            return session.getCustomerDetails().getName();
        }
        return null;
    }

    private String getBillingAddressFromSession(Session session) {
        if (session.getCustomerDetails() != null && session.getCustomerDetails().getAddress() != null) {
            return formatAddress(session.getCustomerDetails().getAddress());
        }
        return null;
    }

    private String getBillingNameFromPaymentIntent(PaymentIntent intent) {
        if (intent.getShipping() != null) {
            return intent.getShipping().getName();
        }
        return null;
    }

    private String getBillingAddressFromPaymentIntent(PaymentIntent intent) {
        if (intent.getShipping() != null && intent.getShipping().getAddress() != null) {
            return formatAddress(intent.getShipping().getAddress());
        }
        return null;
    }

    private String formatAddress(com.stripe.model.Address address) {
        List<String> parts = new ArrayList<>();
        if (address.getLine1() != null) parts.add(address.getLine1());
        if (address.getLine2() != null) parts.add(address.getLine2());
        if (address.getPostalCode() != null) parts.add(address.getPostalCode());
        if (address.getCity() != null) parts.add(address.getCity());
        if (address.getState() != null) parts.add(address.getState());
        if (address.getCountry() != null) parts.add(address.getCountry());
        return String.join(", ", parts);
    }

    @Transactional
    public void handlePaymentIntentFailed(PaymentIntent intent) {
        log.warn("SECURITY: Payment intent failed for ID: {}", intent.getId());
        
        String userIdStr = intent.getMetadata() != null ? intent.getMetadata().get("userId") : null;
        if (userIdStr == null && intent.getMetadata() != null) userIdStr = intent.getMetadata().get("user_id");
        if (userIdStr == null && intent.getMetadata() != null) userIdStr = intent.getMetadata().get("from_user_id");
        
        if (userIdStr != null) {
            User user = userService.getById(Long.parseLong(userIdStr));
            Payment payment = new Payment();
            payment.setUser(user);
            payment.setSuccess(false);
            payment.setFailureReason(intent.getLastPaymentError() != null ? intent.getLastPaymentError().getMessage() : "Unknown failure");
            payment.setAmount(BigDecimal.valueOf(intent.getAmount()).divide(BigDecimal.valueOf(100)));
            payment.setCurrency(intent.getCurrency());
            payment.setStripePaymentIntentId(intent.getId());
            
            populatePaymentMetadata(payment, intent.getMetadata());
            
            String creatorIdStr = null;
            if (intent.getMetadata() != null) {
                creatorIdStr = intent.getMetadata().get("creator");
                if (creatorIdStr == null) creatorIdStr = intent.getMetadata().get("creator_id");
            }
            if (creatorIdStr != null) {
                User creator = userService.getById(Long.parseLong(creatorIdStr));
                payment.setCreator(creator);
            }
            
            paymentRepository.save(payment);
            
            analyticsEventPublisher.publishEvent(
                    com.joinlivora.backend.analytics.AnalyticsEventType.PAYMENT_FAILED,
                    user,
                    Map.of(
                            "amount", payment.getAmount(),
                            "currency", payment.getCurrency(),
                            "stripePaymentIntentId", payment.getStripePaymentIntentId(),
                            "type", payment.getFailureReason()
                    )
            );
            
            fraudDetectionService.evaluate(user, payment.getIpAddress(), payment.getCountry());

            // Re-evaluate AML risk for creator after failed payment
            amlRulesEngine.evaluateRules(user, BigDecimal.ZERO);
        }
    }

    private void populatePaymentMetadata(Payment payment, Map<String, String> metadata) {
        if (metadata != null) {
            payment.setIpAddress(metadata.get("ip_address"));
            payment.setCountry(metadata.get("country"));
            payment.setUserAgent(metadata.get("user_agent"));
            payment.setDeviceFingerprint(metadata.get("device_fingerprint"));
            
            String riskLevelStr = metadata.get("fraud_risk_level");
            if (riskLevelStr != null) {
                try {
                    payment.setRiskLevel(com.joinlivora.backend.fraud.model.RiskLevel.valueOf(riskLevelStr));
                } catch (IllegalArgumentException e) {
                    log.warn("SECURITY: Invalid fraud_risk_level in metadata: {}", riskLevelStr);
                }
            }
        }
    }

    private void populatePaymentMethodDetails(Payment payment, String paymentMethodId) {
        if (!stripeEnabled) {
            return;
        }
        if (paymentMethodId != null) {
            try {
                PaymentMethod pm = stripeClient.paymentMethods().retrieve(paymentMethodId);
                if (pm.getCard() != null) {
                    payment.setPaymentMethodFingerprint(pm.getCard().getFingerprint());
                    payment.setPaymentMethodBrand(pm.getCard().getBrand());
                    payment.setPaymentMethodLast4(pm.getCard().getLast4());
                }
            } catch (Exception e) {
                log.error("SECURITY: Failed to fetch PaymentMethod details from Stripe: {}", paymentMethodId, e);
            }
        }
    }

    private void populatePaymentMethodDetailsFromSession(Payment payment, Session session) {
        if (!stripeEnabled) {
            return;
        }
        try {
            String pmId = null;
            if (session.getPaymentIntent() != null) {
                PaymentIntent pi = stripeClient.paymentIntents().retrieve(session.getPaymentIntent());
                pmId = pi.getPaymentMethod();
            } else if (session.getSubscription() != null) {
                Subscription sub = stripeClient.subscriptions().retrieve(session.getSubscription());
                pmId = sub.getDefaultPaymentMethod();
            }
            populatePaymentMethodDetails(payment, pmId);
        } catch (Exception e) {
            log.error("SECURITY: Failed to fetch PaymentMethod details from Session: {}", session.getId(), e);
        }
    }
}
