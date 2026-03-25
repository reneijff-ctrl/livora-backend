package com.joinlivora.backend.payment.dto;

import com.joinlivora.backend.fraud.model.RiskLevel;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class RiskEscalationResult {
    private RiskLevel riskLevel;
    private List<String> actions;
}
