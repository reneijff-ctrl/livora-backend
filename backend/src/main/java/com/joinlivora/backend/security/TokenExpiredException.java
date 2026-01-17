package com.joinlivora.backend.security;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class TokenExpiredException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public TokenExpiredException(String message) {
        super(message);
    }
}
