package com.joinlivora.backend.fraud.service;

import com.joinlivora.backend.fraud.dto.RiskDecisionResult;
import com.joinlivora.backend.fraud.model.RiskDecision;
import com.joinlivora.backend.fraud.model.RiskExplanation;
import com.joinlivora.backend.fraud.model.RiskSubjectType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RiskDecisionEngineTest {

    @Mock
    private RiskExplanationService riskExplanationService;

    @InjectMocks
    private RiskDecisionEngine riskDecisionEngine;

    @Test
    void evaluate_HighRisk_ShouldReturnBlock() {
        UUID subjectId = UUID.randomUUID();
        Map<String, Object> factors = Map.of("AML_HIGH_RISK", true);
        UUID explanationId = UUID.randomUUID();
        
        when(riskExplanationService.generateSystemExplanation(
                eq(RiskSubjectType.CREATOR), eq(subjectId), eq(90), eq(RiskDecision.BLOCK), eq(factors)))
                .thenReturn(RiskExplanation.builder().id(explanationId).build());

        RiskDecisionResult result = riskDecisionEngine.evaluate(RiskSubjectType.CREATOR, subjectId, 90, factors);

        assertEquals(RiskDecision.BLOCK, result.getDecision());
        assertEquals(explanationId, result.getExplanationId());
        assertEquals(90, result.getRiskScore());
    }

    @Test
    void evaluate_MediumRisk_ShouldReturnReview() {
        UUID subjectId = UUID.randomUUID();
        Map<String, Object> factors = Map.of("COLLUSION_DETECTED", true);
        UUID explanationId = UUID.randomUUID();
        
        when(riskExplanationService.generateSystemExplanation(
                eq(RiskSubjectType.CREATOR), eq(subjectId), eq(70), eq(RiskDecision.REVIEW), eq(factors)))
                .thenReturn(RiskExplanation.builder().id(explanationId).build());

        RiskDecisionResult result = riskDecisionEngine.evaluate(RiskSubjectType.CREATOR, subjectId, 70, factors);

        assertEquals(RiskDecision.REVIEW, result.getDecision());
        assertEquals(explanationId, result.getExplanationId());
    }

    @Test
    void evaluate_LowRisk_ShouldReturnLimit() {
        UUID subjectId = UUID.randomUUID();
        Map<String, Object> factors = Map.of("NEW_DEVICE", true);
        
        when(riskExplanationService.generateSystemExplanation(
                any(), any(), anyInt(), eq(RiskDecision.LIMIT), any()))
                .thenReturn(RiskExplanation.builder().id(UUID.randomUUID()).build());

        RiskDecisionResult result = riskDecisionEngine.evaluate(RiskSubjectType.CREATOR, subjectId, 45, factors);

        assertEquals(RiskDecision.LIMIT, result.getDecision());
    }

    @Test
    void evaluate_Safe_ShouldReturnAllow() {
        UUID subjectId = UUID.randomUUID();
        
        when(riskExplanationService.generateSystemExplanation(
                any(), any(), anyInt(), eq(RiskDecision.ALLOW), any()))
                .thenReturn(RiskExplanation.builder().id(UUID.randomUUID()).build());

        RiskDecisionResult result = riskDecisionEngine.evaluate(RiskSubjectType.CREATOR, subjectId, 10, null);

        assertEquals(RiskDecision.ALLOW, result.getDecision());
    }
}








