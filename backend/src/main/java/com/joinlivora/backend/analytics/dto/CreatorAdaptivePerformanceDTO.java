package com.joinlivora.backend.analytics.dto;
import lombok.Value;
import lombok.Builder;

@Value
@Builder
public class CreatorAdaptivePerformanceDTO {
    int experimentsRun;
    double successRate;
    double averageRevenueLift;
    double averageRiskReduction;
    String momentum;
    double engineConfidence;
}
