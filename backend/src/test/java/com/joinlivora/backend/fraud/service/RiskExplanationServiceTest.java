package com.joinlivora.backend.fraud.service;

import com.joinlivora.backend.fraud.model.RiskDecision;
import com.joinlivora.backend.fraud.model.RiskExplanation;
import com.joinlivora.backend.fraud.model.RiskSubjectType;
import com.joinlivora.backend.fraud.repository.RiskExplanationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RiskExplanationServiceTest {

    @Mock
    private RiskExplanationRepository repository;

    @InjectMocks
    private RiskExplanationService service;

    private UUID subjectId;

    @BeforeEach
    void setUp() {
        subjectId = UUID.randomUUID();
    }

    @Test
    void generateSystemExplanation_ShouldBuildTextFromFactors() {
        // Given
        Map<String, Object> factors = Map.of(
                "NEW_DEVICE", true,
                "VPN_PROXY", true
        );
        when(repository.save(any(RiskExplanation.class))).thenAnswer(i -> i.getArgument(0));

        // When
        RiskExplanation result = service.generateSystemExplanation(
                RiskSubjectType.USER, subjectId, 50, RiskDecision.REVIEW, factors);

        // Then
        assertNotNull(result);
        assertEquals(subjectId, result.getSubjectId());
        assertEquals(RiskSubjectType.USER, result.getSubjectType());
        assertEquals(50, result.getRiskScore());
        assertEquals(RiskDecision.REVIEW, result.getDecision());
        assertEquals(factors, result.getFactors());
        
        // Order of factors in map might vary, check for both
        assertTrue(result.getExplanationText().contains("[LOW] [Trust Evaluation] Action performed from a new device (current session)"));
        assertTrue(result.getExplanationText().contains("[MEDIUM] [Trust Evaluation] VPN or Proxy usage detected (current session)"));
        assertTrue(result.getExplanationText().endsWith("."));
        
        verify(repository).save(any(RiskExplanation.class));
    }

    @Test
    void generateSystemExplanation_UnknownFactor_ShouldUseKey() {
        // Given
        Map<String, Object> factors = Map.of("UNKNOWN_FACTOR", "value");
        when(repository.save(any(RiskExplanation.class))).thenAnswer(i -> i.getArgument(0));

        // When
        RiskExplanation result = service.generateSystemExplanation(
                RiskSubjectType.CREATOR, subjectId, 10, RiskDecision.ALLOW, factors);

        // Then
        assertEquals("UNKNOWN_FACTOR.", result.getExplanationText());
    }

    @Test
    void generateSystemExplanation_NoFactors_ShouldUseDefaultText() {
        // Given
        when(repository.save(any(RiskExplanation.class))).thenAnswer(i -> i.getArgument(0));

        // When
        RiskExplanation result = service.generateSystemExplanation(
                RiskSubjectType.CREATOR, subjectId, 0, RiskDecision.ALLOW, Collections.emptyMap());

        // Then
        assertEquals("No specific risk factors identified.", result.getExplanationText());
    }

    @Test
    void generateAdminExplanation_ShouldUseCustomText() {
        // Given
        String customText = "Confirmed legitimate transaction after phone call.";
        when(repository.save(any(RiskExplanation.class))).thenAnswer(i -> i.getArgument(0));

        // When
        RiskExplanation result = service.generateAdminExplanation(
                RiskSubjectType.TRANSACTION, subjectId, 0, RiskDecision.ALLOW, customText, null);

        // Then
        assertNotNull(result);
        assertEquals("ADMIN OVERRIDE: " + customText, result.getExplanationText());
        assertNull(result.getFactors());
        verify(repository).save(any(RiskExplanation.class));
    }

    @Test
    void generateSystemExplanation_NewTemplates_ShouldFormatCorrectly() {
        // Given
        Map<String, Object> factors = Map.of(
                "HIGH_CHARGEBACK_RATIO", true,
                "LOW_REPUTATION", true
        );
        when(repository.save(any(RiskExplanation.class))).thenAnswer(i -> i.getArgument(0));

        // When
        RiskExplanation result = service.generateSystemExplanation(
                RiskSubjectType.CREATOR, subjectId, 75, RiskDecision.LIMIT, factors);

        // Then
        assertTrue(result.getExplanationText().contains("[HIGH] [Payments] High chargeback ratio (last 14 days)"));
        assertTrue(result.getExplanationText().contains("[MEDIUM] [Reputation System] Creator reputation below trusted threshold (current)"));
    }

    @Test
    void getExplanationsForSubject_ShouldCallRepository() {
        // Given
        when(repository.findAllBySubjectIdAndSubjectTypeOrderByGeneratedAtDesc(subjectId, RiskSubjectType.CREATOR))
                .thenReturn(List.of(new RiskExplanation()));

        // When
        List<RiskExplanation> results = service.getExplanationsForSubject(subjectId, RiskSubjectType.CREATOR);

        // Then
        assertEquals(1, results.size());
        verify(repository).findAllBySubjectIdAndSubjectTypeOrderByGeneratedAtDesc(subjectId, RiskSubjectType.CREATOR);
    }
}








