package com.joinlivora.backend.fraud.model;

public enum FraudRiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    public static FraudRiskLevel fromScore(int score) {
        if (score < 40) {
            return LOW;
        } else if (score < 70) {
            return MEDIUM;
        } else if (score < 90) {
            return HIGH;
        } else {
            return CRITICAL;
        }
    }
}
