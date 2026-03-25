package com.joinlivora.backend.payments.webhook;

import com.joinlivora.backend.fraud.service.FraudEvaluationService;
import com.joinlivora.backend.payment.Payment;
import com.joinlivora.backend.payment.PaymentRepository;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.monetization.TipOrchestrationService;
import com.joinlivora.backend.payout.CreatorEarningsService;
import com.joinlivora.backend.user.UserService;
import com.joinlivora.backend.audit.service.AuditService;
import com.joinlivora.backend.payments.service.WebhookReplayProtectionService;
import com.stripe.model.Dispute;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service("paymentsStripeWebhookService")
@RequiredArgsConstructor
@Slf4j
public class StripeWebhookService {

    private final ApplicationEventPublisher eventPublisher;
    private final PaymentRepository paymentRepository;
    private final com.joinlivora.backend.user.UserRepository userRepository;
    private final FraudEvaluationService fraudEvaluationService;
    private final WebhookReplayProtectionService replayProtectionService;
    private final com.joinlivora.backend.chargeback.ChargebackService chargebackService;
    private final TipOrchestrationService tipService;
    private final CreatorEarningsService creatorEarningsService;
    private final UserService userService;
    private final AuditService auditService;

    public void processEvent(Event event) {
        String eventType = event.getType();
        log.info("WEBHOOK: Processing Stripe event: {}", eventType);

        EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();
        StripeObject stripeObject = dataObjectDeserializer.getObject().orElse(null);

        if (stripeObject == null) {
            log.warn("WEBHOOK: Could not deserialize event data for: {}", eventType);
            return;
        }

        switch (eventType) {
            case "charge.dispute.created":
                if (stripeObject instanceof Dispute dispute) {
                    handleChargebackOpened(dispute, event.getId());
                }
                break;
            case "charge.dispute.closed":
                if (stripeObject instanceof Dispute dispute) {
                    handleChargebackClosed(dispute, event.getId());
                }
                break;
            case "payment_intent.succeeded":
                if (stripeObject instanceof PaymentIntent intent) {
                    handlePaymentIntentSucceeded(intent, event.getId());
                }
                break;
            default:
                log.debug("WEBHOOK: Ignoring unhandled event type: {}", eventType);
                break;
        }
    }

    public void handleChargebackOpened(Dispute dispute, String stripeEventId) {
        String paymentIntentId = dispute.getPaymentIntent();
        log.info("WEBHOOK: Dispute created for PaymentIntent: {}", paymentIntentId);
        
        Optional<Payment> paymentOpt = paymentIntentId != null ? paymentRepository.findByStripePaymentIntentId(paymentIntentId) : Optional.empty();
        
        if (paymentOpt.isPresent()) {
            chargebackService.handleDisputeCreated(paymentOpt.get().getUser(), paymentIntentId, dispute);
        } else {
            log.warn("WEBHOOK: Payment record not found for PaymentIntent: {}. Cannot handle dispute.", paymentIntentId);
        }

        // Maintain existing evaluation flow
        UUID userId = extractUserId(dispute, paymentOpt);
        if (userId != null) {
            String ipAddress = paymentOpt.map(Payment::getIpAddress).orElse(null);
            fraudEvaluationService.evaluateUser(userId, stripeEventId, dispute.getReason(), true, ipAddress);
        }
    }

    private UUID extractUserId(Dispute dispute, Optional<Payment> paymentOpt) {
        // 1. Try from Payment record
        if (paymentOpt.isPresent()) {
            return new UUID(0L, paymentOpt.get().getUser().getId());
        }
        
        // 2. Try from metadata
        if (dispute.getMetadata() != null) {
            String userIdStr = dispute.getMetadata().get("creator");
            if (userIdStr != null) {
                try {
                    return UUID.fromString(userIdStr);
                } catch (IllegalArgumentException e) {
                    // Ignore
                }
            }
        }
        
        return null;
    }

    public void handleChargebackClosed(Dispute dispute, String stripeEventId) {
        String paymentIntentId = dispute.getPaymentIntent();
        log.info("WEBHOOK: Dispute closed for PaymentIntent: {}, Status: {}", paymentIntentId, dispute.getStatus());
        
        Optional<Payment> paymentOpt = paymentIntentId != null ? paymentRepository.findByStripePaymentIntentId(paymentIntentId) : Optional.empty();

        chargebackService.handleDisputeClosed(dispute.getId(), dispute);
        
        // Maintain existing evaluation flow
        UUID userId = extractUserId(dispute, paymentOpt);
        if (userId != null) {
            String ipAddress = paymentOpt.map(Payment::getIpAddress).orElse(null);
            fraudEvaluationService.evaluateUser(userId, stripeEventId, null, false, ipAddress);
        }
    }

