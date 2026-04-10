package com.joinlivora.backend.presence.service;

import com.joinlivora.backend.config.MetricsService;
import com.joinlivora.backend.resilience.RedisCircuitBreakerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Service for managing WebSocket sessions and their associated metadata.
 * Thin wrapper over RedisSessionRegistryService — Redis is the single source of truth.
 * All calls are protected by {@link RedisCircuitBreakerService}: if Redis goes down,
 * the circuit opens and all session reads/writes fail-open immediately rather than
 * hammering the unavailable instance.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SessionRegistryService {

    private final RedisSessionRegistryService redisRegistry;
    private final MetricsService metricsService;
    private final RedisCircuitBreakerService redisCircuitBreaker;

    // -------------------------------------------------------------------------
    // Mutations — delegate to Redis via circuit breaker
    // -------------------------------------------------------------------------

    public void registerSession(String sessionId, String principalName, Long userId, Long creatorId, String ip, String userAgent) {
        boolean ok = redisCircuitBreaker.execute(() -> {
            redisRegistry.registerSession(sessionId, principalName, userId, creatorId, ip, userAgent);
            long count = redisRegistry.getActiveSessionsCount();
            metricsService.setWsSessionsActive(count);
            metricsService.setWsSessionsCount(count);
        });
        if (!ok) {
            log.warn("REDIS WRITE FAILED [registerSession] sessionId={}: circuit open", sessionId);
            metricsService.getRedisFailuresTotal().increment();
        } else {
            log.debug("Session registered: {}", sessionId);
        }
    }

    public void unregisterSession(String sessionId) {
        boolean ok = redisCircuitBreaker.execute(() -> {
            redisRegistry.unregisterSession(sessionId);
            long count = redisRegistry.getActiveSessionsCount();
            metricsService.setWsSessionsActive(count);
            metricsService.setWsSessionsCount(count);
        });
        if (!ok) {
            log.warn("REDIS WRITE FAILED [unregisterSession] sessionId={}: circuit open", sessionId);
            metricsService.getRedisFailuresTotal().increment();
        } else {
            log.debug("Session unregistered: {}", sessionId);
        }
    }

    public boolean addSubscription(String sessionId, String subscriptionId, String destination) {
        return Boolean.TRUE.equals(redisCircuitBreaker.execute(
                () -> redisRegistry.addSubscription(sessionId, subscriptionId, destination),
                Boolean.FALSE));
    }

    public String removeSubscription(String sessionId, String subscriptionId) {
        return redisCircuitBreaker.execute(
                () -> redisRegistry.removeSubscription(sessionId, subscriptionId),
                null);
    }

    public void trackJoinTime(String sessionId, String destination, Instant joinTime) {
        redisCircuitBreaker.execute(
                () -> redisRegistry.trackJoinTime(sessionId, destination, joinTime));
    }

    public Instant removeJoinTime(String sessionId, String destination) {
        return redisCircuitBreaker.execute(
                () -> redisRegistry.removeJoinTime(sessionId, destination),
                null);
    }

    public boolean markStreamJoined(String sessionId, Long streamSessionId) {
        if (streamSessionId == null) return false;
        return Boolean.TRUE.equals(redisCircuitBreaker.execute(
                () -> redisRegistry.markStreamJoined(sessionId, streamSessionId),
                Boolean.FALSE));
    }

    public boolean markStreamLeft(String sessionId, Long streamSessionId) {
        if (streamSessionId == null) return false;
        return Boolean.TRUE.equals(redisCircuitBreaker.execute(
                () -> redisRegistry.markStreamLeft(sessionId, streamSessionId),
                Boolean.FALSE));
    }

    // -------------------------------------------------------------------------
    // Reads — Redis via circuit breaker, fail-open on OPEN state
    // -------------------------------------------------------------------------

    public String getPrincipal(String sessionId) {
        return redisCircuitBreaker.execute(
                () -> redisRegistry.getPrincipal(sessionId),
                null);
    }

    public Long getUserId(String sessionId) {
        return redisCircuitBreaker.execute(
                () -> redisRegistry.getUserId(sessionId),
                null);
    }

    public Long getCreatorId(String sessionId) {
        return redisCircuitBreaker.execute(
                () -> redisRegistry.getCreatorId(sessionId),
                null);
    }

    public String getIp(String sessionId) {
        return redisCircuitBreaker.execute(
                () -> redisRegistry.getIp(sessionId),
                null);
    }

    public String getUserAgent(String sessionId) {
        return redisCircuitBreaker.execute(
                () -> redisRegistry.getUserAgent(sessionId),
                null);
    }

    public boolean isSubscribedTo(String sessionId, String destination) {
        return Boolean.TRUE.equals(redisCircuitBreaker.execute(
                () -> redisRegistry.isSubscribedTo(sessionId, destination),
                Boolean.FALSE));
    }

    public Set<String> getSubscriptions(String sessionId) {
        @SuppressWarnings("unchecked")
        Set<String> value = (Set<String>) redisCircuitBreaker.execute(
                () -> redisRegistry.getSubscriptions(sessionId),
                Collections.emptySet());
        return value != null ? value : Collections.emptySet();
    }

    public Instant getJoinTime(String sessionId, String destination) {
        return redisCircuitBreaker.execute(
                () -> redisRegistry.getJoinTime(sessionId, destination),
                null);
    }

    public Set<Long> getJoinedStreams(String sessionId) {
        @SuppressWarnings("unchecked")
        Set<Long> value = (Set<Long>) redisCircuitBreaker.execute(
                () -> redisRegistry.getJoinedStreams(sessionId),
                Collections.emptySet());
        return value != null ? value : Collections.emptySet();
    }

    public Map<String, String> getAllActiveSessions() {
        @SuppressWarnings("unchecked")
        Map<String, String> value = (Map<String, String>) redisCircuitBreaker.execute(
                () -> redisRegistry.getAllActiveSessions(),
                Collections.emptyMap());
        return value != null ? value : Collections.emptyMap();
    }

    public long getActiveSessionsCount() {
        Long value = redisCircuitBreaker.execute(
                () -> redisRegistry.getActiveSessionsCount(),
                0L);
        return value != null ? value : 0L;
    }

    /**
     * Returns the session IDs registered on this pod only.
     * O(SCARD) on the per-pod set — much cheaper than SMEMBERS on the global set.
     */
    public Set<String> getPodSessionIds() {
        @SuppressWarnings("unchecked")
        Set<String> value = (Set<String>) redisCircuitBreaker.execute(
                () -> redisRegistry.getPodSessionIds(),
                Collections.emptySet());
        return value != null ? value : Collections.emptySet();
    }

    /**
     * Returns the global session count from the INCR/DECR counter — O(1).
     */
    public long getGlobalSessionCount() {
        Long value = redisCircuitBreaker.execute(
                () -> redisRegistry.getGlobalSessionCount(),
                0L);
        return value != null ? value : 0L;
    }
}
