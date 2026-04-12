package com.joinlivora.backend.resilience;

/**
 * Thrown when a security-critical operation cannot be completed because a required
 * external dependency (database, Redis) is unavailable (circuit OPEN).
 *
 * <p>This exception is intentionally unchecked so it propagates through
 * {@code @Transactional} boundaries and triggers rollback without requiring
 * callers to declare a checked exception.</p>
 *
 * <p><strong>Fail-CLOSED semantics</strong>: if we cannot verify authorization,
 * payment status, or rate-limit state, we DENY the request rather than silently
 * granting access.</p>
 */
public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException(String message) {
        super(message);
    }

    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
