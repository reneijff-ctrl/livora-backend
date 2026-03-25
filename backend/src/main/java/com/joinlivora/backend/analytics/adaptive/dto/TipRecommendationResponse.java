package com.joinlivora.backend.analytics.adaptive.dto;

import lombok.Getter;
import java.math.BigDecimal;

/**
 * Immutable DTO for tip recommendations.
 */
@Getter
public final class TipRecommendationResponse {
    private final BigDecimal recommendedMinimumTip;
    private final double confidenceScore;
    private final String riskLevel;
    private final String explanation;

    public TipRecommendationResponse(
            BigDecimal recommendedMinimumTip,
            double confidenceScore,
            String riskLevel,
            String explanation
    ) {
        this.recommendedMinimumTip = recommendedMinimumTip;
        this.confidenceScore = confidenceScore;
        this.riskLevel = riskLevel;
        this.explanation = explanation;
    }
}
