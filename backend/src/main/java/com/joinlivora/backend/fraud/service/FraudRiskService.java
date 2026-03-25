package com.joinlivora.backend.fraud.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.joinlivora.backend.fraud.model.FraudRiskAssessment;
import com.joinlivora.backend.fraud.model.RiskLevel;
import com.joinlivora.backend.fraud.repository.FraudRiskAssessmentRepository;
import com.joinlivora.backend.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service("fraudRiskService")
@RequiredArgsConstructor
@Slf4j
public class FraudRiskService {

    private final List<FraudRiskRule> rules;
    private final FraudRiskAssessmentRepository assessmentRepository;
    private final RiskDecisionAuditService auditService;
    private final ObjectMapper objectMapper;

    /**
     * Calculates the fraud risk for a creator based on the provided context and persists the assessment.
     *
     * @param user              The creator to evaluate
     * @param paymentAmount     The amount of the current transaction
     * @param ipAddress         The IP address of the request
     * @param country           The country code derived from the IP
     * @param deviceFingerprint The device fingerprint hash
     * @return The persisted FraudRiskAssessment
     */
    @Transactional
    public FraudRiskAssessment calculateRisk(User user, BigDecimal paymentAmount, String ipAddress, String country, String deviceFingerprint) {
        log.info("Performing fraud risk assessment for creator: {}", user.getEmail());

        Map<String, Object> context = new HashMap<>();
        context.put("paymentAmount", paymentAmount);
        context.put("ipAddress", ipAddress);
        context.put("country", country);
        context.put("deviceFingerprint", deviceFingerprint);

        Map<String, Integer> factors = new HashMap<>();
        int totalScore = 0;

        for (FraudRiskRule rule : rules) {
            int ruleScore = rule.evaluate(user, context);
            factors.put(rule.getName(), ruleScore);
            totalScore += ruleScore;
        }

        int finalScore = Math.min(100, totalScore);
        RiskLevel riskLevel = RiskLevel.fromScore(finalScore);

        String reasons;
        try {
            reasons = objectMapper.writeValueAsString(factors);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize fraud risk factors for creator {}", user.getId(), e);
            reasons = factors.toString();
        }

        FraudRiskAssessment assessment = FraudRiskAssessment.builder()
                .userId(new UUID(0L, user.getId()))
                .score(finalScore)
                .riskLevel(riskLevel)
                .reasons(reasons)
                .build();

        assessment = assessmentRepository.save(assessment);
        log.info("Risk assessment completed for creator {}: score={}, level={}", user.getEmail(), finalScore, riskLevel);

        auditService.logDecision(
                assessment.getUserId(),
                null,
                "FRAUD_EVALUATION",
                riskLevel,
                finalScore,
                "SYSTEM",
                riskLevel == RiskLevel.HIGH ? "FLAG_FOR_REVIEW" : "ALLOW",
                reasons
        );

        return assessment;
    }
}
