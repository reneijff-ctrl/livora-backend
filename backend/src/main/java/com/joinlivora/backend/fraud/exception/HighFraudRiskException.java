package com.joinlivora.backend.fraud.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;

@Getter
@ResponseStatus(HttpStatus.FORBIDDEN)
public class HighFraudRiskException extends RuntimeException {
    private final int score;
    private final List<String> reasons;

    public HighFraudRiskException(int score, List<String> reasons) {
        super("Transaction blocked for security review");
        this.score = score;
        this.reasons = reasons;
    }
}
