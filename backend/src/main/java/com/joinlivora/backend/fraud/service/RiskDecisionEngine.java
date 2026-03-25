package com.joinlivora.backend.fraud.service;

import com.joinlivora.backend.fraud.dto.RiskDecisionResult;
import com.joinlivora.backend.fraud.model.RiskDecision;
import com.joinlivora.backend.fraud.model.RiskExplanation;
import com.joinlivora.backend.fraud.model.RiskProfile;
import com.joinlivora.backend.fraud.model.RiskSubjectType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service("riskDecisionEngine")
@RequiredArgsConstructor
@Slf4j
public class RiskDecisionEngine {

    private final RiskExplanationService riskExplanationService;

    /**
     * Evaluates risk from a RiskProfile and returns a decision with a persisted explanation.
     */
    public RiskDecisionResult evaluate(RiskSubjectType subjectType, RiskProfile profile) {
        return evaluate(subjectType, profile.getUserId(), profile.getRiskScore(), profile.getFactors());
    }

    /**
     * Evaluates risk and returns a decision with a persisted explanation.
     */
    public RiskDecisionResult evaluate(RiskSubjectType subjectType, UUID subjectId, int riskScore, Map<String, Object> factors) {
        RiskDecision decision = determineDecision(riskScore);
        
        RiskExplanation explanation = riskExplanationService.generateSystemExplanation(
                subjectType, subjectId, riskScore, decision, factors);

        log.info("Risk evaluation completed for {} {}: Decision={}, Score={}, ExplanationId={}", 
                subjectType, subjectId, decision, riskScore, explanation.getId());

        return RiskDecisionResult.builder()
                .decision(decision)
                .explanationId(explanation.getId())
                .riskScore(riskScore)
                .build();
    }

    private RiskDecision determineDecision(int score) {
        if (score >= 80) {
            return RiskDecision.BLOCK;
        } else if (score >= 60) {
            return RiskDecision.REVIEW;
        } else if (score >= 30) {
            return RiskDecision.LIMIT;
        } else {
            return RiskDecision.ALLOW;
        }
    }
}
