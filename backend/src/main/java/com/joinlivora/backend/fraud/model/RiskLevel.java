package com.joinlivora.backend.fraud.model;

public enum RiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    /**
     * Resolves RiskLevel from a numeric score (0-100).
     *
     * Rules:
     * - LOW: score < 40
     * - MEDIUM: 40–69
     * - HIGH: 70–89
     * - CRITICAL: >= 90
     *
     * @param score the risk score
     * @return the corresponding RiskLevel
     */
    public static RiskLevel fromScore(int score) {
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
