package com.joinlivora.backend.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.security.Principal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class PresenceService {

    private final SimpMessagingTemplate messagingTemplate;
    // Map of session ID to user email
    private final Map<String, String> activeSessions = new ConcurrentHashMap<>();

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectEvent event) {
        Principal principal = event.getUser();
        if (principal != null) {
            String email = principal.getName();
            String sessionId = (String) event.getMessage().getHeaders().get("simpSessionId");
            activeSessions.put(sessionId, email);
            log.info("WebSocket: User {} connected (Session: {})", email, sessionId);
            
            broadcastPresence();
            
            // Broadcast join event to admin topic
            messagingTemplate.convertAndSend("/topic/admin/activity", RealtimeMessage.of("user_join", Map.of("email", email)));
        }
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        String email = activeSessions.remove(sessionId);
        
        if (email != null) {
            log.info("WebSocket: User {} disconnected (Session: {})", email, sessionId);
            broadcastPresence();
            
            // Broadcast leave event to admin topic
            messagingTemplate.convertAndSend("/topic/admin/activity", RealtimeMessage.of("user_leave", Map.of("email", email)));
        }
    }

    @EventListener
    public void handleSubscriptionEvent(SessionSubscribeEvent event) {
        Principal principal = event.getUser();
        String destination = (String) event.getMessage().getHeaders().get("simpDestination");
        
        if (principal != null && "/topic/premium".equals(destination)) {
            // Role-based gating logic could also be here, but STOMP interceptors are better.
            // For this implementation, we rely on ChatController gating for sending, 
            // and we could add an interceptor for subscription gating.
            log.debug("User {} subscribed to {}", principal.getName(), destination);
        }
    }

    private void broadcastPresence() {
        // Broadcast unique online users count
        long onlineCount = activeSessions.values().stream().distinct().count();
        messagingTemplate.convertAndSend("/topic/presence", Map.of(
                "onlineCount", onlineCount,
                "timestamp", java.time.Instant.now()
        ));
    }
}
