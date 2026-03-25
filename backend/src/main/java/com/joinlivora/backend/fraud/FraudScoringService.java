package com.joinlivora.backend.fraud;

import com.joinlivora.backend.fraud.model.FraudDecision;
import com.joinlivora.backend.fraud.model.FraudRiskLevel;
import com.joinlivora.backend.fraud.model.FraudRiskResult;
import com.joinlivora.backend.fraud.model.FraudSignal;
import com.joinlivora.backend.fraud.repository.FraudDecisionRepository;
import com.joinlivora.backend.fraud.repository.FraudSignalRepository;
import com.joinlivora.backend.abuse.RestrictionService;
import com.joinlivora.backend.abuse.model.AbuseEvent;
import com.joinlivora.backend.abuse.model.AbuseEventType;
import com.joinlivora.backend.abuse.repository.AbuseEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service("fraudScoringService")
@Slf4j
public class FraudScoringService {

    private final FraudDecisionRepository fraudDecisionRepository;
    private final FraudSignalRepository fraudSignalRepository;
    private final com.joinlivora.backend.fraud.repository.RuleFraudSignalRepository ruleFraudSignalRepository;
    private final RestrictionService restrictionService;
    private final AbuseEventRepository abuseEventRepository;
    private final com.joinlivora.backend.user.UserService userService;
    private final com.joinlivora.backend.admin.service.AdminRealtimeEventService adminRealtimeEventService;

    public FraudScoringService(
            FraudDecisionRepository fraudDecisionRepository,
            FraudSignalRepository fraudSignalRepository,
            com.joinlivora.backend.fraud.repository.RuleFraudSignalRepository ruleFraudSignalRepository,
            @org.springframework.context.annotation.Lazy RestrictionService restrictionService,
            AbuseEventRepository abuseEventRepository,
            com.joinlivora.backend.user.UserService userService,
            com.joinlivora.backend.admin.service.AdminRealtimeEventService adminRealtimeEventService
    ) {
        this.fraudDecisionRepository = fraudDecisionRepository;
        this.fraudSignalRepository = fraudSignalRepository;
        this.ruleFraudSignalRepository = ruleFraudSignalRepository;
        this.restrictionService = restrictionService;
        this.abuseEventRepository = abuseEventRepository;
        this.userService = userService;
        this.adminRealtimeEventService = adminRealtimeEventService;
    }

