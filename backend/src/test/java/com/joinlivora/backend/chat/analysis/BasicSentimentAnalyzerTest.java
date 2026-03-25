package com.joinlivora.backend.chat.analysis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BasicSentimentAnalyzerTest {

    private BasicSentimentAnalyzer analyzer;

    @Mock
    private ToxicityAnalyzer toxicityAnalyzer;

    @BeforeEach
    void setUp() {
        analyzer = new BasicSentimentAnalyzer(toxicityAnalyzer);
        ReflectionTestUtils.setField(analyzer, "positiveKeywords", List.of("happy", "love", "awesome"));
        ReflectionTestUtils.setField(analyzer, "negativeKeywords", List.of("sad", "hate", "terrible"));
        
        lenient().when(toxicityAnalyzer.isToxic(anyString())).thenReturn(false);
    }

    @Test
    void analyze_PositiveMessage_ShouldReturnPositiveResult() {
        SentimentResult result = analyzer.analyze("I love this app, it is awesome and makes me happy!");
        
        assertTrue(result.getScore() > 0);
        assertTrue(result.isPositive());
        assertFalse(result.isToxic());
    }

    @Test
    void analyze_NegativeMessage_ShouldReturnNegativeResult() {
        SentimentResult result = analyzer.analyze("I hate this, it is so sad and terrible.");
        
        assertTrue(result.getScore() < 0);
        assertFalse(result.isPositive());
        assertFalse(result.isToxic());
    }

    @Test
    void analyze_ToxicMessage_ShouldReturnToxicResult() {
        when(toxicityAnalyzer.isToxic(anyString())).thenReturn(true);
        
        SentimentResult result = analyzer.analyze("Some toxic message");
        
        assertTrue(result.isToxic());
        assertTrue(result.getScore() < 0);
        assertFalse(result.isPositive());
    }

    @Test
    void analyze_NeutralMessage_ShouldReturnZeroScore() {
        SentimentResult result = analyzer.analyze("Hello world");
        
        assertEquals(0.0, result.getScore());
        assertFalse(result.isPositive());
        assertFalse(result.isToxic());
    }

    @Test
    void analyze_MixedMessage_ShouldBalanceScore() {
        SentimentResult result = analyzer.analyze("I love it but it is sad");
        // love (+0.25), sad (-0.25) => 0
        assertEquals(0.0, result.getScore());
    }

    @Test
    void analyze_EmptyMessage_ShouldReturnDefaultResult() {
        SentimentResult result = analyzer.analyze("");
        assertEquals(0.0, result.getScore());
        assertFalse(result.isPositive());
        assertFalse(result.isToxic());
    }
}








