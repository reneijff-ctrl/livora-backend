package com.joinlivora.backend.resilience;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Database Circuit Breaker — protects all DB-backed calls from slow-query
 * cascading failures.
 *
 * <h2>Failure Modes Handled</h2>
 * <ul>
 *   <li>PostgreSQL down → connection timeout → OPEN state</li>
 *   <li>HikariCP pool exhausted → {@link java.sql.SQLTransientConnectionException} → OPEN</li>
 *   <li>Slow queries (>2s) → enforced timeout → counted as failure</li>
 * </ul>
 *
 * <h2>Key Safety Properties</h2>
 * <ul>
 *   <li>DB calls that would block an HTTP thread for >2s are timed out</li>
 *   <li>After 5 consecutive failures the circuit opens for 15 seconds</li>
 *   <li>All fallback values are {@link Optional#empty()} or user-supplied defaults</li>
 *   <li>HLS validate endpoint NEVER hits DB — protected by {@link
 *       com.joinlivora.backend.streaming.HlsTokenService} HMAC-only path</li>
 * </ul>
 */
@Slf4j
@Component
public class DatabaseCircuitBreakerService {

    // ── Thresholds ────────────────────────────────────────────────────────────
    private static final int      FAILURE_THRESHOLD  = 5;
    private static final int      SUCCESS_THRESHOLD  = 2;
    private static final Duration OPEN_DURATION      = Duration.ofSeconds(15);
    private static final Duration QUERY_TIMEOUT      = Duration.ofSeconds(2);

    // ── State ─────────────────────────────────────────────────────────────────
    private enum State { CLOSED, OPEN, HALF_OPEN }

    private final AtomicReference<State> state               = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger          consecutiveFailures = new AtomicInteger(0);
    private final AtomicInteger          consecutiveSuccesses = new AtomicInteger(0);
    private final AtomicLong             openedAt            = new AtomicLong(0);
    private final AtomicInteger          halfOpenProbes      = new AtomicInteger(0);

    // ── Executor for timeout enforcement ─────────────────────────────────────
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "db-cb-timeout");
        t.setDaemon(true);
        return t;
    });

    // ── Metrics ───────────────────────────────────────────────────────────────
    private final Counter openedCounter;
    private final Counter closedCounter;
    private final Counter shortCircuitedCounter;
    private final Counter timeoutCounter;
    private final Timer   queryTimer;

    public DatabaseCircuitBreakerService(MeterRegistry registry) {
        this.openedCounter        = registry.counter("db.circuit.opened");
        this.closedCounter        = registry.counter("db.circuit.closed");
        this.shortCircuitedCounter = registry.counter("db.circuit.shortcircuited");
        this.timeoutCounter       = registry.counter("db.circuit.timeout");
        this.queryTimer           = registry.timer("db.circuit.query.duration");
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Execute a DB operation with circuit-breaker + timeout protection.
     *
     * @param operation  DB call (e.g., repository method)
     * @param fallback   Value returned when circuit is OPEN or call fails/times out
     * @param label      Short name for logging (e.g., "findStream", "resolveUser")
     * @param <T>        Return type
     */
    public <T> T execute(Supplier<T> operation, T fallback, String label) {
        State current = state.get();

        if (current == State.OPEN) {
            if (shouldAttemptReset()) {
                return attemptHalfOpen(operation, fallback, label);
            }
            shortCircuitedCounter.increment();
            log.debug("[DB_CIRCUIT] OPEN — short-circuiting DB call '{}'", label);
            return fallback;
        }

        if (current == State.HALF_OPEN && halfOpenProbes.get() > 0) {
            shortCircuitedCounter.increment();
            return fallback;
        }

        return callWithTimeout(operation, fallback, label);
    }

    /**
     * Convenience overload returning {@link Optional#empty()} on failure.
     */
    public <T> Optional<T> executeOptional(Supplier<Optional<T>> operation, String label) {
        return execute(operation, Optional.empty(), label);
    }

    /** @return true when the circuit is OPEN (DB considered down) */
    public boolean isOpen() {
        return state.get() == State.OPEN;
    }

    public String getState() {
        return state.get().name();
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private <T> T callWithTimeout(Supplier<T> operation, T fallback, String label) {
        Timer.Sample sample = Timer.start();
        Future<T> future = executor.submit(operation::get);
        try {
            T result = future.get(QUERY_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            sample.stop(queryTimer);
            onSuccess();
            return result;
        } catch (TimeoutException ex) {
            future.cancel(true);
            timeoutCounter.increment();
            log.warn("[DB_CIRCUIT] Query timeout (>{}ms) for '{}' — counting as failure",
                    QUERY_TIMEOUT.toMillis(), label);
            onFailure(new RuntimeException("DB query timeout: " + label));
            return fallback;
        } catch (ExecutionException ex) {
            log.warn("[DB_CIRCUIT] DB error in '{}': {}", label,
                    ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage());
            onFailure(ex);
            return fallback;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return fallback;
        }
    }

    private <T> T attemptHalfOpen(Supplier<T> operation, T fallback, String label) {
        if (!halfOpenProbes.compareAndSet(0, 1)) {
            shortCircuitedCounter.increment();
            return fallback;
        }
        state.set(State.HALF_OPEN);
        log.info("[DB_CIRCUIT] HALF_OPEN — probing DB with call '{}'", label);
        try {
            T result = callWithTimeout(operation, fallback, label);
            if (result != fallback) onSuccess();
            return result;
        } finally {
            halfOpenProbes.set(0);
        }
    }

    private void onSuccess() {
        consecutiveFailures.set(0);
        State current = state.get();
        if (current == State.HALF_OPEN) {
            int s = consecutiveSuccesses.incrementAndGet();
            if (s >= SUCCESS_THRESHOLD) {
                if (state.compareAndSet(State.HALF_OPEN, State.CLOSED)) {
                    consecutiveSuccesses.set(0);
                    closedCounter.increment();
                    log.info("[DB_CIRCUIT] CLOSED — database recovered");
                }
            }
        } else {
            consecutiveSuccesses.set(0);
        }
    }

    private void onFailure(Exception ex) {
        consecutiveSuccesses.set(0);
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= FAILURE_THRESHOLD) {
            if (state.compareAndSet(State.CLOSED, State.OPEN)
                    || state.compareAndSet(State.HALF_OPEN, State.OPEN)) {
                openedAt.set(Instant.now().toEpochMilli());
                openedCounter.increment();
                log.error("[DB_CIRCUIT] OPEN — database declared DOWN after {} failures. "
                        + "DB calls short-circuit for {}s. Last error: {}",
                        FAILURE_THRESHOLD, OPEN_DURATION.getSeconds(), ex.getMessage());
            }
        }
    }

    private boolean shouldAttemptReset() {
        long elapsed = Instant.now().toEpochMilli() - openedAt.get();
        return elapsed >= OPEN_DURATION.toMillis();
    }
}
