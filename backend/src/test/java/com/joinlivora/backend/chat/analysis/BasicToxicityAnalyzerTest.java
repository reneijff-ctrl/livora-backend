package com.joinlivora.backend.chat.analysis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BasicToxicityAnalyzerTest {

    private BasicToxicityAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new BasicToxicityAnalyzer();
        ReflectionTestUtils.setField(analyzer, "toxicKeywords", List.of("toxicword1", "toxicword2"));
        ReflectionTestUtils.setField(analyzer, "toxicPatterns", List.of(".*f[u*][c*]k.*"));
    }

    @Test
    void isToxic_NormalMessage_ShouldReturnFalse() {
        assertFalse(analyzer.isToxic("Hello world, have a nice day!"));
    }

    @Test
    void isToxic_ToxicKeyword_ShouldReturnTrue() {
        assertTrue(analyzer.isToxic("This is a toxicword1 in a message."));
        assertTrue(analyzer.isToxic("This is a toxicword2 in a message."));
    }

    @Test
    void isToxic_ToxicPattern_ShouldReturnTrue() {
        assertTrue(analyzer.isToxic("This is f*ck in a message."));
    }

    @Test
    void isToxic_EmptyMessage_ShouldReturnFalse() {
        assertFalse(analyzer.isToxic(""));
        assertFalse(analyzer.isToxic(null));
        assertFalse(analyzer.isToxic("   "));
    }

    @Test
    void isToxic_CaseInsensitiveKeyword_ShouldReturnTrue() {
        assertTrue(analyzer.isToxic("This is a TOXICWORD1 in a message."));
    }
}








