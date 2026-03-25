package com.joinlivora.backend.monetization;

import lombok.Getter;
import java.math.BigDecimal;

@Getter
public enum HighlightType {
    COLOR(new BigDecimal("2.00"), 15, false),
    PINNED(new BigDecimal("5.00"), 60, true),
    LARGE(new BigDecimal("10.00"), 30, true);

    private final BigDecimal minimumAmount;
    private final int durationSeconds;
    private final boolean priority;

    HighlightType(BigDecimal minimumAmount, int durationSeconds, boolean priority) {
        this.minimumAmount = minimumAmount;
        this.durationSeconds = durationSeconds;
        this.priority = priority;
    }
}
