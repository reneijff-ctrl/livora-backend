package com.joinlivora.backend.fraud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskProfile {
    private UUID userId;
    private int riskScore;
    private int trustScore;
    private Instant lastEvaluatedAt;
    private Map<String, Object> factors;
}
