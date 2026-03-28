package com.joinlivora.backend.monetization;

import com.joinlivora.backend.exception.ResourceNotFoundException;
import com.joinlivora.backend.fraud.exception.HighFraudRiskException;
import com.joinlivora.backend.fraud.model.FraudRiskResult;
import com.joinlivora.backend.fraud.model.RiskLevel;
import com.joinlivora.backend.monetization.dto.TipResult;
import com.joinlivora.backend.streaming.Stream;
import com.joinlivora.backend.token.TipRecord;
import com.joinlivora.backend.user.User;
import com.stripe.model.PaymentIntent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class TipOrchestrationService {

    private final TipPersistenceService persistenceService;
    private final TipRiskService riskService;
    private final TipNotificationService notificationService;
    private final TipPaymentService paymentService;

    @Transactional
    public String createTipIntent(User fromUser, Long creatorId, BigDecimal amount, String message, String clientRequestId, String ipAddress, String country, String userAgent, String fingerprintHash) throws Exception {
        User creator = persistenceService.findUserById(creatorId)
                .orElseThrow(() -> new ResourceNotFoundException("Creator not found with ID: " + creatorId));

        // 1. Prevent direct self tipping
        if (fromUser.getId().equals(creator.getId())) {
            throw new IllegalStateException("Creators cannot tip their own streams");
        }

        // 2. Detect suspicious patterns
        riskService.checkSuspiciousTippingPatterns(fromUser, creator, amount, ipAddress, fingerprintHash);
        riskService.validateTippingAccess(new UUID(0L, fromUser.getId()), amount);
        
        RiskLevel riskLevel = riskService.checkPaymentLock(fromUser, amount, ipAddress, country, userAgent, fingerprintHash);
        riskService.evaluateTrust(fromUser, ipAddress, fingerprintHash);
        riskService.trackVelocity(fromUser.getId());
        riskService.checkRapidTipping(new UUID(0L, fromUser.getId()), ipAddress);

        // Idempotency check
        if (clientRequestId != null) {
            Optional<Tip> existing = persistenceService.findByClientRequestId(clientRequestId);
            if (existing.isPresent()) {
                log.info("MONETIZATION: Duplicate Tip request {} for creator {}. Returning existing status.", clientRequestId, fromUser.getEmail());
                if (existing.get().getStripePaymentIntentId() != null) {
                    try {
                        PaymentIntent pi = paymentService.retrievePaymentIntent(existing.get().getStripePaymentIntentId());
                        return pi.getClientSecret();
                    } catch (Exception e) {
                        log.error("Failed to retrieve existing PaymentIntent", e);
                    }
                }
                throw new com.joinlivora.backend.exception.DuplicateRequestException("A tip with this request ID already exists.");
            }
        }

        FraudRiskResult risk = riskService.calculateFraudRisk(fromUser, amount);
        RiskLevel maxRiskLevel = riskLevel;
        if (risk.level().ordinal() > (riskLevel != null ? riskLevel.ordinal() : -1)) {
            maxRiskLevel = RiskLevel.valueOf(risk.level().name());
        }

        if (maxRiskLevel == RiskLevel.CRITICAL) {
            log.error("Blocking tip due to CRITICAL fraud risk for creator {}: score={}, reasons={}",
                    fromUser.getEmail(), risk.score(), risk.reasons());
            riskService.recordFraudDecision(new UUID(0L, fromUser.getId()), null, null, risk);
            riskService.recordFraudIncident(new UUID(0L, fromUser.getId()), "CRITICAL_FRAUD_RISK: " + String.join(", ", risk.reasons()), Map.of("score", risk.score(), "amount", amount));
            throw new HighFraudRiskException(risk.score(), risk.reasons());
        }

        if (maxRiskLevel == RiskLevel.HIGH) {
            log.warn("Allowing tip but marking for review due to HIGH fraud risk for creator {}: score={}, reasons={}",
                    fromUser.getEmail(), risk.score(), risk.reasons());
        }

        // Anti-abuse validation
        riskService.validateStripeTip(fromUser, amount);

        PaymentIntent intent = paymentService.createStripePaymentIntent(fromUser, creator, amount, message, clientRequestId, maxRiskLevel, 
                Map.of("ip_address", ipAddress != null ? ipAddress : "", 
                       "country", country != null ? country : "", 
                       "user_agent", userAgent != null ? userAgent : ""));

        // 2. Persist Tip as PENDING
        Tip tip = Tip.builder()
                .senderUserId(fromUser)
                .creatorUserId(creator)
                .amount(amount)
                .currency("eur")
                .message(message)
                .clientRequestId(clientRequestId)
                .stripePaymentIntentId(intent.getId())
                .status(TipStatus.PENDING)
                .build();

        if (maxRiskLevel == RiskLevel.MEDIUM || maxRiskLevel == RiskLevel.HIGH) {
            log.info("Marking tip as PENDING_REVIEW due to {} fraud risk for creator {}: score={}, reasons={}",
                    maxRiskLevel, fromUser.getEmail(), risk.score(), risk.reasons());
            tip.setStatus(TipStatus.PENDING_REVIEW);
        }

        persistenceService.saveTip(tip);
        riskService.recordFraudDecision(new UUID(0L, fromUser.getId()), null, tip.getId() != null ? tip.getId().getLeastSignificantBits() : null, risk);

        persistenceService.logAudit(tip, ipAddress, userAgent, Map.of(
                "amount", amount,
                "currency", "eur",
                "creator", creator.getId(),
                "clientRequestId", clientRequestId != null ? clientRequestId : ""
        ));

        log.info("MONETIZATION: Created Tip PaymentIntent {} for creator {} to creator {}",
                intent.getId(), fromUser.getEmail(), creator.getEmail());

        return intent.getClientSecret();
    }

    @Transactional
    public String createTestTip(User fromUser, Long creatorId, BigDecimal amount) throws Exception {
        User creator = persistenceService.findUserById(creatorId)
                .orElseThrow(() -> new ResourceNotFoundException("Creator not found with ID: " + creatorId));

        PaymentIntent intent = paymentService.createStripePaymentIntent(fromUser, creator, amount, "Test tip", null, RiskLevel.LOW, Map.of("type", "test_tip"));

        Tip tip = Tip.builder()
                .senderUserId(fromUser)
                .creatorUserId(creator)
                .amount(amount)
                .currency("eur")
                .stripePaymentIntentId(intent.getId())
                .status(TipStatus.PENDING)
                .build();

        persistenceService.saveTip(tip);

        log.info("MONETIZATION: Created test Tip PaymentIntent {} from {} to creator {}",
                intent.getId(), fromUser.getEmail(), creator.getEmail());

        return intent.getClientSecret();
    }

    @Transactional
    public void confirmTip(String paymentIntentId) {
        persistenceService.findByStripePaymentIntentId(paymentIntentId).ifPresent(tip -> {
            if (tip.getStatus() == TipStatus.COMPLETED) return;

            if (tip.getStatus() != TipStatus.PENDING_REVIEW) {
                tip.setStatus(TipStatus.COMPLETED);
            }
            persistenceService.saveTip(tip);

            log.info("MONETIZATION: Tip confirmed and marked as COMPLETED: {}", tip.getId());

            // Notifications
            notificationService.notifyTip(tip, null);

            // Re-evaluate AML risk for creator
            riskService.evaluateAMLRules(tip.getCreatorUserId(), BigDecimal.ZERO);
        });
    }

    @Transactional
    public TipResult sendTokenTip(User viewer, UUID roomId, long amount, String message, String clientRequestId, String ipAddress, String fingerprintHash, String giftName) {
        // 1. Validate room and creator
        Stream room = persistenceService.findStreamById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Active unified stream not found for roomId: " + roomId));
        User creator = room.getCreator();

        // 2. Prevent direct self tipping
        if (viewer.getId().equals(creator.getId())) {
            throw new IllegalStateException("Creators cannot tip their own streams");
        }

        BigDecimal euroAmountValue = BigDecimal.valueOf(amount).multiply(new BigDecimal("0.01"));

        // 3. Detect suspicious patterns
        riskService.checkSuspiciousTippingPatterns(viewer, creator, euroAmountValue, ipAddress, fingerprintHash);
        riskService.validateTippingAccess(new UUID(0L, viewer.getId()), euroAmountValue);
        
        RiskLevel riskLevel = riskService.checkPaymentLock(viewer, euroAmountValue, ipAddress, null, null, fingerprintHash);
        riskService.evaluateTrust(viewer, ipAddress, fingerprintHash);
        riskService.trackVelocity(viewer.getId());
        riskService.checkRapidTipping(new UUID(0L, viewer.getId()), ipAddress);

        // Idempotency check
        if (clientRequestId != null) {
            Optional<Tip> existing = persistenceService.findByClientRequestId(clientRequestId);
            if (existing.isPresent()) {
                log.info("MONETIZATION: Duplicate token tip request {} for creator {}. Returning existing result.", clientRequestId, viewer.getEmail());
                Tip tip = existing.get();
                return TipResult.builder()
                        .tipId(tip.getId())
                        .senderEmail(viewer.getEmail())
                        .creatorEmail(tip.getCreatorUserId().getEmail())
                        .amount(tip.getAmount())
                        .currency(tip.getCurrency())
                        .message(tip.getMessage())
                        .timestamp(tip.getCreatedAt())
                        .status(tip.getStatus().name())
                        .isDuplicate(true)
                        .viewerBalance(paymentService.getAvailableTokenBalance(viewer.getId()))
                        .creatorBalance(0L) // Simplified for duplicate
                        .build();
            }
        }

        // Anti-abuse validation
        riskService.validateTokenTip(viewer, amount, roomId);

        FraudRiskResult risk = riskService.calculateFraudRisk(viewer, euroAmountValue);
        RiskLevel maxRiskLevel = riskLevel;
        if (risk.level().ordinal() > (riskLevel != null ? riskLevel.ordinal() : -1)) {
            maxRiskLevel = RiskLevel.valueOf(risk.level().name());
        }

        if (maxRiskLevel == RiskLevel.CRITICAL) {
            log.error("Blocking tip due to CRITICAL fraud risk for creator {}: score={}, reasons={}",
                    viewer.getEmail(), risk.score(), risk.reasons());
            riskService.recordFraudDecision(new UUID(0L, viewer.getId()), roomId, null, risk);
            riskService.recordFraudIncident(new UUID(0L, viewer.getId()), "CRITICAL_FRAUD_RISK: " + String.join(", ", risk.reasons()), Map.of("score", risk.score(), "tokens", amount));
            throw new HighFraudRiskException(risk.score(), risk.reasons());
        }

        if (!room.isLive()) {
            throw new IllegalStateException("Cannot tip: Stream is not live");
        }

        // 2. Calculate fees upfront
        BigDecimal gross = BigDecimal.valueOf(amount);
        BigDecimal feeRate = paymentService.getPlatformFeeRate();
        long platformFee = gross.multiply(feeRate).setScale(0, java.math.RoundingMode.HALF_UP).longValue();
        long creatorEarning = amount - platformFee;

        // 3. Persist Tip as PENDING first (safe: no money moved yet)
        Tip tip = Tip.builder()
                .senderUserId(viewer)
                .creatorUserId(creator)
                .room(room)
                .amount(BigDecimal.valueOf(amount))
                .currency("TOKEN")
                .message(message)
                .clientRequestId(clientRequestId)
                .status(TipStatus.PENDING)
                .build();
        Tip savedTip = persistenceService.saveTip(tip);

        TipRecord tipRecord = TipRecord.builder()
                .viewer(viewer)
                .creator(creator)
                .room(room)
                .amount(amount)
                .creatorEarningTokens(creatorEarning)
                .platformFeeTokens(platformFee)
                .build();
        persistenceService.saveTipRecord(tipRecord);

        // 4. Deduct tokens (pessimistic lock on wallet — throws InsufficientBalanceException if insufficient)
        try {
            paymentService.deductTokens(viewer.getId(), amount, roomId);
        } catch (Exception e) {
            savedTip.setStatus(TipStatus.FAILED);
            persistenceService.saveTip(savedTip);
            throw e;
        }

        // 5. Record creator earnings
        paymentService.recordTokenTipEarning(viewer, creator, amount, roomId, maxRiskLevel);

        // 6. Mark tip as COMPLETED (or PENDING_REVIEW for elevated risk)
        if (maxRiskLevel == RiskLevel.MEDIUM || maxRiskLevel == RiskLevel.HIGH) {
            log.info("Marking tip as PENDING_REVIEW due to {} fraud risk for creator {}: score={}, reasons={}",
                    maxRiskLevel, viewer.getEmail(), risk.score(), risk.reasons());
            savedTip.setStatus(TipStatus.PENDING_REVIEW);
        } else {
            savedTip.setStatus(TipStatus.COMPLETED);
        }
        persistenceService.saveTip(savedTip);

        riskService.recordFraudDecision(new UUID(0L, viewer.getId()), roomId, savedTip.getId() != null ? savedTip.getId().getLeastSignificantBits() : null, risk);

        persistenceService.logAudit(tipRecord, ipAddress, null);

        // Notifications (after all DB operations succeed)
        notificationService.notifyTip(savedTip, giftName);

        log.info("MONETIZATION: Token tip of {} tokens from {} to {} in room {}",
                amount, viewer.getEmail(), creator.getEmail(), roomId);

        // Re-evaluate AML risk for creator
        riskService.evaluateAMLRules(creator, BigDecimal.ZERO);

        // 7. Return tip result DTO
        return TipResult.builder()
                .tipId(savedTip.getId())
                .senderEmail(viewer.getEmail())
                .creatorEmail(creator.getEmail())
                .amount(BigDecimal.valueOf(amount))
                .currency("TOKEN")
                .message(message)
                .timestamp(savedTip.getCreatedAt())
                .status(savedTip.getStatus().name())
                .viewerBalance(paymentService.getAvailableTokenBalance(viewer.getId()))
                .creatorBalance(0L) // Optional or fetch from balance service
                .build();
    }
}
