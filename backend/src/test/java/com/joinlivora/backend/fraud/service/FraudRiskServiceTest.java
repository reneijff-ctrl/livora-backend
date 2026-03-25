package com.joinlivora.backend.fraud.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joinlivora.backend.fraud.model.FraudRiskAssessment;
import com.joinlivora.backend.fraud.model.RiskLevel;
import com.joinlivora.backend.fraud.repository.FraudRiskAssessmentRepository;
import com.joinlivora.backend.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FraudRiskServiceTest {

    private FraudRiskService fraudRiskService;

    @Mock
    private FraudRiskAssessmentRepository assessmentRepository;

    @Mock
    private RiskDecisionAuditService auditService;

    @Mock
    private FraudRiskRule rule1;

    @Mock
    private FraudRiskRule rule2;

    private User user;
    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setEmail("test@example.com");

        List<FraudRiskRule> rules = Arrays.asList(rule1, rule2);
        fraudRiskService = new FraudRiskService(rules, assessmentRepository, auditService, objectMapper);

        when(rule1.getName()).thenReturn("rule1");
        when(rule2.getName()).thenReturn("rule2");
    }

    @Test
    void calculateRisk_ShouldAggregateScoresAndStoreAssessment() {
        // Given
        when(rule1.evaluate(any(), any())).thenReturn(20);
        when(rule2.evaluate(any(), any())).thenReturn(15);
        when(assessmentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        FraudRiskAssessment result = fraudRiskService.calculateRisk(
                user,
                new BigDecimal("100.00"),
                "127.0.0.1",
                "US",
                "device-123"
        );

        // Then
        assertNotNull(result);
        assertEquals(35, result.getScore());
        assertEquals(RiskLevel.LOW, result.getRiskLevel());
        assertEquals(new UUID(0L, 1L), result.getUserId());
        assertTrue(result.getReasons().contains("\"rule1\":20"));
        assertTrue(result.getReasons().contains("\"rule2\":15"));

        verify(assessmentRepository, times(1)).save(any(FraudRiskAssessment.class));
        verify(auditService, times(1)).logDecision(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void calculateRisk_ShouldCapScoreAt100() {
        // Given
        when(rule1.evaluate(any(), any())).thenReturn(60);
        when(rule2.evaluate(any(), any())).thenReturn(50);
        when(assessmentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        FraudRiskAssessment result = fraudRiskService.calculateRisk(
                user,
                BigDecimal.ZERO,
                "0.0.0.0",
                "XX",
                "fp"
        );

        // Then
        assertEquals(100, result.getScore());
        assertEquals(RiskLevel.CRITICAL, result.getRiskLevel());
        verify(auditService).logDecision(any(), any(), any(), eq(RiskLevel.CRITICAL), eq(100), any(), any(), any());
    }

    @Test
    void calculateRisk_LowScore_ShouldReturnLowRiskLevel() {
        // Given
        when(rule1.evaluate(any(), any())).thenReturn(10);
        when(rule2.evaluate(any(), any())).thenReturn(5);
        when(assessmentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        FraudRiskAssessment result = fraudRiskService.calculateRisk(
                user,
                BigDecimal.TEN,
                "1.1.1.1",
                "UK",
                "f"
        );

        // Then
        assertEquals(15, result.getScore());
        assertEquals(RiskLevel.LOW, result.getRiskLevel());
        verify(auditService).logDecision(any(), any(), any(), eq(RiskLevel.LOW), eq(15), any(), any(), any());
    }
}








