package com.joinlivora.backend.exception;

import com.joinlivora.backend.monetization.dto.SuperTipErrorCode;
import lombok.Getter;

@Getter
public class SuperTipException extends RuntimeException {
    private final SuperTipErrorCode errorCode;

    public SuperTipException(SuperTipErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
