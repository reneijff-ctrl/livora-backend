package com.joinlivora.backend.websocket;

import com.joinlivora.backend.livestream.websocket.WebRTCSignalingController;
import com.joinlivora.backend.presence.service.PresenceTrackingService;
import com.joinlivora.backend.presence.service.SessionRegistryService;
import com.joinlivora.backend.presence.service.ViewerCountService;
import com.joinlivora.backend.streaming.client.MediasoupClient;
import com.joinlivora.backend.streaming.service.LiveViewerCounterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WebSocketSessionCleanupServiceTest {

    @Mock private LiveViewerCounterService liveViewerCounterService;
    @Mock private MediasoupClient mediasoupClient;
    @Mock private SessionRegistryService sessionRegistryService;
    @Mock private ViewerCountService viewerCountService;
    @Mock private PresenceTrackingService presenceTrackingService;
    @Mock private WebRTCSignalingController signalingController;

    private WebSocketSessionCleanupService cleanupService;

    private static final String SESSION_ID = "test-session-123";

    @BeforeEach
    void setUp() {
        cleanupService = new WebSocketSessionCleanupService(
                liveViewerCounterService,
                mediasoupClient,
                sessionRegistryService,
                viewerCountService,
                presenceTrackingService,
                signalingController
        );

        // Default: no joined streams in registry
        when(sessionRegistryService.getJoinedStreams(anyString())).thenReturn(Collections.emptySet());
    }

    @SuppressWarnings("unchecked")
    private SessionDisconnectEvent createDisconnectEvent(Map<String, Object> sessionAttributes) {
        Map<String, Object> headers = new HashMap<>();
        headers.put("simpSessionId", SESSION_ID);
        if (sessionAttributes != null) {
            headers.put("simpSessionAttributes", sessionAttributes);
        }
        Message<byte[]> message = mock(Message.class);
        when(message.getHeaders()).thenReturn(new MessageHeaders(headers));
        SessionDisconnectEvent event = mock(SessionDisconnectEvent.class);
        when(event.getMessage()).thenReturn(message);
        when(event.getSessionId()).thenReturn(SESSION_ID);
        return event;
    }

    @Test
    void handleSessionDisconnect_fullCleanup_mediasoupTransportsAndViewerCount() {
        UUID streamId = UUID.randomUUID();
        Set<String> transports = new HashSet<>(Set.of("transport-1", "transport-2"));
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("mediasoupTransports", transports);
        attrs.put("mediasoupRoomId", "room-abc");
        attrs.put("creatorUserId", 10L);
        attrs.put("userId", 5L);
        attrs.put("streamSessionId", streamId.toString()); // UUID string (V2 path)
        attrs.put("ip", "127.0.0.1");
        attrs.put("userAgent", "TestAgent");
        attrs.put("corrId", "corr-1");

        when(mediasoupClient.closeTransport(anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        SessionDisconnectEvent event = createDisconnectEvent(attrs);
        cleanupService.handleSessionDisconnect(event);

        // Verify rate-limiting cleanup
        verify(signalingController).cleanupSessionRateLimiting(SESSION_ID);

        // Verify mediasoup transport cleanup
        verify(mediasoupClient).closeTransport("room-abc", "transport-1");
        verify(mediasoupClient).closeTransport("room-abc", "transport-2");

        // Verify viewer count decrement using UUID-based path
        verify(liveViewerCounterService).removeViewer(streamId, 10L, 5L, SESSION_ID, "127.0.0.1", "TestAgent");
    }

    @Test
    void handleSessionDisconnect_onceOnlyGuard_secondCallSkipsCleanup() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("creatorUserId", 10L);
        attrs.put("streamSessionId", UUID.randomUUID().toString());

        SessionDisconnectEvent event = createDisconnectEvent(attrs);

        // First call should perform cleanup
        cleanupService.handleSessionDisconnect(event);
        verify(signalingController, times(1)).cleanupSessionRateLimiting(SESSION_ID);

        // Second call should be skipped (cleanupDone is already set)
        cleanupService.handleSessionDisconnect(event);
        verify(signalingController, times(1)).cleanupSessionRateLimiting(SESSION_ID);
    }

    @Test
    void handleSessionDisconnect_nullSessionId_skipsCleanup() {
        Map<String, Object> headers = new HashMap<>();
        // No simpSessionId
        Message<byte[]> message = mock(Message.class);
        when(message.getHeaders()).thenReturn(new MessageHeaders(headers));
        SessionDisconnectEvent event = mock(SessionDisconnectEvent.class);
        when(event.getMessage()).thenReturn(message);

        cleanupService.handleSessionDisconnect(event);

        verifyNoInteractions(signalingController);
        verifyNoInteractions(mediasoupClient);
        verifyNoInteractions(liveViewerCounterService);
    }

    @Test
    void handleSessionDisconnect_nullSessionAttributes_stillCleansRateLimiting() {
        SessionDisconnectEvent event = createDisconnectEvent(null);

        cleanupService.handleSessionDisconnect(event);

        // Rate-limiting cleanup should still run (no guard needed without attributes)
        verify(signalingController).cleanupSessionRateLimiting(SESSION_ID);

        // No mediasoup or viewer cleanup
        verifyNoInteractions(mediasoupClient);
        verifyNoInteractions(liveViewerCounterService);
    }

    @Test
    void handleSessionDisconnect_livestreamPresenceCleanup_viaSessionRegistry() {
        // With V2 migration, legacy Long-based joined streams are no longer decremented.
        // Presence cleanup (markUserOffline + unregisterSession) still runs.
        Map<String, Object> attrs = new HashMap<>();

        Long userId = 42L;
        Long creatorId = 7L;
        Set<Long> joinedStreams = Set.of(200L, 300L);  // legacy Long IDs — no longer acted upon

        when(sessionRegistryService.getUserId(SESSION_ID)).thenReturn(userId);
        when(sessionRegistryService.getCreatorId(SESSION_ID)).thenReturn(creatorId);
        when(sessionRegistryService.getIp(SESSION_ID)).thenReturn("10.0.0.1");
        when(sessionRegistryService.getUserAgent(SESSION_ID)).thenReturn("Agent");
        when(sessionRegistryService.getJoinedStreams(SESSION_ID)).thenReturn(joinedStreams);

        SessionDisconnectEvent event = createDisconnectEvent(attrs);
        cleanupService.handleSessionDisconnect(event);

        // Legacy Long IDs in joinedStreams are ignored after V2 migration — no decrementViewerCount calls
        verifyNoInteractions(viewerCountService);

        // Presence cleanup still runs
        verify(presenceTrackingService).markUserOffline(userId);
        verify(sessionRegistryService).unregisterSession(SESSION_ID);
    }

    @Test
    void handleSessionDisconnect_noMediasoupTransports_skipsTransportCleanup() {
        Map<String, Object> attrs = new HashMap<>();
        // No mediasoupTransports or mediasoupRoomId

        SessionDisconnectEvent event = createDisconnectEvent(attrs);
        cleanupService.handleSessionDisconnect(event);

        verifyNoInteractions(mediasoupClient);
    }

    @Test
    void handleSessionDisconnect_noCreatorUserId_skipsViewerDecrement() {
        Map<String, Object> attrs = new HashMap<>();
        // No creatorUserId

        SessionDisconnectEvent event = createDisconnectEvent(attrs);
        cleanupService.handleSessionDisconnect(event);

        verifyNoInteractions(liveViewerCounterService);
    }

    @Test
    void handleSessionDisconnect_creatorUserIdButNoStreamSessionId_skipsViewerDecrement() {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("creatorUserId", 10L);
        // No streamSessionId

        SessionDisconnectEvent event = createDisconnectEvent(attrs);
        cleanupService.handleSessionDisconnect(event);

        verifyNoInteractions(liveViewerCounterService);
    }

    @Test
    void handleSessionDisconnect_noJoinedStreams_stillUnregistersSession() {
        Map<String, Object> attrs = new HashMap<>();

        when(sessionRegistryService.getJoinedStreams(SESSION_ID)).thenReturn(Collections.emptySet());

        SessionDisconnectEvent event = createDisconnectEvent(attrs);
        cleanupService.handleSessionDisconnect(event);

        // Viewer count should NOT be decremented (no joined streams)
        verifyNoInteractions(viewerCountService);

        // Session should ALWAYS be unregistered to prevent memory leaks
        verify(sessionRegistryService).unregisterSession(SESSION_ID);
    }
}
