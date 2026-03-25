package com.joinlivora.backend.monetization;

import com.joinlivora.backend.analytics.AnalyticsEventPublisher;
import com.joinlivora.backend.analytics.AnalyticsEventType;
import com.joinlivora.backend.chat.SlowModeBypassService;
import com.joinlivora.backend.chat.SlowModeBypassSource;
import com.joinlivora.backend.abuse.AbuseDetectionService;
import com.joinlivora.backend.chargeback.ChargebackService;
import com.joinlivora.backend.exception.ResourceNotFoundException;
import com.joinlivora.backend.fraud.exception.HighFraudRiskException;
import com.joinlivora.backend.fraud.FraudScoringService;
import com.joinlivora.backend.fraud.model.FraudRiskResult;
import com.joinlivora.backend.monetization.dto.SuperTipResponse;
import com.joinlivora.backend.aml.service.AMLRulesEngine;
import com.joinlivora.backend.audit.service.AuditService;
import com.joinlivora.backend.payout.CreatorEarningsService;
import com.joinlivora.backend.streaming.Stream;
import com.joinlivora.backend.token.TokenWalletService;
import com.joinlivora.backend.wallet.WalletTransactionType;
import com.joinlivora.backend.user.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class SuperTipService {

    private final SuperTipRepository superTipRepository;
    private final TipRepository tipRepository;
    private final TokenWalletService tokenWalletService;
    private final CreatorEarningsService creatorEarningsService;
    private final com.joinlivora.backend.streaming.StreamRepository streamRepository;
    private final TipValidationService tipValidationService;
    private final AnalyticsEventPublisher analyticsEventPublisher;
    private final SlowModeBypassService slowModeBypassService;
    private final com.joinlivora.backend.payment.PaymentService paymentService;
    private final AMLRulesEngine amlRulesEngine;
    private final AuditService auditService;
    private final FraudScoringService fraudRiskService;
    private final ChargebackService chargebackService;
    private final AbuseDetectionService abuseDetectionService;
    private final com.joinlivora.backend.abuse.RestrictionService restrictionService;
    private final com.joinlivora.backend.fraud.service.EnforcementService enforcementService;

    public SuperTipService(
            SuperTipRepository superTipRepository,
            TipRepository tipRepository,
            TokenWalletService tokenWalletService,
            @org.springframework.context.annotation.Lazy CreatorEarningsService creatorEarningsService,
            com.joinlivora.backend.streaming.StreamRepository streamRepository,
            TipValidationService tipValidationService,
            AnalyticsEventPublisher analyticsEventPublisher,
            SlowModeBypassService slowModeBypassService,
            @org.springframework.context.annotation.Lazy com.joinlivora.backend.payment.PaymentService paymentService,
            AMLRulesEngine amlRulesEngine,
            AuditService auditService,
            FraudScoringService fraudRiskService,
            ChargebackService chargebackService,
            AbuseDetectionService abuseDetectionService,
            com.joinlivora.backend.abuse.RestrictionService restrictionService,
            com.joinlivora.backend.fraud.service.EnforcementService enforcementService) {
        this.superTipRepository = superTipRepository;
        this.tipRepository = tipRepository;
        this.tokenWalletService = tokenWalletService;
        this.creatorEarningsService = creatorEarningsService;
        this.streamRepository = streamRepository;
        this.tipValidationService = tipValidationService;
        this.analyticsEventPublisher = analyticsEventPublisher;
        this.slowModeBypassService = slowModeBypassService;
        this.paymentService = paymentService;
        this.amlRulesEngine = amlRulesEngine;
        this.auditService = auditService;
        this.fraudRiskService = fraudRiskService;
        this.chargebackService = chargebackService;
        this.abuseDetectionService = abuseDetectionService;
        this.restrictionService = restrictionService;
        this.enforcementService = enforcementService;
    }

    @Value("${livora.supertips.slow-mode-bypass-seconds:300}")
    private int bypassDurationSeconds;

    /**
     * Processes a SuperTip.
     * Validates amount against HighlightLevel, deducts tokens, credits creator, and persists the record.
     */
    @Transactional
    public SuperTipResponse sendSuperTip(User viewer, UUID roomId, long amountTokens, String message, String clientRequestId, String ipAddress, String fingerprintHash) {
        BigDecimal amountCurrency = BigDecimal.valueOf(amountTokens).multiply(new BigDecimal("0.01"));
        restrictionService.validateTippingAccess(new UUID(0L, viewer.getId()), amountCurrency);
        com.joinlivora.backend.fraud.model.RiskLevel riskLevel = paymentService.checkPaymentLock(viewer, amountCurrency, ipAddress, null, null, fingerprintHash);

        abuseDetectionService.checkRapidTipping(new UUID(0L, viewer.getId()), ipAddress);

        FraudRiskResult risk = calculateFraudRisk(viewer, amountCurrency);
        com.joinlivora.backend.fraud.model.RiskLevel maxRiskLevel = riskLevel;
        if (risk.level().ordinal() > (riskLevel != null ? riskLevel.ordinal() : -1)) {
            maxRiskLevel = com.joinlivora.backend.fraud.model.RiskLevel.valueOf(risk.level().name());
        }

        if (maxRiskLevel == com.joinlivora.backend.fraud.model.RiskLevel.CRITICAL) {
            log.error("Blocking super tip due to CRITICAL fraud risk for creator {}: score={}, reasons={}",
                    viewer.getEmail(), risk.score(), risk.reasons());
            fraudRiskService.recordDecision(new UUID(0L, viewer.getId()), roomId, null, risk);
            enforcementService.recordFraudIncident(new UUID(0L, viewer.getId()), "CRITICAL_FRAUD_RISK: " + String.join(", ", risk.reasons()), Map.of("score", risk.score(), "tokens", amountTokens));
            throw new HighFraudRiskException(risk.score(), risk.reasons());
        }

        if (maxRiskLevel == com.joinlivora.backend.fraud.model.RiskLevel.HIGH) {
            log.warn("Allowing super tip but marking for review due to HIGH fraud risk for creator {}: score={}, reasons={}",
                    viewer.getEmail(), risk.score(), risk.reasons());
        }

        // 0. Idempotency check
        if (clientRequestId != null) {
            java.util.Optional<SuperTip> existing = superTipRepository.findByClientRequestId(clientRequestId);
            if (existing.isPresent()) {
                log.info("MONETIZATION: Duplicate SuperTip request {} from {}. Returning existing record.", clientRequestId, viewer.getEmail());
                SuperTip st = existing.get();
                SuperTipResponse response = SuperTipResponse.builder()
                        .id(st.getId())
                        .senderId(viewer.getId())
                        .senderUsername(viewer.getUsername())
                        .senderEmail(viewer.getEmail())
                        .creatorEmail(st.getCreatorUserId().getEmail())
                        .roomId(roomId)
                        .amount(st.getAmount())
                        .message(st.getMessage())
                        .highlightLevel(st.getHighlightLevel())
                        .durationSeconds(st.getDurationSeconds())
                        .createdAt(st.getCreatedAt())
                        .build();
                response.setSuccess(true);
                return response;
            }
        }

        // 1. Validate room
        Stream room = streamRepository.findById(roomId)
                .orElseGet(() -> streamRepository.findByMediasoupRoomId(roomId)
                        .orElseThrow(() -> new ResourceNotFoundException("Active unified stream not found for roomId: " + roomId)));

        if (!room.isLive()) {
            throw new IllegalStateException("Cannot send SuperTip: Stream is not live");
        }

        // 2. Determine and validate highlight level
        // Convert tokens to currency value for HighlightLevel check (1 token = 0.01)
        HighlightLevel level = HighlightLevel.fromAmount(amountCurrency);
        
        if (level == null) {
            // Find the minimum level's minimum amount in tokens
            long minTokens = HighlightLevel.BASIC.getMinimumAmount().multiply(new BigDecimal("100")).longValue();
            throw new com.joinlivora.backend.exception.SuperTipException(
                    com.joinlivora.backend.monetization.dto.SuperTipErrorCode.INVALID_HIGHLIGHT_LEVEL,
                    "Minimum SuperTip amount is " + minTokens + " tokens"
            );
        }

        // 3. SuperTip specific validation (rate limit, room cooldown, common checks)
        try {
            tipValidationService.validateSuperTip(viewer, roomId);
        } catch (RuntimeException e) {
            if (e.getMessage().contains("rate limit") || e.getMessage().contains("cooldown")) {
                throw new com.joinlivora.backend.exception.SuperTipException(
                        com.joinlivora.backend.monetization.dto.SuperTipErrorCode.RATE_LIMITED,
                        e.getMessage()
                );
            }
            throw e;
        }

        // 4. Validate sender balance
        long availableBalance = tokenWalletService.getAvailableBalance(viewer.getId());
        if (availableBalance < amountTokens) {
            throw new com.joinlivora.backend.exception.SuperTipException(
                    com.joinlivora.backend.monetization.dto.SuperTipErrorCode.INSUFFICIENT_BALANCE,
                    "Insufficient tokens for SuperTip"
            );
        }

        // 5. Deduct tokens
        tokenWalletService.deductTokens(viewer.getId(), amountTokens, WalletTransactionType.TIP, "SuperTip to room " + roomId);

        // 6. Credit creator earnings
        User creator = room.getCreator();
        creatorEarningsService.recordTokenTipEarning(viewer, creator, amountTokens, roomId, maxRiskLevel);

        // 7. Persist SuperTip
        SuperTip superTip = SuperTip.builder()
                .senderUserId(viewer)
                .creatorUserId(creator)
                .roomId(room)
                .amount(BigDecimal.valueOf(amountTokens))
                .message(message)
                .highlightLevel(level)
                .durationSeconds(level.getDisplayDurationSeconds())
                .clientRequestId(clientRequestId)
                .status(TipStatus.COMPLETED)
                .build();
        
        if (maxRiskLevel == com.joinlivora.backend.fraud.model.RiskLevel.MEDIUM || maxRiskLevel == com.joinlivora.backend.fraud.model.RiskLevel.HIGH) {
            log.info("Marking super tip as PENDING_REVIEW due to {} fraud risk for creator {}: score={}, reasons={}",
                    maxRiskLevel, viewer.getEmail(), risk.score(), risk.reasons());
            superTip.setStatus(TipStatus.PENDING_REVIEW);
        }
        
        SuperTip saved = superTipRepository.save(superTip);
        fraudRiskService.recordDecision(new UUID(0L, viewer.getId()), roomId, saved.getId() != null ? saved.getId().getLeastSignificantBits() : null, risk);

        auditService.logEvent(
                new UUID(0L, viewer.getId()),
                AuditService.TIP_CREATED,
                "TIP",
                saved.getId(),
                Map.of(
                        "amount", amountTokens,
                        "currency", "TOKEN",
                        "roomId", roomId,
                        "type", "SUPERTIP",
                        "highlightLevel", level.name(),
                        "clientRequestId", clientRequestId != null ? clientRequestId : ""
                ),
                ipAddress,
                null
        );

        // 7.5 Grant slow mode bypass for PREMIUM and ULTRA levels
        if (level.ordinal() >= HighlightLevel.PREMIUM.ordinal()) {
            slowModeBypassService.grantBypass(viewer, room, bypassDurationSeconds, SlowModeBypassSource.SUPERTIP);
        }

        // 8. Emit analytics event
        analyticsEventPublisher.publishEvent(
                AnalyticsEventType.SUPERTIP_SENT,
                viewer,
                Map.of(
                        "highlightLevel", level.name(),
                        "amount", amountTokens,
                        "roomId", roomId,
                        "creatorUserId", creator.getId(),
                        "senderUserId", viewer.getId()
                )
        );

        log.info("MONETIZATION: SuperTip of {} tokens from {} to {} in room {} (Level: {})",
                amountTokens, viewer.getEmail(), creator.getEmail(), roomId, level);

        // Re-evaluate AML risk for creator
        amlRulesEngine.evaluateRules(creator, BigDecimal.ZERO);

        // 9. Return Result DTO
        SuperTipResponse response = SuperTipResponse.builder()
                .id(saved.getId())
                .senderId(viewer.getId())
                .senderUsername(viewer.getUsername())
                .senderEmail(viewer.getEmail())
                .creatorEmail(creator.getEmail())
                .roomId(roomId)
                .amount(saved.getAmount())
                .message(saved.getMessage())
                .highlightLevel(saved.getHighlightLevel())
                .durationSeconds(saved.getDurationSeconds())
                .createdAt(saved.getCreatedAt())
                .build();
        response.setSuccess(true);
        return response;
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
