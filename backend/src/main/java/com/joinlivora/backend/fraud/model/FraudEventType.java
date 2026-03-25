package com.joinlivora.backend.fraud.model;

/**
 * Represents the type of fraud-related event.
 */
public enum FraudEventType {
    CHARGEBACK_REPORTED,
    PAYOUT_FROZEN,
    ACCOUNT_SUSPENDED,
    ACCOUNT_TERMINATED,
    MANUAL_OVERRIDE,
    PAYMENT_SUCCESS,
    CRITICAL_RISK_DETECTED
}
