package com.joinlivora.backend.fraud.dto;

import com.joinlivora.backend.fraud.model.RiskDecision;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskDecisionResult {
    private RiskDecision decision;
    private UUID explanationId;
    private int riskScore;
}
