package com.joinlivora.backend.fraud.model;

public enum RiskFactor {
    CHARGEBACK_RATE,
    CHARGEBACK_COUNT,
    HIGH_TIP_FREQUENCY,
    RAPID_PAYOUT_REQUESTS,
    MULTIPLE_ACCOUNTS,
    MANUAL_FLAG
}
