package com.joinlivora.backend.abuse.model;

/**
 * Enum representing the level of restriction applied to a creator or IP due to abusive behavior.
 */
public enum RestrictionLevel {
    NONE,
    SLOW_MODE,
    TIP_COOLDOWN,
    TIP_LIMIT,
    CHAT_MUTE,
    FRAUD_LOCK,
    TEMP_SUSPENSION
}