    /**
     * Calculates a fraud risk score (0–100) based on various signals.
     *
     * @param userId                The ID of the creator
     * @param tipAmount             The amount of the tip
     * @param tipsInLast5Minutes    Number of tips the creator has sent in the last 5 minutes
     * @param totalAmountShortWindow Sum of tip amounts in a short window
     * @param userAccountAgeDays    Age of the creator account in days
     * @param ipOrDeviceChanged     Whether IP or device has changed recently
     * @param previousChargebacks   Number of previous chargebacks associated with this creator
     * @return A FraudRiskResult containing the score, risk level, and reasons
     */
    public FraudRiskResult calculateRisk(
            UUID userId,
            BigDecimal tipAmount,
            int tipsInLast5Minutes,
            BigDecimal totalAmountShortWindow,
            long userAccountAgeDays,
            boolean ipOrDeviceChanged,
            int previousChargebacks
    ) {
        log.debug("Calculating fraud risk for creator {}: tipsIn5m={}, totalAmt={}, ageDays={}, ipChanged={}, chargebacks={}",
                userId, tipsInLast5Minutes, totalAmountShortWindow, userAccountAgeDays, ipOrDeviceChanged, previousChargebacks);

        int score = 0;
        List<String> reasons = new ArrayList<>();

        // 1. Tip frequency per creator (Velocity)
        if (tipsInLast5Minutes > 10) {
            score += 50;
            reasons.add("High tip frequency: > 10 tips in 5 minutes (actual: " + tipsInLast5Minutes + ")");
            log.debug("Rule 1 (Velocity): +50 points (count: {})", tipsInLast5Minutes);
        } else if (tipsInLast5Minutes > 5) {
            score += 25;
            reasons.add("Elevated tip frequency: > 5 tips in 5 minutes (actual: " + tipsInLast5Minutes + ")");
            log.debug("Rule 1 (Velocity): +25 points (count: {})", tipsInLast5Minutes);
        }

        // 2. Total tip amount in short time window
        if (totalAmountShortWindow.compareTo(new BigDecimal("500")) > 0) {
            score += 60;
            reasons.add("High total tip amount in short window: > €500 (actual: €" + totalAmountShortWindow + ")");
            log.debug("Rule 2 (Amount): +60 points (total: {})", totalAmountShortWindow);
        } else if (totalAmountShortWindow.compareTo(new BigDecimal("200")) > 0) {
            score += 30;
            reasons.add("Elevated total tip amount in short window: > €200 (actual: €" + totalAmountShortWindow + ")");
            log.debug("Rule 2 (Amount): +30 points (total: {})", totalAmountShortWindow);
        }

        // 3. Repeated chargebacks
        if (previousChargebacks >= 2) {
            score += 100;
            reasons.add("Repeated chargebacks detected (actual: " + previousChargebacks + ")");
            log.debug("Rule 3 (Chargebacks): +100 points (count: {})", previousChargebacks);
        } else if (previousChargebacks == 1) {
            score += 50;
            reasons.add("Previous chargeback on record");
            log.debug("Rule 3 (Chargebacks): +50 points (count: {})", previousChargebacks);
        }

        // 4. IP / device changes
        if (ipOrDeviceChanged) {
            score += 30;
            reasons.add("IP or device change detected");
            log.debug("Rule 4 (IP/Device): +30 points");
        }

        // 5. Account age
        if (userAccountAgeDays < 1) {
            score += 40;
            reasons.add("New account: < 24 hours old (actual: " + userAccountAgeDays + " days)");
            log.debug("Rule 5 (Account Age): +40 points (age: {} days)", userAccountAgeDays);
        } else if (userAccountAgeDays < 7) {
            score += 15;
            reasons.add("Recent account: < 7 days old (actual: " + userAccountAgeDays + " days)");
            log.debug("Rule 5 (Account Age): +15 points (age: {} days)", userAccountAgeDays);
        }

        // Cap score at 100 and ensure it's not negative
        int finalScore = Math.max(0, Math.min(100, score));
        FraudRiskLevel riskLevel = FraudRiskLevel.fromScore(finalScore);

        log.debug("Fraud risk calculation completed for creator {}: finalScore={}, riskLevel={}, reasons={}",
                userId, finalScore, riskLevel, reasons);

        return new FraudRiskResult(riskLevel, finalScore, reasons);
    }

    /**
     * Records a fraud decision in the database for MEDIUM and HIGH risk levels.
     *
     * @param userId        The ID of the creator being evaluated
     * @param roomId        The ID of the stream room (optional)
     * @param relatedTipId  The ID of the related tip/transaction (may be null if blocked)
     * @param result       The result of the fraud risk calculation
     */
    @Transactional
    public void recordDecision(UUID userId, UUID roomId, Long relatedTipId, FraudRiskResult result) {
        log.debug("Recording fraud decision for creator {}: roomId={}, tipId={}, score={}, level={}",
                userId, roomId, relatedTipId, result.score(), result.level());

        if (result.level() == FraudRiskLevel.LOW && result.reasons().isEmpty()) {
            return;
        }

        FraudDecision decision = FraudDecision.builder()
                .userId(userId)
                .relatedTipId(relatedTipId)
                .score(result.score())
                .riskLevel(result.level())
                .reasons(String.join(", ", result.reasons()))
                .build();

        fraudDecisionRepository.save(decision);

        // Persistent FraudSignal for score >= MEDIUM
        if (result.level().ordinal() >= FraudRiskLevel.MEDIUM.ordinal()) {
            FraudSignal signal = FraudSignal.builder()
                    .userId(userId)
                    .roomId(roomId)
                    .score(result.score())
                    .riskLevel(result.level())
                    .reasons(String.join(", ", result.reasons()))
                    .build();
            fraudSignalRepository.save(signal);
            log.info("Stored persistent FraudSignal for creator {}: score={}, level={}", userId, result.score(), result.level());
        }

        // Integrate with RestrictionService
        boolean escalated = restrictionService.applyRestriction(userId, result.score(), String.join(", ", result.reasons()));

        if (escalated) {
            log.info("ENFORCEMENT: Restriction escalated for creator {} based on fraud score {}", userId, result.score());
            AbuseEvent escalationEvent = AbuseEvent.builder()
                    .userId(userId)
                    .eventType(AbuseEventType.RESTRICTION_ESCALATED)
                    .description("Restriction escalated due to fraud score: " + result.score() + ". Reasons: " + String.join(", ", result.reasons()))
                    .build();
            abuseEventRepository.save(escalationEvent);
        }
    }

