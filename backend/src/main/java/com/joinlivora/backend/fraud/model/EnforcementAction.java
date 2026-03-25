package com.joinlivora.backend.fraud.model;

/**
 * Represents actions that can be taken as part of fraud enforcement.
 */
public enum EnforcementAction {
    NONE,
    FREEZE_PAYOUTS,
    SUSPEND_ACCOUNT,
    TERMINATE_ACCOUNT
}
