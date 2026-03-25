package com.joinlivora.backend.fraud.repository;

import com.joinlivora.backend.fraud.model.RiskDecision;
import com.joinlivora.backend.fraud.model.RiskExplanation;
import com.joinlivora.backend.fraud.model.RiskSubjectType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class RiskExplanationRepositoryTest {

    @Autowired
    private RiskExplanationRepository repository;

    @Test
    void testSaveAndFind() {
        UUID subjectId = UUID.randomUUID();
        RiskExplanation explanation = RiskExplanation.builder()
                .subjectType(RiskSubjectType.CREATOR)
                .subjectId(subjectId)
                .riskScore(75)
                .decision(RiskDecision.REVIEW)
                .explanationText("High velocity detected")
                .factors(Map.of("velocity", 80, "history", "clean"))
                .build();

        RiskExplanation saved = repository.save(explanation);
        assertNotNull(saved.getId());
        assertNotNull(saved.getGeneratedAt());

        List<RiskExplanation> found = repository.findAllBySubjectIdAndSubjectTypeOrderByGeneratedAtDesc(subjectId, RiskSubjectType.CREATOR);
        assertFalse(found.isEmpty());
        assertEquals(1, found.size());
        
        RiskExplanation result = found.get(0);
        assertEquals(75, result.getRiskScore());
        assertEquals(RiskDecision.REVIEW, result.getDecision());
        assertEquals("High velocity detected", result.getExplanationText());
        assertEquals(80, result.getFactors().get("velocity"));
    }
}








