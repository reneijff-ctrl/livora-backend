package com.joinlivora.backend.payment;

import com.joinlivora.backend.admin.service.AdminRealtimeEventService;
import com.joinlivora.backend.exception.PaymentLockedException;
import com.joinlivora.backend.exception.TrustChallengeException;
import com.joinlivora.backend.fraud.dto.RiskDecisionResult;
import com.joinlivora.backend.fraud.model.FraudDecisionLevel;
import com.joinlivora.backend.fraud.model.FraudSignalType;
import com.joinlivora.backend.fraud.model.FraudSource;
import com.joinlivora.backend.fraud.model.RiskDecision;
import com.joinlivora.backend.fraud.model.VelocityActionType;
import com.joinlivora.backend.fraud.repository.UserRiskStateRepository;
import com.joinlivora.backend.fraud.service.FraudDetectionService;
import com.joinlivora.backend.fraud.service.TrustEvaluationService;
import com.joinlivora.backend.fraud.service.VelocityTrackerService;
import com.joinlivora.backend.user.User;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service("paymentService")
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final StripeClient stripeClient;
    private final UserRiskStateRepository userRiskStateRepository;
    private final VelocityTrackerService velocityTrackerService;
    private final TrustEvaluationService trustEvaluationService;
    private final FraudDetectionService fraudDetectionService;
    private final AutoFreezePolicyService autoFreezePolicyService;
    private final com.joinlivora.backend.fraud.service.FraudRiskScoreService fraudRiskScoreService;
    private final com.joinlivora.backend.fraud.service.FraudRiskService fraudRiskService;
    private final AdminRealtimeEventService adminRealtimeEventService;

    @Value("${stripe.premium-plan-id}")
    private String premiumPlanId;

    @Value("${stripe.success-url}")
    private String successUrl;

    @Value("${stripe.cancel-url}")
    private String cancelUrl;

    @Value("${stripe.token-success-url}")
    private String tokenSuccessUrl;

    @Value("${stripe.token-cancel-url}")
    private String tokenCancelUrl;

    @jakarta.annotation.PostConstruct
    public void validateConfig() {
        if (successUrl == null || !successUrl.startsWith("http")) {
            throw new IllegalStateException("Stripe success URL must be an absolute URL starting with http/https");
        }
        if (cancelUrl == null || !cancelUrl.startsWith("http")) {
            throw new IllegalStateException("Stripe cancel URL must be an absolute URL starting with http/https");
        }
        if (tokenSuccessUrl == null || !tokenSuccessUrl.startsWith("http")) {
            throw new IllegalStateException("Stripe token success URL must be an absolute URL starting with http/https");
        }
        if (tokenCancelUrl == null || !tokenCancelUrl.startsWith("http")) {
            throw new IllegalStateException("Stripe token cancel URL must be an absolute URL starting with http/https");
        }
    }

    public String createCheckoutSession(User user, String planId, String ipAddress, String country, String userAgent, String fingerprintHash) throws StripeException {
        // Use provided planId or default to premiumPlanId
        String priceId = (planId != null && !planId.isEmpty() && !planId.equals("free")) ? planId : premiumPlanId;
        
        // We use a default amount for premium subscription if unknown here (e.g. 10.00 EUR)
        com.joinlivora.backend.fraud.model.RiskLevel riskLevel = checkPaymentLock(user, new java.math.BigDecimal("10.00"), ipAddress, country, userAgent, fingerprintHash);
        evaluateTrust(user, ipAddress, fingerprintHash);
        velocityTrackerService.trackAction(user.getId(), VelocityActionType.PAYMENT);
        return createCheckoutSession(user, priceId, SessionCreateParams.Mode.SUBSCRIPTION, ipAddress, country, userAgent, fingerprintHash, riskLevel);
    }

    public String createTokenCheckoutSession(User user, String stripePriceId, UUID packageId, String ipAddress, String country, String userAgent, String fingerprintHash) throws StripeException {
        // We should ideally fetch the amount from the package, but for now we'll pass null or default
        com.joinlivora.backend.fraud.model.RiskLevel riskLevel = checkPaymentLock(user, null, ipAddress, country, userAgent, fingerprintHash);
        evaluateTrust(user, ipAddress, fingerprintHash);
        velocityTrackerService.trackAction(user.getId(), VelocityActionType.PAYMENT);
        log.info("SECURITY: Creating Stripe token checkout session for creator: {} package: {}", user.getEmail(), packageId);
        
        SessionCreateParams.Builder builder = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setCurrency("eur")
                .setSuccessUrl(tokenSuccessUrl + "?package=" + packageId)
                .setCancelUrl(tokenCancelUrl)
                .setCustomerEmail(user.getEmail())
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setQuantity(1L)
                                .setPrice(stripePriceId)
                                .build()
                )
                .setClientReferenceId(user.getId().toString())
                .putMetadata("package_id", packageId.toString())
                .putMetadata("type", "token_purchase")
                .putMetadata("fraud_risk_level", riskLevel != null ? riskLevel.name() : "LOW");

        SessionCreateParams.PaymentIntentData.Builder piBuilder = SessionCreateParams.PaymentIntentData.builder()
                .putMetadata("package_id", packageId.toString())
                .putMetadata("type", "token_purchase")
                .putMetadata("user_id", user.getId().toString());

        if (ipAddress != null) {
            builder.putMetadata("ip_address", ipAddress);
            piBuilder.putMetadata("ip_address", ipAddress);
        }
        if (fingerprintHash != null) {
            builder.putMetadata("device_fingerprint", fingerprintHash);
            piBuilder.putMetadata("device_fingerprint", fingerprintHash);
        }
        if (country != null) {
            builder.putMetadata("country", country);
            piBuilder.putMetadata("country", country);
        }
        if (userAgent != null) {
            builder.putMetadata("user_agent", userAgent);
            piBuilder.putMetadata("user_agent", userAgent);
        }

        builder.setPaymentIntentData(piBuilder.build());

        long timestampBucket = System.currentTimeMillis() / 10000;
        String idempotencyKey = "checkout_" + user.getId() + "_" + stripePriceId + "_" + timestampBucket;
        RequestOptions options = RequestOptions.builder()
                .setIdempotencyKey(idempotencyKey)
                .build();

        try {
            Session session = stripeClient.checkout().sessions().create(builder.build(), options);
            return session.getUrl();
        } catch (Exception e) {
            log.error("Stripe checkout session failed", e);
            if (e instanceof StripeException se) {
                throw se;
            }
            throw new RuntimeException(e);
        }
    }

    private String createCheckoutSession(User user, String priceId, SessionCreateParams.Mode mode, String ipAddress, String country, String userAgent, String fingerprintHash, com.joinlivora.backend.fraud.model.RiskLevel riskLevel) throws StripeException {
        log.info("SECURITY: Creating Stripe checkout session for creator: {} mode: {}, risk: {}", user.getEmail(), mode, riskLevel);

        SessionCreateParams.Builder builder = SessionCreateParams.builder()
                .setMode(mode)
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl);
        
        if (mode == SessionCreateParams.Mode.PAYMENT) {
            builder.setCurrency("eur");
        }

        builder.setCustomerEmail(user.getEmail())
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setQuantity(1L)
                                .setPrice(priceId)
                                .build()
                )
                .setClientReferenceId(user.getId().toString())
                .putMetadata("fraud_risk_level", riskLevel != null ? riskLevel.name() : "LOW");

        if (mode == SessionCreateParams.Mode.PAYMENT) {
            SessionCreateParams.PaymentIntentData.Builder piBuilder = SessionCreateParams.PaymentIntentData.builder()
                    .putMetadata("user_id", user.getId().toString());
            if (ipAddress != null) {
                builder.putMetadata("ip_address", ipAddress);
                piBuilder.putMetadata("ip_address", ipAddress);
            }
            if (fingerprintHash != null) {
                builder.putMetadata("device_fingerprint", fingerprintHash);
                piBuilder.putMetadata("device_fingerprint", fingerprintHash);
            }
            if (country != null) {
                builder.putMetadata("country", country);
                piBuilder.putMetadata("country", country);
            }
            if (userAgent != null) {
                builder.putMetadata("user_agent", userAgent);
                piBuilder.putMetadata("user_agent", userAgent);
            }
            builder.setPaymentIntentData(piBuilder.build());
        } else if (mode == SessionCreateParams.Mode.SUBSCRIPTION) {
            SessionCreateParams.SubscriptionData.Builder subBuilder = SessionCreateParams.SubscriptionData.builder()
                    .putMetadata("user_id", user.getId().toString());
            if (ipAddress != null) {
                builder.putMetadata("ip_address", ipAddress);
                subBuilder.putMetadata("ip_address", ipAddress);
            }
            if (fingerprintHash != null) {
                builder.putMetadata("device_fingerprint", fingerprintHash);
                subBuilder.putMetadata("device_fingerprint", fingerprintHash);
            }
            if (country != null) {
                builder.putMetadata("country", country);
                subBuilder.putMetadata("country", country);
            }
            if (userAgent != null) {
                builder.putMetadata("user_agent", userAgent);
                subBuilder.putMetadata("user_agent", userAgent);
            }
            builder.setSubscriptionData(subBuilder.build());
        }

        long timestampBucket = System.currentTimeMillis() / 10000;
        String idempotencyKey = "checkout_" + user.getId() + "_" + priceId + "_" + timestampBucket;
        RequestOptions options = RequestOptions.builder()
                .setIdempotencyKey(idempotencyKey)
                .build();

        try {
            Session session = stripeClient.checkout().sessions().create(builder.build(), options);
            return session.getUrl();
        } catch (Exception e) {
            log.error("Stripe checkout session failed", e);
            if (e instanceof StripeException se) {
                throw se;
            }
            throw new RuntimeException(e);
        }
    }

    public com.joinlivora.backend.fraud.model.RiskLevel checkPaymentLock(User user, java.math.BigDecimal amount, String ipAddress, String country, String userAgent, String fingerprintHash) {
        autoFreezePolicyService.validateUserStatus(user);

        com.joinlivora.backend.fraud.model.FraudRiskAssessment assessment = fraudRiskService.calculateRisk(user, amount, ipAddress, country, fingerprintHash);
        com.joinlivora.backend.fraud.model.RiskLevel riskLevel = assessment.getRiskLevel();

        if (riskLevel == com.joinlivora.backend.fraud.model.RiskLevel.HIGH) {
            log.warn("SECURITY: High risk payment attempt by creator: {}. Allowing but marking for hold.", user.getEmail());
        } else if (riskLevel == com.joinlivora.backend.fraud.model.RiskLevel.MEDIUM) {
            log.info("SECURITY: Medium risk payment attempt by creator: {}.", user.getEmail());
        }

        userRiskStateRepository.findById(user.getId()).ifPresent(state -> {
            if (state.isPaymentLocked()) {
                // We still check for explicit locks, but maybe we should allow if it's just HIGH risk?
                // The requirement says "do not block payment" if riskLevel == HIGH.
                // If it's ALREADY locked, it means a previous assessment or admin action blocked it.
                // I'll keep the lock check for now as it's separate from the current assessment.
                throw new PaymentLockedException("Payments are locked for this account due to suspicious activity.");
            }
        });

        return riskLevel;
    }

    private void evaluateTrust(User user, String ipAddress, String fingerprintHash) {
        RiskDecisionResult result = trustEvaluationService.evaluate(user, fingerprintHash, ipAddress);
        if (result.getDecision() == RiskDecision.BLOCK) {
            log.warn("SECURITY [trust_evaluation]: Blocked payment attempt for creator: {} from IP: {} with fingerprint: {}. ExplanationId: {}",
                    user.getEmail(), ipAddress, fingerprintHash, result.getExplanationId());
            fraudDetectionService.logFraudSignal(user.getId(), FraudDecisionLevel.HIGH, FraudSource.PAYMENT, FraudSignalType.TRUST_EVALUATION_BLOCK, "TRUST_EVALUATION_BLOCK");
            throw new org.springframework.security.access.AccessDeniedException("Action blocked due to security risk.");
        } else if (result.getDecision() == RiskDecision.REVIEW) {
            log.info("SECURITY [trust_evaluation]: Trust challenge required for payment for creator: {}. ExplanationId: {}", user.getEmail(), result.getExplanationId());
            throw new TrustChallengeException("Additional verification required to complete this action.");
        }
    }

    public void notifyPaymentCompleted(Payment payment) {
        log.info("PAYMENT: Notifying admin of completed payment: {} {}", payment.getAmount(), payment.getCurrency());
        adminRealtimeEventService.broadcastPaymentCompleted(payment);
    }
}
