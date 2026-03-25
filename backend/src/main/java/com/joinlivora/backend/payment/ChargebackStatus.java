package com.joinlivora.backend.payment;

/**
 * Represents the lifecycle status of a chargeback (dispute).
 */
public enum ChargebackStatus {
    /**
     * The chargeback has been received.
     */
    RECEIVED,

    /**
     * Evidence has been submitted and is being reviewed by the bank.
     */
    UNDER_REVIEW,

    /**
     * The chargeback was successfully contested and funds were returned.
     */
    WON,

    /**
     * The chargeback was lost and funds were permanently deducted.
     */
    LOST
}
