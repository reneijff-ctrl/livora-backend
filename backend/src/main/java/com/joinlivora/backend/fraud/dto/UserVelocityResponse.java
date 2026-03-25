package com.joinlivora.backend.fraud.dto;

import com.joinlivora.backend.fraud.model.FraudDecisionLevel;
import com.joinlivora.backend.fraud.model.RuleFraudSignal;
import com.joinlivora.backend.fraud.model.VelocityMetric;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class UserVelocityResponse {
    private Long userId;
    private FraudDecisionLevel riskLevel;
    private boolean paymentLocked;
    private Instant blockedUntil;
    private List<VelocityMetric> currentMetrics;
    private List<RuleFraudSignal> recentSignals;
}
