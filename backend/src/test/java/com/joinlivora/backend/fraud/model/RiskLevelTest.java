package com.joinlivora.backend.fraud.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RiskLevelTest {

    @ParameterizedTest
    @CsvSource({
            "0, LOW",
            "39, LOW",
            "40, MEDIUM",
            "69, MEDIUM",
            "70, HIGH",
            "89, HIGH",
            "90, CRITICAL",
            "100, CRITICAL"
    })
    void fromScore_ShouldReturnCorrectLevel(int score, RiskLevel expected) {
        assertEquals(expected, RiskLevel.fromScore(score));
    }
}








