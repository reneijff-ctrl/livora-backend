package com.joinlivora.backend.fraud.model;

import java.util.List;

public record FraudRiskResult(
        FraudRiskLevel level,
        int score,
        List<String> reasons
) {
}
