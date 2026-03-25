package com.joinlivora.backend.fraud.dto;

import com.joinlivora.backend.user.FraudRiskLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FraudRiskOverrideRequest {
    private FraudRiskLevel riskLevel;
}
