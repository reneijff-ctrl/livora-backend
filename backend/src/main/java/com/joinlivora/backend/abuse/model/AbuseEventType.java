package com.joinlivora.backend.abuse.model;

/**
 * Enum representing the type of abuse event.
 */
public enum AbuseEventType {
    RAPID_TIPPING,
    MESSAGE_SPAM,
    LOGIN_BRUTE_FORCE,
    MULTI_ACCOUNT_BEHAVIOR,
    SUSPICIOUS_API_USAGE,
    RESTRICTION_ESCALATED
}
