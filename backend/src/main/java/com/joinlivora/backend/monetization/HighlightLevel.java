package com.joinlivora.backend.monetization;

import lombok.Getter;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Comparator;

@Getter
public enum HighlightLevel {
    BASIC(new BigDecimal("10.00"), "#FFFFFF", 30, false),
    PREMIUM(new BigDecimal("50.00"), "#FFD700", 60, true),
    ULTRA(new BigDecimal("100.00"), "#FF4500", 120, true);

    private final BigDecimal minimumAmount;
    private final String highlightColor;
    private final int displayDurationSeconds;
    private final boolean priority;

    HighlightLevel(BigDecimal minimumAmount, String highlightColor, int displayDurationSeconds, boolean priority) {
        this.minimumAmount = minimumAmount;
        this.highlightColor = highlightColor;
        this.displayDurationSeconds = displayDurationSeconds;
        this.priority = priority;
    }

    /**
     * Determines the appropriate highlight level based on the tip amount.
     * Returns the highest level where amount >= minimumAmount.
     */
    public static HighlightLevel fromAmount(BigDecimal amount) {
        if (amount == null) return null;
        
        return Arrays.stream(values())
                .filter(level -> level.qualifies(amount))
                .max(Comparator.comparing(HighlightLevel::getMinimumAmount))
                .orElse(null);
    }

    /**
     * Checks if the given amount qualifies for this highlight level.
     */
    public boolean qualifies(BigDecimal amount) {
        return amount != null && amount.compareTo(minimumAmount) >= 0;
    }
}
