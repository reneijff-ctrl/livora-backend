package com.joinlivora.backend.monetization;

import com.joinlivora.backend.analytics.AnalyticsEventPublisher;
import com.joinlivora.backend.analytics.AnalyticsEventType;
import com.joinlivora.backend.audit.service.AuditService;
import com.joinlivora.backend.exception.ResourceNotFoundException;
import com.joinlivora.backend.abuse.AbuseDetectionService;
import com.joinlivora.backend.chargeback.ChargebackService;
import com.joinlivora.backend.fraud.exception.HighFraudRiskException;
import com.joinlivora.backend.fraud.FraudScoringService;
import com.joinlivora.backend.fraud.model.FraudRiskResult;
import com.joinlivora.backend.payment.PaymentService;
import com.joinlivora.backend.payout.CreatorEarningsService;
import com.joinlivora.backend.streaming.Stream;
import com.joinlivora.backend.user.User;
import com.stripe.StripeClient;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service("highlightedMessageService")
@Slf4j
public class HighlightedMessageService {

    private final HighlightedChatMessageRepository repository;
    private final StripeClient stripeClient;
    private final com.joinlivora.backend.streaming.StreamRepository streamRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final TipValidationService tipValidationService;
    private final AuditService auditService;
    private final CreatorEarningsService creatorEarningsService;
    private final AnalyticsEventPublisher analyticsEventPublisher;
    private final PaymentService paymentService;
    private final FraudScoringService fraudRiskService;
    private final TipRepository tipRepository;
    private final ChargebackService chargebackService;
    private final AbuseDetectionService abuseDetectionService;
    private final com.joinlivora.backend.abuse.RestrictionService restrictionService;
    private final com.joinlivora.backend.fraud.service.EnforcementService enforcementService;

    public HighlightedMessageService(
            HighlightedChatMessageRepository repository,
            StripeClient stripeClient,
            com.joinlivora.backend.streaming.StreamRepository streamRepository,
            @org.springframework.context.annotation.Lazy SimpMessagingTemplate messagingTemplate,
            TipValidationService tipValidationService,
            AuditService auditService,
            CreatorEarningsService creatorEarningsService,
            AnalyticsEventPublisher analyticsEventPublisher,
            PaymentService paymentService,
            FraudScoringService fraudRiskService,
            TipRepository tipRepository,
            ChargebackService chargebackService,
            AbuseDetectionService abuseDetectionService,
            com.joinlivora.backend.abuse.RestrictionService restrictionService,
            com.joinlivora.backend.fraud.service.EnforcementService enforcementService) {
        this.repository = repository;
        this.stripeClient = stripeClient;
        this.streamRepository = streamRepository;
        this.messagingTemplate = messagingTemplate;
        this.tipValidationService = tipValidationService;
        this.auditService = auditService;
        this.creatorEarningsService = creatorEarningsService;
        this.analyticsEventPublisher = analyticsEventPublisher;
        this.paymentService = paymentService;
        this.fraudRiskService = fraudRiskService;
        this.tipRepository = tipRepository;
        this.chargebackService = chargebackService;
        this.abuseDetectionService = abuseDetectionService;
        this.restrictionService = restrictionService;
        this.enforcementService = enforcementService;
    }

