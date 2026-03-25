package com.joinlivora.backend.payout;

import com.joinlivora.backend.fraud.dto.RiskDecisionResult;
import com.joinlivora.backend.fraud.model.FraudSignalType;
import com.joinlivora.backend.fraud.model.RiskDecision;
import com.joinlivora.backend.fraud.model.RiskSubjectType;
import com.joinlivora.backend.fraud.service.RiskDecisionEngine;
import com.joinlivora.backend.monetization.CreatorTrustService;
import com.joinlivora.backend.payment.AutoFreezePolicyService;
import com.joinlivora.backend.payout.dto.AmlResult;
import com.joinlivora.backend.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Service("payoutAbuseDetectionService")
@RequiredArgsConstructor
@Slf4j
public class PayoutAbuseDetectionService {

    private final AmlRuleEngineService amlRuleEngineService;
    private final PayoutRiskRepository payoutRiskRepository;
    private final AutoFreezePolicyService autoFreezePolicyService;
    private final AmlAuditService amlAuditService;
    private final CreatorTrustService creatorTrustService;
    private final RiskDecisionEngine riskDecisionEngine;
    private final org.springframework.beans.factory.ObjectProvider<PayoutAbuseDetectionService> selfProvider;

    public void detect(User user, BigDecimal amount) {
        // Run collusion detection before payout
        creatorTrustService.evaluateTrust(user);

        AmlResult result = amlRuleEngineService.analyze(user, amount);

        // Map AmlResult to factors
        java.util.Map<String, Object> factors = new java.util.HashMap<>();
        if (result.getTriggeredRules() != null) {
            result.getTriggeredRules().forEach(r -> factors.put(r, true));
        }
        
        RiskDecisionResult decisionResult = riskDecisionEngine.evaluate(
                RiskSubjectType.CREATOR, new java.util.UUID(0L, user.getId()), result.getRiskScore(), factors);

        PayoutAbuseDetectionService self = selfProvider.getIfAvailable();
        if (self != null) {
            self.savePayoutRisk(user, result);
        } else {
            savePayoutRisk(user, result);
        }

        boolean blocked = decisionResult.getDecision() == RiskDecision.BLOCK;
        amlAuditService.audit(user, amount, result, blocked);

        if (decisionResult.getDecision() == RiskDecision.BLOCK) {
            if (result.getRiskScore() >= 90) {
                log.warn("SECURITY: AML risk score >= 90 for creator {}. Suspending account. ExplanationId: {}",
                        user.getEmail(), decisionResult.getExplanationId());
                autoFreezePolicyService.suspendAccount(user, 
                        "Extreme AML Risk: " + result.getRiskScore() + ". Triggered rules: " + String.join(", ", result.getTriggeredRules()),
                        FraudSignalType.AML_HIGH_RISK);
                throw new org.springframework.security.access.AccessDeniedException("Payout blocked and account suspended due to extreme AML risk.");
            } else {
                log.warn("SECURITY: AML risk score >= 70 for creator {}. Blocking payout. ExplanationId: {}",
                        user.getEmail(), decisionResult.getExplanationId());
                throw new org.springframework.security.access.AccessDeniedException("Payout blocked due to high AML risk score.");
            }
        }
    }

    @Transactional
    public void override(User user, int riskScore, String reason, User admin) {
        log.info("Admin {} overriding AML risk for creator {} to score: {}. Reason: {}",
                admin.getEmail(), user.getEmail(), riskScore, reason);

        PayoutRisk overrideRecord = PayoutRisk.builder()
                .userId(user.getId())
                .riskScore(riskScore)
                .reasons("ADMIN_OVERRIDE: " + reason + " (by " + admin.getEmail() + ")")
                .lastEvaluatedAt(Instant.now())
                .build();

        payoutRiskRepository.save(overrideRecord);
    }

    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void savePayoutRisk(User user, AmlResult result) {
        PayoutRisk risk = PayoutRisk.builder()
                .userId(user.getId())
                .riskScore(result.getRiskScore())
                .reasons(String.join(", ", result.getTriggeredRules()))
                .lastEvaluatedAt(Instant.now())
                .build();

        payoutRiskRepository.save(risk);
    }
}
