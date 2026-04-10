package com.joinlivora.backend.streaming.service;

import com.joinlivora.backend.privateshow.*;
import com.joinlivora.backend.resilience.DatabaseCircuitBreakerService;
import com.joinlivora.backend.resilience.RedisCircuitBreakerService;
import com.joinlivora.backend.streaming.StreamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class LivestreamAccessService {

    private static final String ACCESS_KEY_PREFIX = "access:";

    private final StringRedisTemplate redisTemplate;
    private final StreamRepository streamRepository;
    private final PrivateSessionRepository privateSessionRepository;
    private final PrivateSpySessionRepository privateSpySessionRepository;
    private final RedisCircuitBreakerService redisCircuitBreaker;
    private final DatabaseCircuitBreakerService dbCircuitBreaker;

    // Metric: track UUID-based access checks
    private final AtomicLong metricUuidAccessChecks = new AtomicLong(0);

    // -------------------------------------------------------------------------
    // UUID-based API (Stream as single source of truth)
    // -------------------------------------------------------------------------

    /**
     * Checks access using Stream UUID: access:{streamId}:{viewerUserId}
     */
    public boolean hasAccess(UUID streamId, Long viewerUserId) {
        if (streamId == null) return false;

        var streamOpt = dbCircuitBreaker.executeOptional(
                () -> streamRepository.findById(streamId), "findStreamForAccess");
        if (streamOpt.isEmpty()) return false;
        var stream = streamOpt.get();

        Long creatorId = stream.getCreator() != null ? stream.getCreator().getId() : null;

        if (creatorId != null && creatorId.equals(viewerUserId)) return true;

        if (!enforcePrivateSessionAccess(creatorId, viewerUserId)) return false;

        if (!stream.isPaid()) return true;

        if (viewerUserId == null) return false;

        String key = ACCESS_KEY_PREFIX + streamId + ":" + viewerUserId;
        Boolean exists = redisCircuitBreaker.execute(
                () -> redisTemplate.hasKey(key), Boolean.FALSE);
        if (Boolean.TRUE.equals(exists)) {
            metricUuidAccessChecks.incrementAndGet();
            return true;
        }
        return false;
    }

    /**
     * Grants access using Stream UUID key: access:{streamId}:{viewerUserId}
     * Throws {@link org.springframework.dao.DataAccessException} on Redis failure so callers can retry.
     */
    public void grantAccess(UUID streamId, Long viewerUserId, Duration duration) {
        if (streamId == null || viewerUserId == null || duration == null) return;
        String key = ACCESS_KEY_PREFIX + streamId + ":" + viewerUserId;
        boolean granted = redisCircuitBreaker.execute(
                (Runnable) () -> redisTemplate.opsForValue().set(key, "true", duration));
        if (granted) {
            log.info("LIVESTREAM-ACCESS: Granted access for streamId={} to viewerUserId={} for {}", streamId, viewerUserId, duration);
        } else {
            // Circuit is open or Redis failed — propagate so caller's retry logic fires
            throw new org.springframework.dao.QueryTimeoutException("Redis circuit OPEN: grantAccess failed for streamId=" + streamId);
        }
    }

    // -------------------------------------------------------------------------
    // Cleanup
    // -------------------------------------------------------------------------

    /**
     * Removes the UUID-based access key on stream stop.
     */
    public void revokeAccess(UUID streamId, Long viewerUserId) {
        if (streamId == null || viewerUserId == null) return;
        String key = ACCESS_KEY_PREFIX + streamId + ":" + viewerUserId;
        redisCircuitBreaker.execute(
                (Runnable) () -> redisTemplate.delete(key));
        log.debug("LIVESTREAM-ACCESS: Revoked access key for streamId={} viewerUserId={}", streamId, viewerUserId);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Enforces private session isolation: returns false if viewer is blocked during
     * an active private session. Returns true if access may continue.
     */
    private boolean enforcePrivateSessionAccess(Long creatorId, Long viewerUserId) {
        if (creatorId == null) return true;

        final Long finalCreatorId = creatorId;
        Optional<PrivateSession> activePrivate = dbCircuitBreaker.executeOptional(
                () -> privateSessionRepository.findFirstByCreator_IdAndStatusOrderByStartedAtDesc(
                        finalCreatorId, PrivateSessionStatus.ACTIVE),
                "findPrivateSession");

        if (activePrivate.isPresent()) {
            PrivateSession ps = activePrivate.get();

            if (viewerUserId != null && viewerUserId.equals(ps.getViewer().getId())) return true;

            if (viewerUserId != null) {
                boolean isSpy = dbCircuitBreaker.execute(
                        () -> privateSpySessionRepository.existsBySpyViewer_IdAndPrivateSession_IdAndStatus(
                                viewerUserId, ps.getId(), SpySessionStatus.ACTIVE),
                        Boolean.FALSE,
                        "checkSpySession");
                if (Boolean.TRUE.equals(isSpy)) return true;
            }

            log.info("STREAM-ACCESS BLOCKED: viewer {} denied during active private session {} for creator {}",
                    viewerUserId != null ? viewerUserId : "anonymous", ps.getId(), creatorId);
            return false;
        }
        return true;
    }

    /**
     * Returns a snapshot of Redis access key usage metrics for monitoring.
     */
    public Map<String, Long> getRedisKeyMetrics() {
        return Map.of(
                "uuidAccessChecks", metricUuidAccessChecks.get()
        );
    }
}
