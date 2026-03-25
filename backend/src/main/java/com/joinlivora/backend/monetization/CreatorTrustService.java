package com.joinlivora.backend.monetization;

import com.joinlivora.backend.fraud.dto.RiskDecisionResult;
import com.joinlivora.backend.fraud.model.FraudDecisionLevel;
import com.joinlivora.backend.fraud.model.FraudSignalType;
import com.joinlivora.backend.fraud.model.FraudSource;
import com.joinlivora.backend.fraud.model.RiskSubjectType;
import com.joinlivora.backend.fraud.service.FraudDetectionService;
import com.joinlivora.backend.fraud.service.RiskDecisionEngine;
import com.joinlivora.backend.reputation.model.ReputationEventSource;
import com.joinlivora.backend.reputation.model.ReputationEventType;
import com.joinlivora.backend.reputation.service.ReputationEventService;
import com.joinlivora.backend.monetization.dto.CollusionResult;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import com.joinlivora.backend.user.UserStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service("creatorTrustService")
@RequiredArgsConstructor
@Slf4j
public class CreatorTrustService {

    private final CollusionDetectionService collusionDetectionService;
    private final UserRepository userRepository;
    private final FraudDetectionService fraudDetectionService;
    private final CreatorCollusionRecordRepository creatorCollusionRecordRepository;
    private final CollusionAuditService collusionAuditService;
    private final ReputationEventService reputationEventService;
    private final RiskDecisionEngine riskDecisionEngine;

    @Transactional
    public void evaluateTrust(User creator) {
        log.info("Evaluating trust for creator: {}", creator.getEmail());
        
        UUID creatorUuid = new UUID(0L, creator.getId());
        CollusionResult result = collusionDetectionService.detectCollusion(creatorUuid);
        int score = result.getCollusionScore();

        // Audit the detection result
        collusionAuditService.audit(creator, result);

        // Persist collusion record
        saveCollusionRecord(creatorUuid, result);

        if (score >= 60) {
            applyCollusionRules(creator, result);
        }
    }

    @Transactional
    public void override(User creator, int score, String reason, User admin) {
        log.info("Admin {} overriding collusion risk for creator {} to score: {}. Reason: {}", 
                admin.getEmail(), creator.getEmail(), score, reason);

        CreatorCollusionRecord record = CreatorCollusionRecord.builder()
                .creatorId(new UUID(0L, creator.getId()))
                .score(score)
                .detectedPattern("ADMIN_OVERRIDE: " + reason + " (by " + admin.getEmail() + ")")
                .build();

        creatorCollusionRecordRepository.save(record);
    }

    private void saveCollusionRecord(UUID creatorId, CollusionResult result) {
        CreatorCollusionRecord record = CreatorCollusionRecord.builder()
                .creatorId(creatorId)
                .score(result.getCollusionScore())
                .detectedPattern(String.join(", ", result.getPatternTypes()))
                .build();
        creatorCollusionRecordRepository.save(record);
    }

    private void applyCollusionRules(User creator, CollusionResult result) {
        int score = result.getCollusionScore();
        String reason = "Collusion score: " + score + ". Patterns: " + String.join(", ", result.getPatternTypes());

        // Map CollusionResult to factors
        java.util.Map<String, Object> factors = new java.util.HashMap<>();
        if (result.getPatternTypes() != null) {
            result.getPatternTypes().forEach(p -> factors.put(p, true));
        }
        if (score >= 60) {
            factors.put("COLLUSION_DETECTED", true);
        }

        RiskDecisionResult decisionResult = riskDecisionEngine.evaluate(
                RiskSubjectType.CREATOR, new java.util.UUID(0L, creator.getId()), score, factors);

        log.info("Collusion rules applied for creator {}. ExplanationId: {}", creator.getEmail(), decisionResult.getExplanationId());

        // Rule: collusionScore >= 95 -> manual review
        if (score >= 95) {
            log.warn("SECURITY: Extreme collusion detected for creator {}. Setting status to MANUAL_REVIEW.", creator.getEmail());
            if (updateStatus(creator, UserStatus.MANUAL_REVIEW, reason)) {
                collusionAuditService.recordRestriction(creator, "MANUAL_REVIEW", score);
            }
        }
        // Rule: collusionScore >= 80 -> restrict payouts
        else if (score >= 80) {
            log.warn("SECURITY: High collusion detected for creator {}. Freezing payouts.", creator.getEmail());
            if (updateStatus(creator, UserStatus.PAYOUTS_FROZEN, reason)) {
                collusionAuditService.recordRestriction(creator, "PAYOUTS_FROZEN", score);
            }
        }
        // Rule: collusionScore >= 60 -> reduce trust
        else {
            log.warn("SECURITY: Collusion detected for creator {}. Reducing trust score.", creator.getEmail());
            reduceTrust(creator, reason);
            collusionAuditService.recordRestriction(creator, "TRUST_REDUCTION", score);
        }

        // Emit fraud signal for any collusion detection >= 60
        fraudDetectionService.logFraudSignal(
                creator.getId(),
                score >= 80 ? FraudDecisionLevel.HIGH : FraudDecisionLevel.MEDIUM,
                FraudSource.SYSTEM,
                FraudSignalType.COLLUSION_DETECTED,
                reason
        );

        reputationEventService.recordEvent(
                new UUID(0L, creator.getId()),
                ReputationEventType.FRAUD_FLAG,
                score >= 80 ? -30 : -10,
                ReputationEventSource.SYSTEM,
                Map.of("collusionScore", score, "type", reason)
        );
    }

    private boolean updateStatus(User user, UserStatus newStatus, String reason) {
        if (user.getStatus().ordinal() < newStatus.ordinal()) {
            user.setStatus(newStatus);
            userRepository.save(user);
            log.info("Updated status for creator {} to {} due to collusion.", user.getEmail(), newStatus);
            return true;
        }
        return false;
    }

    private void reduceTrust(User user, String reason) {
        // Simple logic: reduce trust score by 20 points, min 0.
        int currentTrust = user.getTrustScore();
        user.setTrustScore(Math.max(0, currentTrust - 20));
        userRepository.save(user);
        log.info("Reduced trust score for creator {} to {}. Reason: {}", user.getEmail(), user.getTrustScore(), reason);
    }
}
