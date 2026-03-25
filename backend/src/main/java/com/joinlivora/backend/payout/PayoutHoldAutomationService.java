package com.joinlivora.backend.payout;

import com.joinlivora.backend.analytics.CreatorStats;
import com.joinlivora.backend.analytics.CreatorStatsRepository;
import com.joinlivora.backend.fraud.model.RiskProfile;
import com.joinlivora.backend.fraud.model.RiskSubjectType;
import com.joinlivora.backend.fraud.service.RiskProfileService;
import com.joinlivora.backend.payment.ChargebackHistoryService;
import com.joinlivora.backend.payout.dto.PayoutHoldDecision;
import com.joinlivora.backend.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service("payoutHoldAutomationService")
@RequiredArgsConstructor
@Slf4j
public class PayoutHoldAutomationService {

    private final PayoutHoldDecisionService decisionService;
    private final RiskProfileService riskProfileService;
    private final ChargebackHistoryService chargebackHistoryService;
    private final CreatorStatsRepository creatorStatsRepository;
    private final LegacyCreatorProfileRepository creatorProfileRepository;
    private final PayoutHoldPolicyRepository holdPolicyRepository;
    private final PayoutHoldAuditService holdAuditService;

    @Transactional
    public void evaluateAndApplyHold(User user) {
        UUID subjectId = new UUID(0L, user.getId());
        log.info("Automatically evaluating payout hold for creator: {}", user.getEmail());

        RiskProfile riskProfile = riskProfileService.generateRiskProfile(subjectId);
        
        // Fetch chargeback risk score and convert to a mock rate for decision service
        // In a real scenario, we would calculate actual rate (chargebacks / total transactions)
        int cbRiskScore = chargebackHistoryService.getChargebackRiskScore(user.getId());
        double chargebackRate = cbRiskScore / 10.0; 

        // Account age and earnings
        int accountAgeDays = 365; // Default to established if unknown
        BigDecimal totalEarnings = BigDecimal.ZERO;
        
        LegacyCreatorProfile profile = creatorProfileRepository.findByUser(user).orElse(null);
        if (profile != null) {
            creatorStatsRepository.findById(profile.getId()).ifPresent(stats -> {
                // totalEarnings is already in BigDecimal
            });
            // Re-fetch to be sure or use ifPresent properly
            totalEarnings = creatorStatsRepository.findById(profile != null ? profile.getId() : null)
                    .map(CreatorStats::getTotalNetEarnings)
                    .orElse(BigDecimal.ZERO);
        }

        PayoutHoldDecision decision = decisionService.decide(
                riskProfile.getRiskScore(), 
                chargebackRate, 
                accountAgeDays, 
                totalEarnings
        );

        applyDecision(user, decision);
    }

    private void applyDecision(User user, PayoutHoldDecision decision) {
        UUID subjectId = new UUID(0L, user.getId());
        
        PayoutHoldPolicy currentPolicy = holdPolicyRepository.findAllBySubjectIdAndSubjectTypeOrderByCreatedAtDesc(
                subjectId, RiskSubjectType.CREATOR)
                .stream()
                .filter(p -> p.getExpiresAt() != null && p.getExpiresAt().isAfter(Instant.now()))
                .findFirst().orElse(null);

        if (decision.getHoldLevel() == HoldLevel.NONE) {
            if (currentPolicy != null) {
                log.info("Automatic evaluation resulted in NONE, but active hold exists. Not releasing automatically.");
            }
            return;
        }

        // If new hold is longer than current, or no current hold, apply it
        Instant newExpiresAt = Instant.now().plus(decision.getHoldDays(), ChronoUnit.DAYS);
        if (currentPolicy == null || newExpiresAt.isAfter(currentPolicy.getExpiresAt())) {
            
            PayoutHoldPolicy policy = PayoutHoldPolicy.builder()
                    .subjectType(RiskSubjectType.CREATOR)
                    .subjectId(subjectId)
                    .holdLevel(decision.getHoldLevel())
                    .holdDays(decision.getHoldDays())
                    .reason(decision.getReason())
                    .expiresAt(newExpiresAt)
                    .build();

            holdPolicyRepository.save(policy);
            
            holdAuditService.logHoldApplied(RiskSubjectType.CREATOR, subjectId, currentPolicy,
                    decision.getHoldLevel(), decision.getHoldDays(), newExpiresAt, decision.getReason());
        }
    }
}
