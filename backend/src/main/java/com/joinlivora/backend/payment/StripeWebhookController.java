package com.joinlivora.backend.payment;

import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import com.stripe.StripeClient;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.scheduling.annotation.Async;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

@RestController
@RequestMapping("/api/payments/webhook")
@RequiredArgsConstructor
@Slf4j
public class StripeWebhookController {

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
    private final com.joinlivora.backend.payout.PayoutRepository payoutRepository;
    private final com.joinlivora.backend.payout.StripeAccountRepository stripeAccountRepository;
    private final com.joinlivora.backend.payout.MonetizationService monetizationService;
    private final com.joinlivora.backend.monetization.TipService tipService;
    private final com.joinlivora.backend.monetization.PpvService ppvService;

    @Value("${stripe.webhook-secret}")
    private String endpointSecret;

    @PostMapping("/stripe")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String sigHeader
    ) {
        if (sigHeader == null || sigHeader.isEmpty()) {
            log.warn("SECURITY: Received webhook without Stripe-Signature header");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing signature");
        }
        
        if (endpointSecret == null || endpointSecret.trim().isEmpty() || "whsec_dev_fallback".equals(endpointSecret)) {
            log.warn("SECURITY: Stripe webhook secret is using fallback or is unconfigured. Signature verification will be skipped for development.");
        }

        Event event;
        try {
            if ("whsec_dev_fallback".equals(endpointSecret)) {
                // In development with fallback secret, we skip strict signature verification
                // but we still need to parse the event.
                event = com.stripe.net.ApiResource.GSON.fromJson(payload, Event.class);
            } else {
                event = Webhook.constructEvent(payload, sigHeader, endpointSecret);
            }
        } catch (SignatureVerificationException e) {
            log.error("SECURITY: Invalid Stripe signature: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
        } catch (Exception e) {
            log.error("SECURITY: Error constructing Stripe event", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Error processing event");
        }

        // 1. Idempotency handling: check if event has already been processed
        String stripeEventId = event.getId();
        if (webhookEventRepository.existsByStripeEventId(stripeEventId)) {
            log.info("SECURITY: Duplicate Stripe webhook event ignored: {}", stripeEventId);
            return ResponseEntity.ok("Already processed");
        }

        // 2. Persist Webhook Event
        WebhookEvent webhookEvent = new WebhookEvent();
        webhookEvent.setStripeEventId(stripeEventId);
        webhookEvent.setEventType(event.getType());
        webhookEvent.setPayload(payload);
        try {
            webhookEventRepository.save(webhookEvent);
        } catch (Exception e) {
            log.error("SECURITY: Failed to persist Stripe webhook event: {}", stripeEventId, e);
            // We continue processing if save fails? Actually better to return error if we want retries
            // But if it's already in DB, existsByStripeEventId should have caught it.
            // If it's a DB error, returning 500 will make Stripe retry.
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Database error");
        }

        // Handle the event asynchronously
        processEventAsync(event, webhookEvent.getId());

        return ResponseEntity.ok("Received");
    }

    @Async
    public void processEventAsync(Event event, java.util.UUID internalEventId) {
        String eventType = event.getType();
        String stripeEventId = event.getId();
        log.info("SECURITY: Processing Stripe webhook event (Async): {} (InternalID: {})", eventType, internalEventId);
        meterRegistry.counter("stripe_webhook_events", "type", eventType).increment();

        try {
            if ("checkout.session.completed".equals(eventType)) {
                EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();
                StripeObject stripeObject = dataObjectDeserializer.getObject().orElse(null);

                if (stripeObject instanceof Session session) {
                    log.info("SECURITY: Payment event: checkout.session.completed for user: {}", session.getCustomerEmail());
                    
                    String type = session.getMetadata() != null ? session.getMetadata().get("type") : null;
                    if ("token_purchase".equals(type)) {
                        handleTokenCheckoutSession(session);
                    } else if ("tip".equals(type)) {
                        // Handle tipped checkout sessions if any, or just metadata check
                        handleCheckoutSession(session);
                    } else {
                        handleCheckoutSession(session);
                    }
                    
                    meterRegistry.counter("payments_total", "currency", session.getCurrency()).increment();
                    meterRegistry.counter("subscriptions_active").increment();
                }
            } else if ("payment_intent.succeeded".equals(eventType)) {
                EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();
                StripeObject stripeObject = dataObjectDeserializer.getObject().orElse(null);
                
                if (stripeObject instanceof com.stripe.model.PaymentIntent intent) {
                    log.info("SECURITY: Payment event: payment_intent.succeeded for ID: {}", intent.getId());
                    handlePaymentIntentSucceeded(intent);
                }
            } else if ("customer.subscription.deleted".equals(eventType)) {
                EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();
                StripeObject stripeObject = dataObjectDeserializer.getObject().orElse(null);

                if (stripeObject instanceof com.stripe.model.Subscription stripeSubscription) {
                    log.info("SECURITY: Payment event: customer.subscription.deleted for sub: {}", stripeSubscription.getId());
                    handleSubscriptionDeleted(stripeSubscription);
                    meterRegistry.counter("subscriptions_canceled").increment();
                }
            } else if ("customer.subscription.updated".equals(eventType)) {
                EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();
                StripeObject stripeObject = dataObjectDeserializer.getObject().orElse(null);

                if (stripeObject instanceof com.stripe.model.Subscription stripeSubscription) {
                    handleSubscriptionUpdated(stripeSubscription);
                }
            } else if ("invoice.payment_succeeded".equals(eventType)) {
                EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();
                StripeObject stripeObject = dataObjectDeserializer.getObject().orElse(null);

                if (stripeObject instanceof com.stripe.model.Invoice invoice) {
                    handleInvoicePaymentSucceeded(invoice);
                }
            } else if ("invoice.payment_failed".equals(eventType)) {
                EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();
                StripeObject stripeObject = dataObjectDeserializer.getObject().orElse(null);

                if (stripeObject instanceof com.stripe.model.Invoice invoice) {
                    handleInvoicePaymentFailed(invoice);
                }
            } else if ("account.updated".equals(eventType)) {
                EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();
                StripeObject stripeObject = dataObjectDeserializer.getObject().orElse(null);

                if (stripeObject instanceof com.stripe.model.Account account) {
                    creatorConnectService.updateAccountStatus(
                            account.getId(),
                            Boolean.TRUE.equals(account.getChargesEnabled()),
                            Boolean.TRUE.equals(account.getPayoutsEnabled())
                    );
                }
            } else if ("transfer.paid".equals(eventType)) {
                EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();
                StripeObject stripeObject = dataObjectDeserializer.getObject().orElse(null);

                if (stripeObject instanceof com.stripe.model.Transfer transfer) {
                    handleTransferPaid(transfer);
                }
            } else if ("transfer.failed".equals(eventType)) {
                EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();
                StripeObject stripeObject = dataObjectDeserializer.getObject().orElse(null);

                if (stripeObject instanceof com.stripe.model.Transfer transfer) {
                    handleTransferFailed(transfer);
                }
            }
            
            // Mark as processed successfully
            webhookEventRepository.findById(internalEventId).ifPresent(we -> {
                we.setProcessed(true);
                webhookEventRepository.save(we);
            });
            
        } catch (Exception e) {
            log.error("SECURITY: Error processing Stripe webhook event: {} ID: {} [InternalID: {}]", eventType, stripeEventId, internalEventId, e);
            webhookEventRepository.findById(internalEventId).ifPresent(we -> {
                we.setProcessed(false);
                we.setErrorMessage(e.getMessage());
                webhookEventRepository.save(we);
            });
        }
    }

    @Transactional
    protected void handleSubscriptionDeleted(com.stripe.model.Subscription stripeSubscription) {
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
                    java.util.Map.of("stripeSubscriptionId", stripeSubscriptionId, "status", "EXPIRED")
            );
        });
    }

    @Transactional
    protected void handleSubscriptionUpdated(com.stripe.model.Subscription stripeSubscription) {
        String stripeSubscriptionId = stripeSubscription.getId();
        String status = stripeSubscription.getStatus();
        log.info("SECURITY: Handling subscription update for stripeSubscriptionId: {}, new status: {}", stripeSubscriptionId, status);

        subscriptionRepository.findByStripeSubscriptionId(stripeSubscriptionId).ifPresent(userSub -> {
            userSub.setCurrentPeriodStart(Instant.ofEpochSecond(stripeSubscription.getCurrentPeriodStart()));
            userSub.setCurrentPeriodEnd(Instant.ofEpochSecond(stripeSubscription.getCurrentPeriodEnd()));
            userSub.setCancelAtPeriodEnd(Boolean.TRUE.equals(stripeSubscription.getCancelAtPeriodEnd()));
            if (stripeSubscription.getNextPendingInvoiceItemInvoice() != null) {
                 // Simplified, usually you'd get next invoice date from invoice objects or trial_end
            }
            
            // Try to get payment method info
            String paymentMethodId = stripeSubscription.getDefaultPaymentMethod();
            if (paymentMethodId != null) {
                try {
                    com.stripe.model.PaymentMethod pm = stripeClient.paymentMethods().retrieve(paymentMethodId);
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
                userSub.setStatus(SubscriptionStatus.EXPIRED); // Stripe 'canceled' means it's over
                userService.downgradeToUser(userSub.getUser().getEmail());
            } else if ("unpaid".equals(status)) {
                userSub.setStatus(SubscriptionStatus.EXPIRED);
                userService.downgradeToUser(userSub.getUser().getEmail());
            }
            
            // If it's active but cancelAtPeriodEnd is true, our internal status should be CANCELED (meaning "will cancel")
            if (userSub.getStatus() == SubscriptionStatus.ACTIVE && userSub.isCancelAtPeriodEnd()) {
                userSub.setStatus(SubscriptionStatus.CANCELED);
            }

            subscriptionRepository.save(userSub);
            subscriptionService.evictSubscriptionCache(userSub.getUser());

            // Real-time notification if status changed
            if (oldStatus != userSub.getStatus()) {
                analyticsEventPublisher.publishEvent(
                        com.joinlivora.backend.analytics.AnalyticsEventType.SUBSCRIPTION_STARTED, // Or updated
                        userSub.getUser(),
                        java.util.Map.of("status", userSub.getStatus().name(), "stripeSubscriptionId", stripeSubscriptionId)
                );
            }
        });
    }

    @Async
    @Transactional
    public void handleInvoicePaymentSucceeded(com.stripe.model.Invoice invoice) {
        String customerEmail = invoice.getCustomerEmail();
        String stripeSubscriptionId = invoice.getSubscription();
        log.info("SECURITY: Invoice payment succeeded for customer: {}, subscription: {}", customerEmail, stripeSubscriptionId);

        if (customerEmail != null) {
            User user = userService.getByEmail(customerEmail);
            Payment payment = new Payment();
            payment.setUser(user);
            payment.setAmount(BigDecimal.valueOf(invoice.getAmountPaid()).divide(BigDecimal.valueOf(100)));
            payment.setCurrency(invoice.getCurrency());
            payment.setStripePaymentIntentId(invoice.getPaymentIntent());
            payment.setStripeInvoiceId(invoice.getId());
            payment.setReceiptUrl(invoice.getHostedInvoiceUrl());
            
            // Record creator earning if associated
            String creatorIdStr = invoice.getMetadata() != null ? invoice.getMetadata().get("creator_id") : null;
            if (creatorIdStr != null) {
                User creator = userService.getById(Long.parseLong(creatorIdStr));
                payment.setCreator(creator);
            }
            
            paymentRepository.save(payment);
            
            if (payment.getCreator() != null) {
                monetizationService.recordSubscriptionEarning(payment, payment.getCreator());
            }

            subscriptionService.evictSubscriptionCache(user);

            analyticsEventPublisher.publishEvent(
                    com.joinlivora.backend.analytics.AnalyticsEventType.PAYMENT_SUCCEEDED,
                    user,
                    java.util.Map.of(
                            "amount", payment.getAmount(),
                            "currency", payment.getCurrency(),
                            "stripePaymentIntentId", payment.getStripePaymentIntentId() != null ? payment.getStripePaymentIntentId() : "N/A"
                    )
            );
        }
    }

    @Async
    @Transactional
    public void handleInvoicePaymentFailed(com.stripe.model.Invoice invoice) {
        String customerEmail = invoice.getCustomerEmail();
        log.warn("SECURITY: Invoice payment failed for customer: {}", customerEmail);
        
        if (customerEmail != null) {
            User user = userService.getByEmail(customerEmail);
            analyticsEventPublisher.publishEvent(
                    com.joinlivora.backend.analytics.AnalyticsEventType.PAYMENT_FAILED,
                    user,
                    java.util.Map.of(
                            "amount", BigDecimal.valueOf(invoice.getAmountDue()).divide(BigDecimal.valueOf(100)),
                            "currency", invoice.getCurrency()
                    )
            );
        }
    }

    @Async
    @Transactional
    public void handleCheckoutSession(Session session) {
        String customerEmail = session.getCustomerEmail();
        if (customerEmail == null) {
            log.error("SECURITY: No customer email in checkout session");
            return;
        }

        User user = userService.getByEmail(customerEmail);
        log.info("SECURITY: Upgrading user {} to PREMIUM after successful Stripe payment", customerEmail);

        // 1. Upgrade User Role
        userService.upgradeToPremium(customerEmail);

        // 2. Persist UserSubscription
        UserSubscription subscription = new UserSubscription();
        subscription.setUser(user);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setStripeSubscriptionId(session.getSubscription());
        
        // Try to get period dates from Stripe (this is a simplified version)
        subscription.setCurrentPeriodStart(Instant.now());
        subscription.setCurrentPeriodEnd(Instant.now().plus(java.time.Duration.ofDays(30))); // Default to 30 days
        
        subscriptionRepository.save(subscription);

        // 3. Persist Payment
        Payment payment = new Payment();
        payment.setUser(user);
        if (session.getAmountTotal() != null) {
            payment.setAmount(BigDecimal.valueOf(session.getAmountTotal()).divide(BigDecimal.valueOf(100)));
        }
        payment.setCurrency(session.getCurrency());
        payment.setStripePaymentIntentId(session.getPaymentIntent());
        paymentRepository.save(payment);
        
        // Record creator earning if associated
        String creatorIdStr = Optional.ofNullable(session.getMetadata()).map(m -> m.get("creator_id")).orElse(null);
        if (creatorIdStr != null) {
            User creator = userService.getById(Long.parseLong(creatorIdStr));
            payment.setCreator(creator);
            paymentRepository.save(payment);
            monetizationService.recordSubscriptionEarning(payment, creator);
        }
        
        subscriptionService.evictSubscriptionCache(user);
        log.info("SECURITY: UserSubscription and Payment persisted for user: {}", customerEmail);

        analyticsEventPublisher.publishEvent(
                com.joinlivora.backend.analytics.AnalyticsEventType.SUBSCRIPTION_STARTED,
                user,
                java.util.Map.of("stripeSubscriptionId", Optional.ofNullable(session.getSubscription()).orElse("N/A"))
        );
    }

    @Async
    @Transactional
    public void handlePaymentIntentSucceeded(com.stripe.model.PaymentIntent intent) {
        String type = Optional.ofNullable(intent.getMetadata()).map(m -> m.get("type")).orElse(null);
        
        if ("tip".equals(type)) {
            tipService.confirmTip(intent.getId());
            
            // Record Earning
            String creatorIdStr = intent.getMetadata().get("creator_id");
            String fromUserIdStr = intent.getMetadata().get("from_user_id");
            
            if (creatorIdStr != null && fromUserIdStr != null) {
                User creator = userService.getById(Long.parseLong(creatorIdStr));
                User fromUser = userService.getById(Long.parseLong(fromUserIdStr));
                
                Payment payment = new Payment();
                payment.setUser(fromUser);
                payment.setCreator(creator);
                payment.setAmount(BigDecimal.valueOf(intent.getAmount()).divide(BigDecimal.valueOf(100)));
                payment.setCurrency(intent.getCurrency());
                payment.setStripePaymentIntentId(intent.getId());
                paymentRepository.save(payment);
                
                monetizationService.recordTipEarning(payment, creator);
            }
        } else if ("ppv".equals(type)) {
            ppvService.confirmPurchase(intent.getId());
            
            // Record Earning
            String creatorIdStr = intent.getMetadata().get("creator_id");
            String userIdStr = intent.getMetadata().get("user_id");
            
            if (creatorIdStr != null && userIdStr != null) {
                User creator = userService.getById(Long.parseLong(creatorIdStr));
                User user = userService.getById(Long.parseLong(userIdStr));
                
                Payment payment = new Payment();
                payment.setUser(user);
                payment.setCreator(creator);
                payment.setAmount(BigDecimal.valueOf(intent.getAmount()).divide(BigDecimal.valueOf(100)));
                payment.setCurrency(intent.getCurrency());
                payment.setStripePaymentIntentId(intent.getId());
                paymentRepository.save(payment);
                
                monetizationService.recordPPVEarning(payment, creator);
            }
        }
    }

    @Async
    @Transactional
    public void handleTokenCheckoutSession(Session session) {
        String customerEmail = session.getCustomerEmail();
        String packageIdStr = session.getMetadata() != null ? session.getMetadata().get("package_id") : null;

        if (customerEmail == null || packageIdStr == null) {
            log.error("SECURITY: Missing email or packageId in token checkout session");
            return;
        }

        User user = userService.getByEmail(customerEmail);
        java.util.UUID packageId = java.util.UUID.fromString(packageIdStr);
        
        com.joinlivora.backend.token.TokenPackage tokenPackage = tokenPackageRepository.findById(packageId)
                .orElseThrow(() -> new RuntimeException("Token package not found"));

        log.info("SECURITY: Crediting {} tokens to user {} after successful payment", tokenPackage.getTokenAmount(), customerEmail);

        // 1. Credit tokens
        tokenService.creditTokens(user, tokenPackage.getTokenAmount(), "Stripe Session: " + session.getId());

        // 2. Persist Payment record
        Payment payment = new Payment();
        payment.setUser(user);
        if (session.getAmountTotal() != null) {
            payment.setAmount(BigDecimal.valueOf(session.getAmountTotal()).divide(BigDecimal.valueOf(100)));
        }
        payment.setCurrency(session.getCurrency());
        payment.setStripePaymentIntentId(session.getPaymentIntent());
        paymentRepository.save(payment);

        // Record creator earning if associated (Tip or PPV)
        String creatorIdStr = session.getMetadata() != null ? session.getMetadata().get("creator_id") : null;
        String type = session.getMetadata() != null ? session.getMetadata().get("type") : null;
        if (creatorIdStr != null) {
            User creator = userService.getById(Long.parseLong(creatorIdStr));
            payment.setCreator(creator);
            paymentRepository.save(payment);
            
            if ("tip".equals(type)) {
                monetizationService.recordTipEarning(payment, creator);
            } else if ("ppv".equals(type)) {
                monetizationService.recordPPVEarning(payment, creator);
            }
        }
        
        log.info("SECURITY: Token purchase persisted for user: {}", customerEmail);
    }

    @Transactional
    protected void handleTransferPaid(com.stripe.model.Transfer transfer) {
        String transferId = transfer.getId();
        log.info("SECURITY: Handling successful transfer: {}", transferId);
        
        // Find payout by stripeTransferId (stored in metadata usually, but let's check our Payout entity)
        // We might want to use metadata during transfer creation to store payoutId
        String payoutIdStr = transfer.getMetadata() != null ? transfer.getMetadata().get("payoutId") : null;
        if (payoutIdStr != null) {
            java.util.UUID payoutId = java.util.UUID.fromString(payoutIdStr);
            payoutRepository.findById(payoutId).ifPresent(payout -> {
                payout.setStatus(com.joinlivora.backend.payout.PayoutStatus.PAID);
                payout.setStripeTransferId(transferId);
                payoutRepository.save(payout);
                log.info("SECURITY: Payout {} marked as PAID", payoutId);
            });
        }
    }

    @Transactional
    protected void handleTransferFailed(com.stripe.model.Transfer transfer) {
        String transferId = transfer.getId();
        log.error("SECURITY: Handling failed transfer: {}", transferId);

        String payoutIdStr = transfer.getMetadata() != null ? transfer.getMetadata().get("payoutId") : null;
        if (payoutIdStr != null) {
            java.util.UUID payoutId = java.util.UUID.fromString(payoutIdStr);
            payoutRepository.findById(payoutId).ifPresent(payout -> {
                payout.setStatus(com.joinlivora.backend.payout.PayoutStatus.FAILED);
                payout.setErrorMessage("Transfer failed according to Stripe webhook");
                payoutRepository.save(payout);
                
                // RESTORE EARNINGS
                User user = payout.getUser();
                com.joinlivora.backend.token.CreatorEarnings earnings = tokenService.getCreatorEarnings(user);
                earnings.setAvailableTokens(earnings.getAvailableTokens() + payout.getTokenAmount());
                tokenService.updateCreatorEarnings(earnings);
                
                log.info("SECURITY: Restoring {} tokens to creator {} due to failed payout {}", 
                        payout.getTokenAmount(), user.getEmail(), payoutId);
            });
        }
    }
}
