package com.joinlivora.backend.controller;

import com.joinlivora.backend.websocket.RealtimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/debug")
@Slf4j
public class DebugController {

    private final SimpMessagingTemplate messagingTemplate;

    public DebugController(@org.springframework.context.annotation.Lazy SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @PostMapping("/notify-me")
    public ResponseEntity<?> notifyMe(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("message", "Not authenticated"));
        }

        String username = principal.getName();
        log.info("Debug: Sending test notification to creator: {}", username);

        messagingTemplate.convertAndSendToUser(
                username,
                "/queue/notifications",
                RealtimeMessage.of("debug", Map.of("message", "Hello " + username + "! This is a realtime test notification."))
        );

        return ResponseEntity.ok(Map.of("message", "Notification sent"));
    }
}
