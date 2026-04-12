package com.joinlivora.backend.resilience;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Redis Circuit Breaker — wraps all Redis operations platform-wide.
 *
 * <h2>States</h2>
 * <pre>
 *   CLOSED  → normal operation; all calls go through
 *   OPEN    → Redis is down; calls short-circuit immediately (fail-open/closed per op)
 *   HALF_OPEN → probe phase; one call allowed through to test recovery
 * </pre>
 *
 * <h2>Thresholds (configurable via application.yml livora.redis.circuit-breaker.*)</h2>
 * <ul>
 *   <li>failureThreshold = 5  — consecutive failures to open</li>
 *   <li>successThreshold = 2  — consecutive successes in HALF_OPEN to close</li>
 *   <li>openDuration   = 10s  — wait before probing again</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>
 *   Long count = redisCircuitBreaker.execute(
 *       () -> redisTemplate.opsForValue().increment(key),
 *       null   // fallback value on open/failure
 *   );
 * </pre>
 */
@Slf4j
@Component
public class RedisCircuitBreakerService {

    // ── Circuit-breaker thresholds ────────────────────────────────────────────
    private static final int    FAILURE_THRESHOLD = 5;
    private static final int    SUCCESS_THRESHOLD = 2;
    private static final Duration OPEN_DURATION   = Duration.ofSeconds(10);

    // ── State ─────────────────────────────────────────────────────────────────
    private enum State { CLOSED, OPEN, HALF_OPEN }

    private final AtomicReference<State>   state            = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger            consecutiveFailures = new AtomicInteger(0);
    private final AtomicInteger            consecutiveSuccesses = new AtomicInteger(0);
    private final AtomicLong               openedAt         = new AtomicLong(0);
    private final AtomicInteger            halfOpenProbes   = new AtomicInteger(0);

    // ── Metrics ───────────────────────────────────────────────────────────────
    private final Counter openedCounter;
    private final Counter closedCounter;
    private final Counter shortCircuitedCounter;
    private final Counter successCounter;
    private final Counter failureCounter;

    public RedisCircuitBreakerService(MeterRegistry registry) {
        this.openedCounter        = registry.counter("redis.circuit.opened");
        this.closedCounter        = registry.counter("redis.circuit.closed");
        this.shortCircuitedCounter = registry.counter("redis.circuit.shortcircuited");
        this.successCounter       = registry.counter("redis.circuit.success");
        this.failureCounter       = registry.counter("redis.circuit.failure");
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Execute a Redis operation with circuit-breaker protection.
     *
     * @param operation  The Redis operation to execute
     * @param fallback   Value returned when the circuit is OPEN or the operation fails
     * @param <T>        Return type
     * @return operation result or fallback
     */
    public <T> T execute(Supplier<T> operation, T fallback) {
        State current = state.get();

        if (current == State.OPEN) {
            if (shouldAttemptReset()) {
                return attemptHalfOpen(operation, fallback);
            }
            shortCircuitedCounter.increment();
            return fallback;
        }

        if (current == State.HALF_OPEN && halfOpenProbes.get() > 0) {
            // Only one probe allowed at a time — others short-circuit
            shortCircuitedCounter.increment();
            return fallback;
        }

        return callOperation(operation, fallback);
    }

    /**
     * Execute a Redis operation with circuit-breaker protection and an operation label
     * for structured logging. Semantically identical to {@link #execute(Supplier, Object)}
     * but emits the label in WARN/DEBUG output.
     *
     * @param operation  The Redis operation to execute
     * @param fallback   Value returned when the circuit is OPEN or the operation fails
     * @param label      Short descriptive name, e.g. {@code "redis:rate-limit:login"}
     * @param <T>        Return type
     */
    public <T> T execute(Supplier<T> operation, T fallback, String label) {
        State current = state.get();
        if (current == State.OPEN) {
            if (shouldAttemptReset()) {
                return attemptHalfOpen(operation, fallback);
            }
            shortCircuitedCounter.increment();
            log.debug("[CIRCUIT_BREAKER] OPEN — short-circuiting Redis call '{}'", label);
            return fallback;
        }
        if (current == State.HALF_OPEN && halfOpenProbes.get() > 0) {
            shortCircuitedCounter.increment();
            return fallback;
        }
        return callOperation(operation, fallback);
    }

    /**
     * Execute a Redis void operation (e.g., SET, DEL).
     *
     * @param operation  The Redis operation to execute
     * @return true if the call succeeded, false if it was short-circuited or failed
     */
    public boolean execute(Runnable operation) {
        Boolean result = execute(() -> { operation.run(); return Boolean.TRUE; }, Boolean.FALSE);
        return Boolean.TRUE.equals(result);
    }

    /** @return true if the circuit is currently OPEN (Redis considered down) */
    public boolean isOpen() {
        return state.get() == State.OPEN;
    }

    /** @return true if the circuit is in normal operation */
    public boolean isClosed() {
        return state.get() == State.CLOSED;
    }

    /** @return current state as a string for health endpoints */
    public String getState() {
        return state.get().name();
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private <T> T callOperation(Supplier<T> operation, T fallback) {
        try {
            T result = operation.get();
            onSuccess();
            return result;
        } catch (Exception ex) {
            onFailure(ex);
            return fallback;
        }
    }

    private <T> T attemptHalfOpen(Supplier<T> operation, T fallback) {
        if (!halfOpenProbes.compareAndSet(0, 1)) {
            shortCircuitedCounter.increment();
            return fallback;
        }
        state.set(State.HALF_OPEN);
        log.info("[CIRCUIT_BREAKER] HALF_OPEN — probing Redis");
        try {
            T result = operation.get();
            onSuccess();
            return result;
        } catch (Exception ex) {
            onFailure(ex);
            return fallback;
        } finally {
            halfOpenProbes.set(0);
        }
    }

    private void onSuccess() {
        successCounter.increment();
        consecutiveFailures.set(0);

        State current = state.get();
        if (current == State.HALF_OPEN) {
            int successes = consecutiveSuccesses.incrementAndGet();
            if (successes >= SUCCESS_THRESHOLD) {
                if (state.compareAndSet(State.HALF_OPEN, State.CLOSED)) {
                    consecutiveSuccesses.set(0);
                    closedCounter.increment();
                    log.info("[CIRCUIT_BREAKER] CLOSED — Redis recovered after {} consecutive successes",
                            SUCCESS_THRESHOLD);
                }
            }
        } else {
            consecutiveSuccesses.set(0);
        }
    }

    private void onFailure(Exception ex) {
        failureCounter.increment();
        consecutiveSuccesses.set(0);

        int failures = consecutiveFailures.incrementAndGet();
        log.warn("[CIRCUIT_BREAKER] Redis failure #{}: {}", failures, ex.getMessage());

        if (failures >= FAILURE_THRESHOLD) {
            if (state.compareAndSet(State.CLOSED, State.OPEN)
                    || state.compareAndSet(State.HALF_OPEN, State.OPEN)) {
                openedAt.set(Instant.now().toEpochMilli());
                openedCounter.increment();
                log.error("[CIRCUIT_BREAKER] OPEN — Redis declared DOWN after {} consecutive failures. "
                        + "All Redis operations will short-circuit for {}s.",
                        FAILURE_THRESHOLD, OPEN_DURATION.getSeconds());
            }
        }
    }

    private boolean shouldAttemptReset() {
        long elapsed = Instant.now().toEpochMilli() - openedAt.get();
        return elapsed >= OPEN_DURATION.toMillis();
    }
}
