package com.joinlivora.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class PayoutRestrictedException extends RuntimeException {
    public PayoutRestrictedException(String message) {
        super(message);
    }
}
