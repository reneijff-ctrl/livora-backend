package com.joinlivora.backend.exception.chat;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class PpvRoomAlreadyExistsException extends RuntimeException {
    public PpvRoomAlreadyExistsException(String message) {
        super(message);
    }
}
