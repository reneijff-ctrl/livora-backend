package com.joinlivora.backend.chat.analysis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Basic implementation of sentiment analysis using keyword matching.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class BasicSentimentAnalyzer implements SentimentAnalyzer {

    private final ToxicityAnalyzer toxicityAnalyzer;

    @Value("${livora.chat.sentiment.positive-keywords:love,happy,great,awesome,good,best,cool,nice,thanks,thank,wonderful}")
    private List<String> positiveKeywords;

    @Value("${livora.chat.sentiment.negative-keywords:hate,sad,bad,worst,awful,terrible,horrible,angry,dislike,no,stop}")
    private List<String> negativeKeywords;

    @Override
    public SentimentResult analyze(String message) {
        if (message == null || message.trim().isEmpty()) {
            return SentimentResult.builder()
                    .score(0.0)
                    .positive(false)
                    .toxic(false)
                    .build();
        }

        String lowerMessage = message.toLowerCase();
        double score = 0.0;

        // 1. Positive scoring
        if (positiveKeywords != null) {
            for (String kw : positiveKeywords) {
                if (lowerMessage.contains(kw.toLowerCase())) {
                    score += 0.25;
                }
            }
        }

        // 2. Negative scoring
        if (negativeKeywords != null) {
            for (String kw : negativeKeywords) {
                if (lowerMessage.contains(kw.toLowerCase())) {
                    score -= 0.25;
                }
            }
        }

        // 3. Toxicity check
        boolean toxic = toxicityAnalyzer.isToxic(message);
        if (toxic) {
            score -= 0.5; // Toxicity strongly weights towards negative
        }

        // Cap score at -1 to +1
        score = Math.max(-1.0, Math.min(1.0, score));

        return SentimentResult.builder()
                .score(score)
                .positive(score > 0)
                .toxic(toxic)
                .build();
    }
}
