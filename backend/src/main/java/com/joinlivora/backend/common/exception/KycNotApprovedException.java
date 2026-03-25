package com.joinlivora.backend.common.exception;

public class KycNotApprovedException extends RuntimeException {

    public KycNotApprovedException() {
        super("Creator KYC not approved. Payout blocked.");
    }
}
