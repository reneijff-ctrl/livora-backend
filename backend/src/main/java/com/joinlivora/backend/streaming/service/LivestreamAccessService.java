package com.joinlivora.backend.streaming.service;

import com.joinlivora.backend.privateshow.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LivestreamAccessService {

    private static final String ACCESS_KEY_PREFIX = "access:";

    private final StringRedisTemplate redisTemplate;
    private final com.joinlivora.backend.livestream.repository.LivestreamSessionRepository sessionRepository;
    private final PrivateSessionRepository privateSessionRepository;
    private final PrivateSpySessionRepository privateSpySessionRepository;

    /**
     * Checks access using a per-viewer session key: access:{sessionId}:{viewerUserId}
     * Returns true if the session is free or if the viewer has been granted access.
     * Also enforces private session isolation: during an ACTIVE private session,
     * only the creator, the assigned private viewer, and active spy viewers are allowed.
     */
    public boolean hasAccess(Long sessionId, Long viewerUserId) {
        if (sessionId == null) return false;

        // Fetch session
        var sessionOpt = sessionRepository.findById(sessionId);
        if (sessionOpt.isEmpty()) return false;
        var session = sessionOpt.get();

        Long creatorId = session.getCreator().getId();

        // 1. Creators always have access to their own stream
        if (creatorId.equals(viewerUserId)) return true;

        // 2. Check if creator has an ACTIVE private session — block unauthorized viewers
        Optional<PrivateSession> activePrivate = privateSessionRepository
                .findFirstByCreator_IdAndStatusOrderByStartedAtDesc(creatorId, PrivateSessionStatus.ACTIVE);

        if (activePrivate.isPresent()) {
            PrivateSession ps = activePrivate.get();

            // Allow the assigned private viewer
            if (viewerUserId != null && viewerUserId.equals(ps.getViewer().getId())) {
                return true;
            }

            // Allow active spy viewers
            if (viewerUserId != null && privateSpySessionRepository
                    .existsBySpyViewer_IdAndPrivateSession_IdAndStatus(viewerUserId, ps.getId(), SpySessionStatus.ACTIVE)) {
                return true;
            }

            // Deny everyone else during active private session
            log.info("STREAM-ACCESS BLOCKED: viewer {} denied during active private session {} for creator {}",
                    viewerUserId != null ? viewerUserId : "anonymous", ps.getId(), creatorId);
            return false;
        }

        // 3. No active private session — apply normal access rules
        // Check if the session is paid. If free, access is granted.
        if (session.isFree()) {
            return true;
        }

        // 4. For paid streams, we need a valid viewer ID
        if (viewerUserId == null) return false;

        try {
            String key = ACCESS_KEY_PREFIX + sessionId + ":" + viewerUserId;
            Boolean exists = redisTemplate.hasKey(key);
            return Boolean.TRUE.equals(exists);
        } catch (DataAccessException ex) {
            log.warn("Redis access check failed for sessionId={} viewerUserId={}: {}", sessionId, viewerUserId, ex.getMessage());
            return false;
        }
    }

    /**
     * Grants access to a viewer for a specific session using a TTL.
     * Key: access:{sessionId}:{viewerUserId}
     */
    public void grantAccess(Long sessionId, Long viewerUserId, Duration duration) {
        if (sessionId == null || viewerUserId == null || duration == null) return;
        try {
            String key = ACCESS_KEY_PREFIX + sessionId + ":" + viewerUserId;
            redisTemplate.opsForValue().set(key, "true", duration);
            log.info("LIVESTREAM-ACCESS: Granted access for sessionId={} to viewerUserId={} for {}", 
                    sessionId, viewerUserId, duration);
        } catch (DataAccessException ex) {
            log.error("Failed to grant access in Redis for sessionId={} viewerUserId={}: {}", 
                    sessionId, viewerUserId, ex.getMessage());
        }
    }
}
