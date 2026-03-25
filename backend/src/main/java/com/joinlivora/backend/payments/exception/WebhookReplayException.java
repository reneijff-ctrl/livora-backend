package com.joinlivora.backend.payments.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class WebhookReplayException extends RuntimeException {
    public WebhookReplayException(String message) {
        super(message);
    }
}
