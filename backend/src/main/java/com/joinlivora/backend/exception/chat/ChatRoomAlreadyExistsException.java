package com.joinlivora.backend.exception.chat;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class ChatRoomAlreadyExistsException extends RuntimeException {
    public ChatRoomAlreadyExistsException(String message) {
        super(message);
    }
}
