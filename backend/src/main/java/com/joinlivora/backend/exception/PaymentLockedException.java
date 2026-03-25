package com.joinlivora.backend.exception;

public class PaymentLockedException extends RuntimeException {
    public PaymentLockedException(String message) {
        super(message);
    }
}
