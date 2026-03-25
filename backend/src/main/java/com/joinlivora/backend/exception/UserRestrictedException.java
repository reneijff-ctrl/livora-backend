package com.joinlivora.backend.exception;

import com.joinlivora.backend.abuse.model.RestrictionLevel;
import lombok.Getter;

@Getter
public class UserRestrictedException extends RuntimeException {
    private final RestrictionLevel level;
    private final java.time.Instant expiresAt;

    public UserRestrictedException(RestrictionLevel level, String message) {
        this(level, message, null);
    }

    public UserRestrictedException(RestrictionLevel level, String message, java.time.Instant expiresAt) {
        super(message);
        this.level = level;
        this.expiresAt = expiresAt;
    }
}