    /**
     * Records a specific fraud signal for tipping patterns.
     */
    @Transactional
    public void recordSignal(com.joinlivora.backend.fraud.model.FraudSignalType type, Long tipperId, Long creatorId, BigDecimal amount) {
        log.warn("FRAUD_SIGNAL: Type={}, Tipper={}, Creator={}, Amount={}", type, tipperId, creatorId, amount);

        // Broadcast to admin feed
        try {
            String username = userService.getById(tipperId).getUsername();
            int score = (type == com.joinlivora.backend.fraud.model.FraudSignalType.NEW_ACCOUNT_TIPPING_HIGH) ? 75 :
                        (type == com.joinlivora.backend.fraud.model.FraudSignalType.RAPID_TIP_REPEATS) ? 70 : 40;
            adminRealtimeEventService.broadcastFraudSignal(username, type.name(), score);
        } catch (Exception e) {
            log.warn("Failed to broadcast fraud signal to admin feed: {}", e.getMessage());
        }

        if (type == com.joinlivora.backend.fraud.model.FraudSignalType.NEW_ACCOUNT_TIP_CLUSTER) {
            if (creatorId != null) {
                // Save persistent rule-based signal for the creator as the primary target
                com.joinlivora.backend.fraud.model.RuleFraudSignal creatorSignal = com.joinlivora.backend.fraud.model.RuleFraudSignal.builder()
                        .userId(creatorId)
                        .creatorId(creatorId)
                        .score(85)
                        .riskLevel(com.joinlivora.backend.fraud.model.FraudDecisionLevel.HIGH)
                        .source(com.joinlivora.backend.fraud.model.FraudSource.PAYMENT)
                        .type(type)
                        .reason(String.format("New account tipping cluster detected: 5+ accounts < 24h tipped in 10m window. Triggered by tipper %d", tipperId))
                        .createdAt(java.time.Instant.now())
                        .build();
                ruleFraudSignalRepository.save(creatorSignal);

                // Increase creator risk score
                recordDecision(
                        new java.util.UUID(0L, creatorId),
                        null,
                        null,
                        new FraudRiskResult(FraudRiskLevel.HIGH, 85, java.util.List.of("NEW_ACCOUNT_TIP_CLUSTER: 5+ new accounts in 10m"))
                );
            }
            return; // Skip standard tipper scoring for this cluster event
        }

        FraudRiskLevel riskLevel = FraudRiskLevel.MEDIUM;
        int score = 40;

        if (type == com.joinlivora.backend.fraud.model.FraudSignalType.NEW_ACCOUNT_TIPPING_HIGH) {
            riskLevel = FraudRiskLevel.HIGH;
            score = 75;
        } else if (type == com.joinlivora.backend.fraud.model.FraudSignalType.RAPID_TIP_REPEATS) {
            riskLevel = FraudRiskLevel.HIGH;
            score = 70;
        }

        // Save persistent rule-based signal
        com.joinlivora.backend.fraud.model.RuleFraudSignal signal = com.joinlivora.backend.fraud.model.RuleFraudSignal.builder()
                .userId(tipperId)
                .creatorId(creatorId)
                .score(score)
                .riskLevel(riskLevel == FraudRiskLevel.HIGH ? com.joinlivora.backend.fraud.model.FraudDecisionLevel.HIGH : com.joinlivora.backend.fraud.model.FraudDecisionLevel.MEDIUM)
                .source(com.joinlivora.backend.fraud.model.FraudSource.PAYMENT)
                .type(type)
                .reason(String.format("Suspicious tipping pattern: %s. creatorId=%d, amount=%s", type.name(), creatorId, amount))
                .createdAt(java.time.Instant.now())
                .build();
        ruleFraudSignalRepository.save(signal);

        // Increase risk score for the tipper
        recordDecision(
                new java.util.UUID(0L, tipperId),
                null,
                null,
                new FraudRiskResult(riskLevel, score, java.util.List.of("SUSPICIOUS_TIP_PATTERN: type=" + type.name() + ", creatorId=" + creatorId))
        );

        // Enhance fraud scoring for new account tipping: increase creator risk score
        if (type == com.joinlivora.backend.fraud.model.FraudSignalType.NEW_ACCOUNT_TIPPING_HIGH && creatorId != null) {
            recordDecision(
                    new java.util.UUID(0L, creatorId),
                    null,
                    null,
                    new FraudRiskResult(FraudRiskLevel.LOW, 20, java.util.List.of("COORDINATED_TIPPING_FARM_TARGET: type=" + type.name() + ", tipperId=" + tipperId))
            );
        }
    }
}
