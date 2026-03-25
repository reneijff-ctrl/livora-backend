package com.joinlivora.backend.analytics.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdaptiveTipEngineResponseDTO {
    private String status;
    private int riskScore;
    private double avgPerSale;
    private double suggestedFloor;
    private double momentum;
    private double top1Share;
    private String confidenceTier;
    private Long cooldownRemainingSeconds;
    private String reason;
}
