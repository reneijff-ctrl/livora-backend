package com.joinlivora.backend.monetization;

import com.joinlivora.backend.analytics.AnalyticsEventPublisher;
import com.joinlivora.backend.analytics.AnalyticsEventType;
import com.joinlivora.backend.chargeback.InternalChargebackService;
import com.joinlivora.backend.exception.*;
import com.joinlivora.backend.fraud.exception.HighFraudRiskException;
import com.joinlivora.backend.fraud.FraudScoringService;
import com.joinlivora.backend.fraud.model.FraudRiskResult;
import com.joinlivora.backend.fraud.dto.RiskDecisionResult;
import com.joinlivora.backend.fraud.model.FraudDecisionLevel;
import com.joinlivora.backend.fraud.model.FraudSignalType;
import com.joinlivora.backend.fraud.model.FraudSource;
import com.joinlivora.backend.fraud.model.RiskDecision;
import com.joinlivora.backend.fraud.model.VelocityActionType;
import com.joinlivora.backend.fraud.service.FraudDetectionService;
import com.joinlivora.backend.fraud.service.TrustEvaluationService;
import com.joinlivora.backend.fraud.service.VelocityTrackerService;
import com.joinlivora.backend.payment.PaymentService;
import com.joinlivora.backend.monetization.dto.TipResult;
import com.joinlivora.backend.payout.CreatorEarningsService;
import com.joinlivora.backend.streaming.StreamRoom;
import com.joinlivora.backend.streaming.StreamRoomRepository;
import com.joinlivora.backend.token.*;
import com.joinlivora.backend.wallet.WalletTransactionType;
import com.joinlivora.backend.reputation.model.ReputationEventSource;
import com.joinlivora.backend.reputation.model.ReputationEventType;
import com.joinlivora.backend.reputation.service.ReputationEventService;
import com.joinlivora.backend.abuse.AbuseDetectionService;
import com.joinlivora.backend.aml.service.AMLRulesEngine;
import com.joinlivora.backend.audit.service.AuditService;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import com.stripe.StripeClient;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import com.joinlivora.backend.websocket.RealtimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class TipService {

    private final TipRepository tipRepository;
    private final UserService userService;
    private final CreatorEarningsService creatorEarningsService;
    private final AnalyticsEventPublisher analyticsEventPublisher;
    private final org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;
    private final StripeClient stripeClient;
    private final TokenWalletService tokenWalletService;
    private final StreamRoomRepository streamRoomRepository;
    private final TipRecordRepository tipRecordRepository;
    private final TipValidationService tipValidationService;
    private final PaymentService paymentService;
    private final VelocityTrackerService velocityTrackerService;
    private final TrustEvaluationService trustEvaluationService;
    private final FraudDetectionService fraudDetectionService;
    private final ReputationEventService reputationEventService;
    private final AMLRulesEngine amlRulesEngine;
    private final AuditService auditService;
    private final FraudScoringService fraudRiskService;
    private final InternalChargebackService chargebackService;
    private final AbuseDetectionService abuseDetectionService;
    private final com.joinlivora.backend.abuse.RestrictionService restrictionService;
    private final com.joinlivora.backend.fraud.service.EnforcementService enforcementService;
    private final Environment environment;

    public TipService(
            TipRepository tipRepository,
            UserService userService,
            @org.springframework.context.annotation.Lazy CreatorEarningsService creatorEarningsService,
            AnalyticsEventPublisher analyticsEventPublisher,
            @org.springframework.context.annotation.Lazy org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate,
            StripeClient stripeClient,
            TokenWalletService tokenWalletService,
            StreamRoomRepository streamRoomRepository,
            TipRecordRepository tipRecordRepository,
            TipValidationService tipValidationService,
            @org.springframework.context.annotation.Lazy PaymentService paymentService,
            VelocityTrackerService velocityTrackerService,
            TrustEvaluationService trustEvaluationService,
            FraudDetectionService fraudDetectionService,
            ReputationEventService reputationEventService,
            AMLRulesEngine amlRulesEngine,
            AuditService auditService,
            FraudScoringService fraudRiskService,
            InternalChargebackService chargebackService,
            AbuseDetectionService abuseDetectionService,
            com.joinlivora.backend.abuse.RestrictionService restrictionService,
            com.joinlivora.backend.fraud.service.EnforcementService enforcementService,
            Environment environment) {
        this.tipRepository = tipRepository;
        this.userService = userService;
        this.creatorEarningsService = creatorEarningsService;
        this.analyticsEventPublisher = analyticsEventPublisher;
        this.messagingTemplate = messagingTemplate;
        this.stripeClient = stripeClient;
        this.tokenWalletService = tokenWalletService;
        this.streamRoomRepository = streamRoomRepository;
        this.tipRecordRepository = tipRecordRepository;
        this.tipValidationService = tipValidationService;
        this.paymentService = paymentService;
        this.velocityTrackerService = velocityTrackerService;
        this.trustEvaluationService = trustEvaluationService;
        this.fraudDetectionService = fraudDetectionService;
        this.reputationEventService = reputationEventService;
        this.amlRulesEngine = amlRulesEngine;
        this.auditService = auditService;
        this.fraudRiskService = fraudRiskService;
        this.chargebackService = chargebackService;
        this.abuseDetectionService = abuseDetectionService;
        this.restrictionService = restrictionService;
        this.enforcementService = enforcementService;
        this.environment = environment;
    }


    @Transactional
    public String createTipIntent(User fromUser, Long creatorId, BigDecimal amount, String message, String clientRequestId, String ipAddress, String country, String userAgent, String fingerprintHash) throws Exception {
        restrictionService.validateTippingAccess(new UUID(0L, fromUser.getId()), amount);
        com.joinlivora.backend.fraud.model.RiskLevel riskLevel = paymentService.checkPaymentLock(fromUser, amount, ipAddress, country, userAgent, fingerprintHash);
        evaluateTrust(fromUser, ipAddress, fingerprintHash);
        velocityTrackerService.trackAction(fromUser.getId(), VelocityActionType.TIP);
        abuseDetectionService.checkRapidTipping(new UUID(0L, fromUser.getId()), ipAddress);
        // Idempotency check
        if (clientRequestId != null) {
            java.util.Optional<Tip> existing = tipRepository.findByClientRequestId(clientRequestId);
            if (existing.isPresent()) {
                log.info("MONETIZATION: Duplicate Tip request {} for creator {}. Returning existing status.", clientRequestId, fromUser.getEmail());
                if (existing.get().getStripePaymentIntentId() != null) {
                    try {
                        PaymentIntent pi = stripeClient.paymentIntents().retrieve(existing.get().getStripePaymentIntentId());
                        return pi.getClientSecret();
                    } catch (Exception e) {
                        log.error("Failed to retrieve existing PaymentIntent", e);
                    }
                }
                throw new com.joinlivora.backend.exception.DuplicateRequestException("A tip with this request ID already exists.");
            }
        }

        User creator = userService.getById(creatorId);
        
        FraudRiskResult risk = calculateFraudRisk(fromUser, amount);
        com.joinlivora.backend.fraud.model.RiskLevel maxRiskLevel = riskLevel;
        if (risk.level().ordinal() > (riskLevel != null ? riskLevel.ordinal() : -1)) {
            maxRiskLevel = com.joinlivora.backend.fraud.model.RiskLevel.valueOf(risk.level().name());
        }

        if (maxRiskLevel == com.joinlivora.backend.fraud.model.RiskLevel.CRITICAL) {
            log.error("Blocking tip due to CRITICAL fraud risk for creator {}: score={}, reasons={}",
                    fromUser.getEmail(), risk.score(), risk.reasons());
            fraudRiskService.recordDecision(new UUID(0L, fromUser.getId()), null, null, risk);
            enforcementService.recordFraudIncident(new UUID(0L, fromUser.getId()), "CRITICAL_FRAUD_RISK: " + String.join(", ", risk.reasons()), Map.of("score", risk.score(), "amount", amount));
            throw new HighFraudRiskException(risk.score(), risk.reasons());
        }

        if (maxRiskLevel == com.joinlivora.backend.fraud.model.RiskLevel.HIGH) {
            log.warn("Allowing tip but marking for review due to HIGH fraud risk for creator {}: score={}, reasons={}",
                    fromUser.getEmail(), risk.score(), risk.reasons());
        }

        // Anti-abuse validation
        tipValidationService.validateStripeTip(fromUser, amount);
        PaymentIntentCreateParams.Builder builder = PaymentIntentCreateParams.builder()
                .setAmount(amount.multiply(new BigDecimal("100")).longValue())
                .setCurrency("eur")
                .putMetadata("type", "tip")
                .putMetadata("from_user_id", fromUser.getId().toString())
                .putMetadata("creator_id", creator.getId().toString())
                .putMetadata("message", message != null ? message : "")
                .putMetadata("client_request_id", clientRequestId != null ? clientRequestId : "")
                .putMetadata("fraud_risk_level", maxRiskLevel != null ? maxRiskLevel.name() : "LOW")
                .setCaptureMethod(PaymentIntentCreateParams.CaptureMethod.AUTOMATIC);

        // Stripe Connect (destination charges) preparation
        // All creator earnings are still recorded in our internal platform balance (ledger) via CreatorEarningsService.
        // Funds flow via Stripe are configured as destination charges so the application fee is collected by the platform.
        if (creator.getStripeAccountId() != null && !creator.getStripeAccountId().isBlank()) {
            // Calculate platform fee in cents using configured percentage
            java.math.BigDecimal feeRate = creatorEarningsService.getPlatformFeeRate();
            long applicationFeeAmount = amount
                    .multiply(feeRate)
                    .multiply(new java.math.BigDecimal("100"))
                    .setScale(0, java.math.RoundingMode.HALF_UP)
                    .longValue();

            builder
                .setApplicationFeeAmount(applicationFeeAmount)
                .setTransferData(
                    PaymentIntentCreateParams.TransferData.builder()
                        .setDestination(creator.getStripeAccountId())
                        .build()
                );
            
            // TODO: In the future, we may want to hold funds on the platform balance 
            // and use manual transfers for scheduled payouts instead of destination charges.
        }

        if (ipAddress != null) builder.putMetadata("ip_address", ipAddress);
        if (country != null) builder.putMetadata("country", country);
        if (userAgent != null) builder.putMetadata("user_agent", userAgent);

        PaymentIntent intent = stripeClient.paymentIntents().create(builder.build());

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

        if (maxRiskLevel == com.joinlivora.backend.fraud.model.RiskLevel.MEDIUM || maxRiskLevel == com.joinlivora.backend.fraud.model.RiskLevel.HIGH) {
            log.info("Marking tip as PENDING_REVIEW due to {} fraud risk for creator {}: score={}, reasons={}",
                    maxRiskLevel, fromUser.getEmail(), risk.score(), risk.reasons());
            tip.setStatus(TipStatus.PENDING_REVIEW);
        }

        tipRepository.save(tip);
        fraudRiskService.recordDecision(new UUID(0L, fromUser.getId()), null, tip.getId() != null ? tip.getId().getLeastSignificantBits() : null, risk);

        auditService.logEvent(
                new UUID(0L, fromUser.getId()),
                AuditService.TIP_CREATED,
                "TIP",
                tip.getId(),
                Map.of(
                        "amount", amount,
                        "currency", "eur",
                        "creator", creatorId,
                        "clientRequestId", clientRequestId != null ? clientRequestId : ""
                ),
                ipAddress,
                userAgent
        );

        log.info("MONETIZATION: Created Tip PaymentIntent {} for creator {} to creator {}",
                intent.getId(), fromUser.getEmail(), creator.getEmail());

        return intent.getClientSecret();
    }

    @Transactional
    public String createTestTip(User fromUser, Long creatorId, BigDecimal amount) throws Exception {
        User creator = userService.getById(creatorId);

        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(amount.multiply(new BigDecimal("100")).longValue())
                .setCurrency("eur")
                .putMetadata("type", "test_tip")
                .putMetadata("from_user_id", fromUser.getId().toString())
                .putMetadata("creator_id", creator.getId().toString())
                .putMetadata("fraud_risk_level", "LOW")
                .setCaptureMethod(PaymentIntentCreateParams.CaptureMethod.AUTOMATIC)
                .build();

        PaymentIntent intent = stripeClient.paymentIntents().create(params);

        Tip tip = Tip.builder()
                .senderUserId(fromUser)
                .creatorUserId(creator)
                .amount(amount)
                .currency("eur")
                .stripePaymentIntentId(intent.getId())
                .status(TipStatus.PENDING)
                .build();

        tipRepository.save(tip);

        log.info("MONETIZATION: Created test Tip PaymentIntent {} from {} to creator {}",
                intent.getId(), fromUser.getEmail(), creator.getEmail());

        return intent.getClientSecret();
    }

    @Transactional
    public void confirmTip(String paymentIntentId) {
        tipRepository.findByStripePaymentIntentId(paymentIntentId).ifPresent(tip -> {
            if (tip.getStatus() == TipStatus.COMPLETED) return;

            if (tip.getStatus() != TipStatus.PENDING_REVIEW) {
                tip.setStatus(TipStatus.COMPLETED);
            }
            tipRepository.save(tip);

            // We will record earnings in StripeWebhookController via creatorEarningsService
            // This method is called from webhook
            
            analyticsEventPublisher.publishEvent(
                    AnalyticsEventType.PAYMENT_SUCCEEDED,
                    tip.getSenderUserId(),
                    Map.of(
                            "type", "tip",
                            "amount", tip.getAmount(),
                            "creator", tip.getCreatorUserId().getId()
                    )
            );

            log.info("MONETIZATION: Tip confirmed and marked as COMPLETED: {}", tip.getId());

            reputationEventService.recordEvent(
                    new UUID(0L, tip.getCreatorUserId().getId()),
                    ReputationEventType.TIP,
                    2,
                    ReputationEventSource.SYSTEM,
                    Map.of("tipId", tip.getId(), "amount", tip.getAmount(), "currency", tip.getCurrency())
            );
            
            // Notify room via WebSocket if applicable
            if (tip.getRoom() != null) {
                messagingTemplate.convertAndSend("/topic/chat/" + tip.getRoom().getId(),
                        RealtimeMessage.of("TIP", Map.of(
                                "viewer", tip.getSenderUserId().getEmail().split("@")[0],
                                "amount", tip.getAmount(),
                                "currency", tip.getCurrency(),
                                "message", tip.getMessage() != null ? tip.getMessage() : "",
                                "animationType", getAnimationForAmount(tip.getAmount().longValue())
                        )));
            }

            // Notify creator via WebSocket
            messagingTemplate.convertAndSendToUser(
                    tip.getCreatorUserId().getEmail(),
                    "/queue/notifications",
                    Map.of(
                            "type", "NEW_TIP",
                            "payload", Map.of(
                                    "amount", tip.getAmount(),
                                    "currency", tip.getCurrency(),
                                    "senderUserId", tip.getSenderUserId().getEmail(),
                                    "message", tip.getMessage() != null ? tip.getMessage() : ""
                            )
                    )
            );
            
            // Also notify creator dashboard
            messagingTemplate.convertAndSendToUser(
                    tip.getCreatorUserId().getEmail(),
                    "/queue/creator/stats",
                    Map.of("type", "STATS_UPDATE")
            );

            // Re-evaluate AML risk for creator
            amlRulesEngine.evaluateRules(tip.getCreatorUserId(), BigDecimal.ZERO);
        });
    }

    @Transactional
    public TipResult sendTokenTip(User viewer, UUID roomId, long amount, String message, String clientRequestId, String ipAddress, String fingerprintHash) {
        BigDecimal euroAmount = BigDecimal.valueOf(amount).multiply(new BigDecimal("0.01"));
        restrictionService.validateTippingAccess(new UUID(0L, viewer.getId()), euroAmount);
        com.joinlivora.backend.fraud.model.RiskLevel riskLevel = paymentService.checkPaymentLock(viewer, euroAmount, ipAddress, null, null, fingerprintHash);
        evaluateTrust(viewer, ipAddress, fingerprintHash);
        velocityTrackerService.trackAction(viewer.getId(), VelocityActionType.TIP);
        abuseDetectionService.checkRapidTipping(new UUID(0L, viewer.getId()), ipAddress);
        // Idempotency check
        if (clientRequestId != null) {
            java.util.Optional<Tip> existing = tipRepository.findByClientRequestId(clientRequestId);
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
                        .viewerBalance(tokenWalletService.getAvailableBalance(viewer.getId()))
                        .creatorBalance(creatorEarningsService.getAggregatedEarnings(tip.getCreatorUserId()) != null ? creatorEarningsService.getAggregatedEarnings(tip.getCreatorUserId()).getTotalTokens() : 0L)
                        .build();
            }
        }

        // Anti-abuse validation
        tipValidationService.validateTokenTip(viewer, amount, roomId);

        // 1. Validate room and creator
        StreamRoom room = streamRoomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Stream room not found"));

        BigDecimal euroAmountValue = BigDecimal.valueOf(amount).multiply(new BigDecimal("0.01"));
        FraudRiskResult risk = calculateFraudRisk(viewer, euroAmountValue);
        com.joinlivora.backend.fraud.model.RiskLevel maxRiskLevel = riskLevel;
        if (risk.level().ordinal() > (riskLevel != null ? riskLevel.ordinal() : -1)) {
            maxRiskLevel = com.joinlivora.backend.fraud.model.RiskLevel.valueOf(risk.level().name());
        }

        if (maxRiskLevel == com.joinlivora.backend.fraud.model.RiskLevel.CRITICAL) {
            log.error("Blocking tip due to CRITICAL fraud risk for creator {}: score={}, reasons={}",
                    viewer.getEmail(), risk.score(), risk.reasons());
            fraudRiskService.recordDecision(new UUID(0L, viewer.getId()), roomId, null, risk);
            enforcementService.recordFraudIncident(new UUID(0L, viewer.getId()), "CRITICAL_FRAUD_RISK: " + String.join(", ", risk.reasons()), Map.of("score", risk.score(), "tokens", amount));
            throw new HighFraudRiskException(risk.score(), risk.reasons());
        }

        if (maxRiskLevel == com.joinlivora.backend.fraud.model.RiskLevel.HIGH) {
            log.warn("Allowing tip but marking for review due to HIGH fraud risk for creator {}: score={}, reasons={}",
                    viewer.getEmail(), risk.score(), risk.reasons());
        }

        if (!room.isLive()) {
            throw new IllegalStateException("Cannot tip: Stream is not live");
        }

        User creator = room.getCreator();

        // 2. Validate sender balance
        long availableBalance = tokenWalletService.getAvailableBalance(viewer.getId());
        if (availableBalance < amount) {
            throw new com.joinlivora.backend.exception.InsufficientBalanceException("Insufficient tokens for tip");
        }

        // 3. Deduct tokens via TokenWalletService
        tokenWalletService.deductTokens(viewer.getId(), amount, WalletTransactionType.TIP, "Tip to room " + roomId);

        // 4. Calculate fees and earnings using central CreatorEarningsService
        creatorEarningsService.recordTokenTipEarning(viewer, creator, amount, roomId, maxRiskLevel);

        // 5. Persist TipRecord (Token-specific)
        // We still keep this for detailed tipping logs
        BigDecimal gross = BigDecimal.valueOf(amount);
        BigDecimal feeRate = creatorEarningsService.getPlatformFeeRate();
        long platformFee = gross.multiply(feeRate).setScale(0, java.math.RoundingMode.HALF_UP).longValue();
        long creatorEarning = amount - platformFee;

        TipRecord tipRecord = TipRecord.builder()
                .viewer(viewer)
                .creator(creator)
                .room(room)
                .amount(amount)
                .creatorEarningTokens(creatorEarning)
                .platformFeeTokens(platformFee)
                .build();
        tipRecordRepository.save(tipRecord);

        // 7. Persist Tip record (Unified record)
        Tip tip = Tip.builder()
                .senderUserId(viewer)
                .creatorUserId(creator)
                .room(room)
                .amount(BigDecimal.valueOf(amount))
                .currency("TOKEN")
                .message(message)
                .clientRequestId(clientRequestId)
                .status(TipStatus.COMPLETED)
                .build();

        if (maxRiskLevel == com.joinlivora.backend.fraud.model.RiskLevel.MEDIUM || maxRiskLevel == com.joinlivora.backend.fraud.model.RiskLevel.HIGH) {
            log.info("Marking tip as PENDING_REVIEW due to {} fraud risk for creator {}: score={}, reasons={}",
                    maxRiskLevel, viewer.getEmail(), risk.score(), risk.reasons());
            tip.setStatus(TipStatus.PENDING_REVIEW);
        }

        Tip savedTip = tipRepository.save(tip);
        fraudRiskService.recordDecision(new UUID(0L, viewer.getId()), roomId, savedTip.getId() != null ? savedTip.getId().getLeastSignificantBits() : null, risk);

        auditService.logEvent(
                new UUID(0L, viewer.getId()),
                AuditService.TIP_CREATED,
                "TIP",
                savedTip.getId(),
                Map.of(
                        "amount", amount,
                        "currency", "TOKEN",
                        "roomId", roomId,
                        "clientRequestId", clientRequestId != null ? clientRequestId : ""
                ),
                ipAddress,
                null
        );

        reputationEventService.recordEvent(
                new UUID(0L, creator.getId()),
                ReputationEventType.TIP,
                2,
                ReputationEventSource.SYSTEM,
                Map.of("tipId", savedTip.getId(), "amount", savedTip.getAmount(), "currency", savedTip.getCurrency())
        );

        // Notify room via WebSocket
        messagingTemplate.convertAndSend("/topic/chat/" + roomId,
                RealtimeMessage.of("TIP", Map.of(
                        "viewer", viewer.getEmail().split("@")[0],
                        "amount", amount,
                        "currency", "TOKEN",
                        "message", message != null ? message : "",
                        "clientRequestId", clientRequestId != null ? clientRequestId : "",
                        "animationType", getAnimationForAmount(amount)
                )));

        // Notify creator via private WebSocket queue
        messagingTemplate.convertAndSendToUser(creator.getEmail(), "/queue/tips", Map.of(
                "type", "TIP",
                "viewer", viewer.getEmail().split("@")[0],
                "displayName", viewer.getDisplayName() != null ? viewer.getDisplayName() : viewer.getEmail().split("@")[0],
                "amount", amount,
                "currency", "TOKEN",
                "message", message != null ? message : "",
                "timestamp", savedTip.getCreatedAt(),
                "animationType", getAnimationForAmount(amount)
        ));

        // 8. Emit analytics event
        analyticsEventPublisher.publishEvent(
                AnalyticsEventType.PAYMENT_SUCCEEDED,
                viewer,
                Map.of(
                        "type", "tip",
                        "currency", "TOKEN",
                        "amount", amount,
                        "creator", creator.getId(),
                        "roomId", roomId,
                        "tipId", savedTip.getId()
                )
        );

        log.info("MONETIZATION: Token tip of {} tokens from {} to {} in room {}",
                amount, viewer.getEmail(), creator.getEmail(), roomId);

        // Re-evaluate AML risk for creator
        amlRulesEngine.evaluateRules(creator, BigDecimal.ZERO);

        // 9. Return tip result DTO
        return TipResult.builder()
                .tipId(savedTip.getId())
                .senderEmail(viewer.getEmail())
                .creatorEmail(creator.getEmail())
                .amount(BigDecimal.valueOf(amount))
                .currency("TOKEN")
                .message(message)
                .timestamp(savedTip.getCreatedAt())
                .status(savedTip.getStatus().name())
                .viewerBalance(tokenWalletService.getAvailableBalance(viewer.getId()))
                .creatorBalance(creatorEarningsService.getAggregatedEarnings(creator) != null ? creatorEarningsService.getAggregatedEarnings(creator).getTotalTokens() : 0L)
                .build();
    }

    private String getAnimationForAmount(long amount) {
        if (amount >= 1000) return "fireworks";
        if (amount >= 500) return "diamond";
        if (amount >= 100) return "heart";
        return "coin";
    }

    private void evaluateTrust(User user, String ipAddress, String fingerprintHash) {
        RiskDecisionResult result = trustEvaluationService.evaluate(user, fingerprintHash, ipAddress);
        if (result.getDecision() == RiskDecision.BLOCK) {
            log.warn("SECURITY [trust_evaluation]: Blocked tip attempt for creator: {} from IP: {} with fingerprint: {}. ExplanationId: {}",
                    user.getEmail(), ipAddress, fingerprintHash, result.getExplanationId());
            fraudDetectionService.logFraudSignal(user.getId(), FraudDecisionLevel.HIGH, FraudSource.PAYMENT, FraudSignalType.TRUST_EVALUATION_BLOCK, "TRUST_EVALUATION_BLOCK");
            throw new org.springframework.security.access.AccessDeniedException("Action blocked due to security risk.");
        } else if (result.getDecision() == RiskDecision.REVIEW) {
            log.info("SECURITY [trust_evaluation]: Trust challenge required for tip for creator: {}. ExplanationId: {}", user.getEmail(), result.getExplanationId());
            
            if (isDevProfile()) {
                log.warn("DEV MODE: Bypassing trust REVIEW for tipping.");
                return;
            }
            
            throw new TrustChallengeException("Additional verification required to complete this action.");
        }
    }

    private boolean isDevProfile() {
        return java.util.Arrays.asList(environment.getActiveProfiles()).contains("dev");
    }

    private FraudRiskResult calculateFraudRisk(User user, BigDecimal amount) {
        java.time.Instant fiveMinutesAgo = java.time.Instant.now().minus(java.time.Duration.ofMinutes(5));
        int tipsInLast5Minutes = (int) tipRepository.countBySenderUserId_IdAndCreatedAtAfter(user.getId(), fiveMinutesAgo);

        // Calculate total amount in short window (last 5 minutes)
        BigDecimal totalAmountShortWindow = tipRepository.aggregateTips(fiveMinutesAgo).stream()
                .filter(row -> row[0].equals(user.getId()))
                .map(row -> (BigDecimal) row[2])
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .add(amount);

        long accountAgeDays = java.time.Duration.between(user.getCreatedAt(), java.time.Instant.now()).toDays();

        // Check if IP or device has changed recently (comparing with last successful login)
        // Simplified check for now
        boolean ipOrDeviceChanged = false;

        java.util.UUID userUuid = new java.util.UUID(0L, user.getId());
        int previousChargebacks = (int) chargebackService.getChargebackCount(userUuid);

        return fraudRiskService.calculateRisk(
                userUuid,
                amount,
                tipsInLast5Minutes,
                totalAmountShortWindow,
                accountAgeDays,
                ipOrDeviceChanged,
                previousChargebacks
        );
    }
}
