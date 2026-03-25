package com.joinlivora.backend.fraud.dto;

import com.joinlivora.backend.fraud.model.RiskDecision;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SanitizedRiskExplanationDto {
    private UUID id;
    private RiskDecision decision;
    private String explanationText;
    private Instant generatedAt;
}
