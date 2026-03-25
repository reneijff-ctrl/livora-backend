package com.joinlivora.backend.payouts.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class PayoutFrozenException extends RuntimeException {
    public PayoutFrozenException(String message) {
        super(message);
    }
}