    private void publishChargebackEvent(Dispute dispute, String paymentIntentId, Optional<Payment> paymentOpt) {
        if (paymentIntentId == null || paymentIntentId.isEmpty()) {
            log.warn("WEBHOOK: Received dispute without PaymentIntent ID: {}", dispute.getId());
            return;
        }

        if (paymentOpt.isPresent()) {
            Payment payment = paymentOpt.get();
            User user = payment.getUser();
            log.info("WEBHOOK: Publishing ChargebackEvent for creator: {}", user.getEmail());
            eventPublisher.publishEvent(new ChargebackEvent(this, user, paymentIntentId, dispute));
        } else {
            log.warn("WEBHOOK: Payment record not found for PaymentIntent: {}", paymentIntentId);
        }
    }

    @Transactional
    public void handlePaymentIntentSucceeded(PaymentIntent intent, String stripeEventId) {
        log.info("WEBHOOK: Payment intent succeeded: {}", intent.getId());

        String type = intent.getMetadata() != null ? intent.getMetadata().get("type") : null;
        
        if (paymentRepository.existsByStripePaymentIntentId(intent.getId())) {
            log.info("WEBHOOK: Payment already recorded for PaymentIntent: {}. Ensuring services are confirmed.", intent.getId());
            if ("tip".equals(type) || "test_tip".equals(type)) {
                tipService.confirmTip(intent.getId());
            }
            return;
        }

        if ("tip".equals(type) || "test_tip".equals(type)) {
            tipService.confirmTip(intent.getId());
            
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
                payment.setSuccess(true);
                
                if (intent.getMetadata() != null) {
                    payment.setIpAddress(intent.getMetadata().get("ip_address"));
                    payment.setCountry(intent.getMetadata().get("country"));
                    payment.setUserAgent(intent.getMetadata().get("user_agent"));
                    String riskLevelStr = intent.getMetadata().get("fraud_risk_level");
                    if (riskLevelStr != null) {
                        try {
                            payment.setRiskLevel(com.joinlivora.backend.fraud.model.RiskLevel.valueOf(riskLevelStr));
                        } catch (Exception e) {
                            // ignore
                        }
                    }
                }
                
                paymentRepository.save(payment);
                creatorEarningsService.recordTipEarning(payment, creator);

                auditService.logEvent(
                        new UUID(0L, fromUser.getId()),
                        AuditService.TIP_CREATED,
                        "TIP",
                        null,
                        Map.of("amount", payment.getAmount(), "currency", payment.getCurrency(), "creator", creator.getId(), "status", "COMPLETED"),
                        payment.getIpAddress(),
                        payment.getUserAgent()
                );
            }
        }

        UUID userId = null;
        String ipAddress = null;
        
        // Try to get data from Payment record first
        Optional<Payment> paymentOpt = paymentRepository.findByStripePaymentIntentId(intent.getId());
        if (paymentOpt.isPresent()) {
            Payment payment = paymentOpt.get();
            userId = new UUID(0L, payment.getUser().getId());
            ipAddress = payment.getIpAddress();
        }

        // Fallback to metadata for creator if not found in DB
        if (userId == null && intent.getMetadata() != null) {
            String userIdStr = intent.getMetadata().get("creator");
            if (userIdStr == null) userIdStr = intent.getMetadata().get("creator_id");
            if (userIdStr != null) {
                try {
                    userId = UUID.fromString(userIdStr);
                } catch (IllegalArgumentException e) {
                    try {
                        // Try parsing as Long if it's not a UUID
                        userId = new UUID(0L, Long.parseLong(userIdStr));
                    } catch (NumberFormatException nfe) {
                        log.warn("WEBHOOK: Invalid creator in PaymentIntent metadata: {}", userIdStr);
                    }
                }
            }
        }

        if (userId == null) {
            log.warn("WEBHOOK: Could not determine creator for PaymentIntent: {}", intent.getId());
            return;
        }

        fraudEvaluationService.processSuccessfulPayment(
                userId,
                intent.getId(),
                intent.getAmount(),
                intent.getCurrency(),
                stripeEventId,
                ipAddress
        );
    }
}
