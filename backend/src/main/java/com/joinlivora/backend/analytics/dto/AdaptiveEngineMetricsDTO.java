package com.joinlivora.backend.analytics.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdaptiveEngineMetricsDTO {
    private long totalExperiments;
    private long successfulExperiments;
    private double successRate;
    private double averageRevenueLift;
    private double averageRiskDelta;
    private long activeExperiments;
}
