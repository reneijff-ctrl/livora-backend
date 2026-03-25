package com.joinlivora.backend.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class WebRtcSessionRegistry {

    private final RedisTemplate<String, String> redisTemplate;
    private static final String HASH_KEY = "webrtc:sessions";

    public WebRtcSessionRegistry(@Autowired(required = false) RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        if (redisTemplate == null) {
            log.warn("WebRtcSessionRegistry: RedisTemplate<String, String> not found. This is expected in 'dev' profile if Redis is disabled.");
        }
    }

    /**
     * Checks if a viewer is already connected with the given session ID.
     *
     * @param sessionId The WebSocket session ID to check
     * @param viewerId  The ID of the viewer
     * @return true if already connected with this session ID
     */
    public boolean isViewerAlreadyConnected(String sessionId, Long viewerId) {
        String existing = getSession(viewerId);
        return sessionId != null && sessionId.equals(existing);
    }

    /**
     * Registers a viewer's WebSocket session ID in Redis.
     *
     * @param sessionId The WebSocket session ID
     * @param viewerId  The ID of the viewer
     */
    public void registerViewer(String sessionId, Long viewerId) {
        registerViewer(viewerId, sessionId);
    }

    /**
     * Registers a viewer's WebSocket session ID in Redis.
     *
     * @param viewerId  The ID of the viewer
     * @param sessionId The WebSocket session ID
     */
    public void registerViewer(Long viewerId, String sessionId) {
        if (redisTemplate == null || viewerId == null || sessionId == null) {
            return;
        }
        log.info("WEBRTC-REGISTRY: Registering viewerId={} with sessionId={}", viewerId, sessionId);
        redisTemplate.opsForHash().put(HASH_KEY, viewerId.toString(), sessionId);
    }

    /**
     * Retrieves the WebSocket session ID for a viewer.
     *
     * @param viewerId The ID of the viewer
     * @return The session ID, or null if not found
     */
    public String getSession(Long viewerId) {
        if (redisTemplate == null || viewerId == null) {
            return null;
        }
        Object session = redisTemplate.opsForHash().get(HASH_KEY, viewerId.toString());
        return session != null ? session.toString() : null;
    }

    /**
     * Removes a viewer from the registry.
     *
     * @param viewerId The ID of the viewer to remove
     */
    public void removeViewer(Long viewerId) {
        if (redisTemplate == null || viewerId == null) {
            return;
        }
        log.info("WEBRTC-REGISTRY: Removing viewerId={}", viewerId);
        redisTemplate.opsForHash().delete(HASH_KEY, viewerId.toString());
    }
}
