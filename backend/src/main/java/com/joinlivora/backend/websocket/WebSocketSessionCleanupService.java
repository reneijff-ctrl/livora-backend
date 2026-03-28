package com.joinlivora.backend.websocket;

import com.joinlivora.backend.livestream.websocket.WebRTCSignalingController;
import com.joinlivora.backend.presence.service.PresenceTrackingService;
import com.joinlivora.backend.presence.service.SessionRegistryService;
import com.joinlivora.backend.presence.service.ViewerCountService;
import com.joinlivora.backend.streaming.client.MediasoupClient;
import com.joinlivora.backend.streaming.service.LiveViewerCounterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Map;
import java.util.Set;

/**
 * Centralized service for WebSocket session cleanup on disconnect.
 * Consolidates all cleanup logic that was previously spread across
 * WebSocketDisconnectListener and WebRTCSignalingController to ensure
 * cleanup executes exactly once per disconnect event.
 */
@Slf4j
@Service
public class WebSocketSessionCleanupService {

    private final LiveViewerCounterService liveViewerCounterService;
    private final MediasoupClient mediasoupClient;
    private final SessionRegistryService sessionRegistryService;
    private final ViewerCountService viewerCountService;
    private final PresenceTrackingService presenceTrackingService;
    private final WebRTCSignalingController signalingController;

    @Autowired
    public WebSocketSessionCleanupService(
            LiveViewerCounterService liveViewerCounterService,
            MediasoupClient mediasoupClient,
            SessionRegistryService sessionRegistryService,
            ViewerCountService viewerCountService,
            PresenceTrackingService presenceTrackingService,
            @Lazy WebRTCSignalingController signalingController) {
        this.liveViewerCounterService = liveViewerCounterService;
        this.mediasoupClient = mediasoupClient;
        this.sessionRegistryService = sessionRegistryService;
        this.viewerCountService = viewerCountService;
        this.presenceTrackingService = presenceTrackingService;
        this.signalingController = signalingController;
    }

    @EventListener
    @SuppressWarnings("unchecked")
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        if (sessionId == null) return;

        Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();

        // Atomic once-only guard: if "cleanupDone" is already set, skip cleanup
        if (sessionAttributes != null) {
            Object alreadyCleaned = sessionAttributes.putIfAbsent("cleanupDone", Boolean.TRUE);
            if (alreadyCleaned != null) {
                log.debug("SESSION-CLEANUP: Cleanup already performed for session {}, skipping", sessionId);
                return;
            }
        }

        log.info("SESSION-CLEANUP: Starting cleanup for session {}", sessionId);

        // 1. Rate-limiting and session-counting cleanup (from WebRTCSignalingController)
        signalingController.cleanupSessionRateLimiting(sessionId);

        // 2. Mediasoup Transport Cleanup (from WebSocketDisconnectListener)
        cleanupMediasoupTransports(sessionAttributes, sessionId);

        // 3. Viewer count decrement via session attributes (from WebSocketDisconnectListener)
        cleanupViewerCountFromSessionAttributes(sessionAttributes, headerAccessor);

        // 4. Livestream presence cleanup via SessionRegistryService (from WebRTCSignalingController)
        cleanupLivestreamPresence(sessionId);

