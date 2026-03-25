package com.joinlivora.backend.chat.analysis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Basic implementation of toxicity analysis using keyword and pattern matching.
 */
@Component
@Slf4j
public class BasicToxicityAnalyzer implements ToxicityAnalyzer {

    @Value("${livora.chat.moderation.toxic-keywords:}")
    private List<String> toxicKeywords;

    @Value("${livora.chat.moderation.toxic-patterns:}")
    private List<String> toxicPatterns;

    @Override
    public boolean isToxic(String message) {
        if (message == null || message.trim().isEmpty()) {
            return false;
        }

        String lowerMessage = message.toLowerCase();

        // 1. Keyword matching
        if (toxicKeywords != null) {
            for (String keyword : toxicKeywords) {
                if (lowerMessage.contains(keyword.toLowerCase())) {
                    log.debug("Toxicity detected by keyword: {}", keyword);
                    return true;
                }
            }
        }

        // 2. Pattern matching
        if (toxicPatterns != null) {
            for (String patternStr : toxicPatterns) {
                try {
                    Pattern pattern = Pattern.compile(patternStr, Pattern.CASE_INSENSITIVE);
                    if (pattern.matcher(message).find()) {
                        log.debug("Toxicity detected by pattern: {}", patternStr);
                        return true;
                    }
                } catch (Exception e) {
                    log.error("Invalid toxicity pattern: {}", patternStr, e);
                }
            }
        }

        return false;
    }
}
