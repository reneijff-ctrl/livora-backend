package com.joinlivora.backend.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;

@Controller
@Slf4j
public class WebSocketTestController {

    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketTestController(@org.springframework.context.annotation.Lazy SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Echo test: Sends a message back to the creator who sent it.
     * Destination: /app/test/echo
     */
    @MessageMapping("/test/echo")
    public void echo(String message, Principal principal) {
        String username = principal != null ? principal.getName() : "anonymous";
        log.info("WebSocket: Echo request from {}: {}", username, message);
        
        messagingTemplate.convertAndSendToUser(
                username,
                "/queue/notifications",
                RealtimeMessage.of("echo", Map.of("content", message, "from", username))
        );
    }

    /**
     * Admin broadcast test: Only admins should be able to trigger this.
     * Destination: /app/admin/broadcast
     */
    @MessageMapping("/admin/broadcast")
    @PreAuthorize("hasRole('ADMIN')")
    public void adminBroadcast(String message, Principal principal) {
        log.info("WebSocket: Admin broadcast from {}: {}", principal.getName(), message);
        
        messagingTemplate.convertAndSend(
                "/exchange/amq.topic/announcements",
                RealtimeMessage.of("announcement", Map.of("content", message, "admin", principal.getName()))
        );
    }
}