        log.info("SESSION-CLEANUP: Completed cleanup for session {}", sessionId);
    }

    @SuppressWarnings("unchecked")
    private void cleanupMediasoupTransports(Map<String, Object> sessionAttributes, String sessionId) {
        if (sessionAttributes == null) return;

        if (sessionAttributes.containsKey("mediasoupTransports") && sessionAttributes.containsKey("mediasoupRoomId")) {
            Set<String> transports = (Set<String>) sessionAttributes.get("mediasoupTransports");
            String roomId = (String) sessionAttributes.get("mediasoupRoomId");
            if (transports != null && !transports.isEmpty()) {
                log.info("SESSION-CLEANUP: Cleaning up {} Mediasoup transports in room {}", transports.size(), roomId);
                transports.forEach(transportId -> mediasoupClient.closeTransport(roomId, transportId));
            }
            sessionAttributes.remove("mediasoupTransports");
        }
    }

    private void cleanupViewerCountFromSessionAttributes(Map<String, Object> sessionAttributes, SimpMessageHeaderAccessor headerAccessor) {
        if (sessionAttributes == null) return;

        if (sessionAttributes.containsKey("creatorUserId")) {
            Long creatorUserId = (Long) sessionAttributes.get("creatorUserId");
            Long userId = (Long) sessionAttributes.get("userId");
            Long streamSessionId = (Long) sessionAttributes.get("streamSessionId");

            if (creatorUserId != null && streamSessionId != null) {
                String sessionId = headerAccessor.getSessionId();
                String ip = (String) sessionAttributes.get("ip");
                String userAgent = (String) sessionAttributes.get("userAgent");
                log.info("SESSION-CLEANUP: Removing viewer from count for creatorUserId={} (streamSessionId={}, viewerId={}, sessionId={}, ip={}, userAgent={})",
                        creatorUserId, streamSessionId, userId != null ? userId : "unknown", sessionId, ip, userAgent);
                liveViewerCounterService.removeViewer(streamSessionId, creatorUserId, userId, sessionId, ip, userAgent);

                // DIAGNOSTICS
                String corrId = (String) sessionAttributes.get("corrId");
                log.info("event=DISCONNECT_DECREMENT corrId={} sessionId={} viewerId={} creatorUserId={} streamSessionId={}",
                        corrId, headerAccessor.getSessionId(), userId, creatorUserId, streamSessionId);

                // Ensure we don't decrement multiple times for the same session
                sessionAttributes.remove("creatorUserId");
                sessionAttributes.remove("streamSessionId");
            } else if (creatorUserId != null) {
                log.warn("SESSION-CLEANUP: No streamSessionId found for creatorUserId={} during disconnect. Viewer cleanup skipped.", creatorUserId);
            }
        }
    }

    private void cleanupLivestreamPresence(String sessionId) {
        Long userId = sessionRegistryService.getUserId(sessionId);
        Long creatorId = sessionRegistryService.getCreatorId(sessionId);
        String sessionIp = sessionRegistryService.getIp(sessionId);
        String userAgent = sessionRegistryService.getUserAgent(sessionId);
        Set<Long> streamSessionIds = sessionRegistryService.getJoinedStreams(sessionId);

        try {
            if (streamSessionIds != null && !streamSessionIds.isEmpty()) {
                log.info("SESSION-CLEANUP: Detected disconnected session {} with active joined streams", sessionId);
                for (Long streamSessionId : streamSessionIds) {
                    try {
                        viewerCountService.decrementViewerCount(streamSessionId, creatorId, userId, sessionId, sessionIp, userAgent);
                    } catch (Exception e) {
                        log.error("SESSION-CLEANUP: Failed to decrement viewer count for streamSessionId={} sessionId={} userId={}",
                                streamSessionId, sessionId, userId, e);
                    }
                }
            }

            // Remove presence entries
            if (userId != null) {
                try {
                    presenceTrackingService.markUserOffline(userId);
                } catch (Exception e) {
                    log.error("SESSION-CLEANUP: Failed to mark user offline userId={} sessionId={}", userId, sessionId, e);
                }
            }
        } finally {
            // Always unregister the session to prevent memory leaks, regardless of any failures above
            try {
                sessionRegistryService.unregisterSession(sessionId);
                log.debug("SESSION-CLEANUP: Successfully cleaned up session {} for userId {}", sessionId, userId);
            } catch (Exception e) {
                log.error("SESSION-CLEANUP: Failed to unregister session {} for userId {}", sessionId, userId, e);
            }
        }
    }
}