    @Transactional
    public String createHighlightIntent(User user, UUID roomId, String messageId, String content, HighlightType highlightType, BigDecimal amount, String clientRequestId, String ipAddress, String country, String userAgent) throws Exception {
        restrictionService.validateTippingAccess(new UUID(0L, user.getId()), amount);
        com.joinlivora.backend.fraud.model.RiskLevel riskLevel = paymentService.checkPaymentLock(user, amount, ipAddress, country, userAgent, null);

        abuseDetectionService.checkRapidTipping(new UUID(0L, user.getId()), ipAddress);

        FraudRiskResult risk = calculateFraudRisk(user, amount);
        com.joinlivora.backend.fraud.model.RiskLevel maxRiskLevel = riskLevel;
        if (risk.level().ordinal() > (riskLevel != null ? riskLevel.ordinal() : -1)) {
            maxRiskLevel = com.joinlivora.backend.fraud.model.RiskLevel.valueOf(risk.level().name());
        }

        if (maxRiskLevel == com.joinlivora.backend.fraud.model.RiskLevel.CRITICAL) {
            log.error("Blocking highlight due to CRITICAL fraud risk for creator {}: score={}, reasons={}",
                    user.getEmail(), risk.score(), risk.reasons());
            fraudRiskService.recordDecision(new UUID(0L, user.getId()), roomId, null, risk);
            enforcementService.recordFraudIncident(new UUID(0L, user.getId()), "CRITICAL_FRAUD_RISK: " + String.join(", ", risk.reasons()), Map.of("score", risk.score(), "amount", amount));
            throw new HighFraudRiskException(risk.score(), risk.reasons());
        }

        if (maxRiskLevel == com.joinlivora.backend.fraud.model.RiskLevel.HIGH) {
            log.warn("Allowing highlight but marking for review due to HIGH fraud risk for creator {}: score={}, reasons={}",
                    user.getEmail(), risk.score(), risk.reasons());
        }

        // 1. Validate amount
        if (amount == null || amount.compareTo(highlightType.getMinimumAmount()) < 0) {
            throw new IllegalArgumentException("Amount " + amount + " is below minimum for " + highlightType + " (" + highlightType.getMinimumAmount() + ")");
        }

        // 2. Idempotency check
        if (clientRequestId != null) {
            Optional<HighlightedMessage> existing = repository.findByClientRequestId(clientRequestId);
            if (existing.isPresent()) {
                log.info("MONETIZATION: Duplicate highlight request {} for creator {}. Returning existing status.", clientRequestId, user.getEmail());
                if (existing.get().getStripePaymentIntentId() != null) {
                    try {
                        PaymentIntent pi = stripeClient.paymentIntents().retrieve(existing.get().getStripePaymentIntentId());
                        return pi.getClientSecret();
                    } catch (Exception e) {
                        log.error("Failed to retrieve existing PaymentIntent", e);
                    }
                }
                throw new com.joinlivora.backend.exception.DuplicateRequestException("A highlight with this request ID already exists.");
            }
        }

        // 3. Anti-abuse validation
        tipValidationService.validateHighlight(user, roomId);

        // Resolve unified Stream identity
        Stream stream = streamRepository.findById(roomId)
                .orElseGet(() -> streamRepository.findByMediasoupRoomId(roomId)
                        .orElseThrow(() -> new ResourceNotFoundException("Active unified stream not found for roomId: " + roomId)));

        // 4. Create Stripe PaymentIntent
        User creator = stream.getCreator();
        PaymentIntentCreateParams.Builder builder = PaymentIntentCreateParams.builder()
                .setAmount(amount.multiply(new BigDecimal("100")).longValue())
                .setCurrency("eur")
                .putMetadata("type", "highlighted_message")
                .putMetadata("user_id", user.getId().toString())
                .putMetadata("room_id", roomId.toString())
                .putMetadata("message_id", messageId)
                .putMetadata("creator_id", creator.getId().toString())
                .putMetadata("highlight_type", highlightType.name())
                .putMetadata("client_request_id", clientRequestId != null ? clientRequestId : "")
                .putMetadata("fraud_risk_level", maxRiskLevel != null ? maxRiskLevel.name() : "LOW")
                .setCaptureMethod(PaymentIntentCreateParams.CaptureMethod.AUTOMATIC);

        // Stripe Connect (destination charges)
        // All creator earnings are recorded in our internal platform balance (ledger) via CreatorEarningsService.
        // Funds flow via Stripe are configured as destination charges so the application fee is collected by the platform.
        if (creator.getStripeAccountId() != null && !creator.getStripeAccountId().isBlank()) {
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

        // 5. Persist HighlightedMessage as PENDING
        HighlightedMessage highlightedMessage = HighlightedMessage.builder()
                .messageId(messageId)
                .content(content)
                .userId(user)
                .roomId(stream)
                .amount(amount)
                .currency("eur")
                .highlightType(highlightType)
                .status(TipStatus.PENDING)
                .stripePaymentIntentId(intent.getId())
                .clientRequestId(clientRequestId)
                .build();

        if (maxRiskLevel == com.joinlivora.backend.fraud.model.RiskLevel.MEDIUM || maxRiskLevel == com.joinlivora.backend.fraud.model.RiskLevel.HIGH) {
            log.info("Marking highlight as PENDING_REVIEW due to {} fraud risk for creator {}: score={}, reasons={}",
                    maxRiskLevel, user.getEmail(), risk.score(), risk.reasons());
            highlightedMessage.setStatus(TipStatus.PENDING_REVIEW);
        }

        repository.save(highlightedMessage);
        fraudRiskService.recordDecision(new UUID(0L, user.getId()), roomId, highlightedMessage.getId() != null ? highlightedMessage.getId().getLeastSignificantBits() : null, risk);

        log.info("MONETIZATION: Created Highlight PaymentIntent {} for creator {} in room {}",
                intent.getId(), user.getEmail(), roomId);

        return intent.getClientSecret();
    }

    @Transactional
    public void confirmHighlight(String paymentIntentId) {
        repository.findByStripePaymentIntentId(paymentIntentId).ifPresent(msg -> {
            if (msg.getStatus() == TipStatus.COMPLETED) return;

            if (msg.getStatus() != TipStatus.PENDING_REVIEW) {
                msg.setStatus(TipStatus.COMPLETED);
            }
            repository.save(msg);

            log.info("MONETIZATION: Highlighted message confirmed and marked as COMPLETED: {}", msg.getId());

            // Emit analytics event
            analyticsEventPublisher.publishEvent(
                    AnalyticsEventType.HIGHLIGHTED_MESSAGE_SENT,
                    msg.getUserId(),
                    Map.of(
                            "amount", msg.getAmount(),
                            "roomId", msg.getRoomId().getId(),
                            "creator", msg.getUserId().getId(),
                            "highlightType", msg.getHighlightType().name()
                    )
            );

            // Notify via WebSocket
            broadcastHighlight(msg);
        });
    }

    @Transactional
    public void removeHighlight(UUID highlightId, User moderator, String reason) {
        HighlightedMessage msg = repository.findById(highlightId)
                .orElseThrow(() -> new ResourceNotFoundException("Highlighted message not found"));

        msg.setModerated(true);
        msg.setModeratedBy(moderator);
        msg.setModeratedAt(java.time.Instant.now());
        msg.setModerationReason(reason);
        repository.save(msg);

        log.info("MODERATION: Highlighted message {} removed by {}. Reason: {}", highlightId, moderator.getEmail(), reason);

        auditService.logEvent(
                new UUID(0L, moderator.getId()),
                AuditService.CONTENT_TAKEDOWN,
                "HIGHLIGHT",
                highlightId,
                Map.of("action", "remove", "type", reason != null ? reason : ""),
                null,
                null
        );

        // Notify frontend to remove the highlight (Using creatorId routing)
        messagingTemplate.convertAndSend("/exchange/amq.topic/chat." + msg.getRoomId().getCreator().getId(),
                com.joinlivora.backend.websocket.RealtimeMessage.of("REMOVE_HIGHLIGHT", Map.of(
                        "messageId", msg.getMessageId(),
                        "highlightId", highlightId
                ))
        );
    }

    @Transactional
    public void refundHighlight(UUID highlightId, User moderator, String reason) throws Exception {
        HighlightedMessage msg = repository.findById(highlightId)
                .orElseThrow(() -> new ResourceNotFoundException("Highlighted message not found"));

        if (msg.getStatus() != TipStatus.COMPLETED) {
            throw new IllegalStateException("Only COMPLETED highlights can be refunded");
        }

        // 1. Stripe Refund
        if (msg.getStripePaymentIntentId() != null) {
            com.stripe.param.RefundCreateParams params = com.stripe.param.RefundCreateParams.builder()
                    .setPaymentIntent(msg.getStripePaymentIntentId())
                    .putMetadata("moderator_id", moderator.getId().toString())
                    .putMetadata("type", reason)
                    .build();
            stripeClient.refunds().create(params);
            log.info("MODERATION: Stripe refund initiated for highlight {} (PI: {})", highlightId, msg.getStripePaymentIntentId());
        }

        // 2. Mark as refunded and moderated
        msg.setStatus(TipStatus.REFUNDED);
        msg.setModerated(true);
        msg.setModeratedBy(moderator);
        msg.setModeratedAt(java.time.Instant.now());
        msg.setModerationReason(reason);
        repository.save(msg);

        // 3. Reverse creator earnings
        if (msg.getStripePaymentIntentId() != null) {
            creatorEarningsService.reverseEarningByStripeId(msg.getStripePaymentIntentId());
        }

        log.info("MODERATION: Highlighted message {} refunded by {}. Reason: {}", highlightId, moderator.getEmail(), reason);

        auditService.logEvent(
                new UUID(0L, moderator.getId()),
                AuditService.REFUND_CREATED,
                "HIGHLIGHT",
                highlightId,
                Map.of(
                        "amount", msg.getAmount(),
                        "type", reason != null ? reason : "",
                        "creator", msg.getUserId().getId(),
                        "stripePaymentIntentId", msg.getStripePaymentIntentId() != null ? msg.getStripePaymentIntentId() : ""
                ),
                null,
                null
        );

        // Emit analytics event
        analyticsEventPublisher.publishEvent(
                AnalyticsEventType.HIGHLIGHTED_MESSAGE_REFUNDED,
                msg.getUserId(),
                Map.of(
                        "amount", msg.getAmount(),
                        "roomId", msg.getRoomId().getId(),
                        "creator", msg.getUserId().getId(),
                        "moderatorId", moderator.getId(),
                        "type", reason != null ? reason : ""
                )
        );

        // 4. Notify frontend to remove highlight (if not already removed) - Using creatorId routing
        messagingTemplate.convertAndSend("/exchange/amq.topic/chat." + msg.getRoomId().getCreator().getId(),
                com.joinlivora.backend.websocket.RealtimeMessage.of("REMOVE_HIGHLIGHT", Map.of(
                        "messageId", msg.getMessageId(),
                        "highlightId", highlightId
                ))
        );
    }

    private void broadcastHighlight(HighlightedMessage msg) {
        HighlightType type = msg.getHighlightType();
        // Broadcast the highlight event so the frontend can display it (Using creatorId routing)
        messagingTemplate.convertAndSend("/exchange/amq.topic/chat." + msg.getRoomId().getCreator().getId(), 
                com.joinlivora.backend.websocket.RealtimeMessage.of("HIGHLIGHT_MESSAGE", Map.of(
                        "messageId", msg.getMessageId(),
                        "content", msg.getContent(),
                        "creator", msg.getUserId().getId(),
                        "senderEmail", msg.getUserId().getEmail(),
                        "highlightType", type,
                        "amount", msg.getAmount(),
                        "priority", type.isPriority(),
                        "highlightDuration", type.getDurationSeconds()
                ))
        );
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

        // Check if IP or device has changed recently
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
