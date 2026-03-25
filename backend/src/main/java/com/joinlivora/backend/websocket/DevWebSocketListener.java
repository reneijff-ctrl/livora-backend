package com.joinlivora.backend.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.security.Principal;

/**
 * Temporary WebSocket listener for development purposes.
 * Logs information when a creator subscribes to the test topic.
 */
@Component
@Slf4j
@Profile("dev")
public class DevWebSocketListener {

    @EventListener
    public void handleTestTopicSubscription(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String destination = accessor.getDestination();

        if ("/exchange/amq.topic/test".equals(destination)) {
            Principal principal = event.getUser();
            String username = (principal != null) ? principal.getName() : "Anonymous";

            log.info("[DEV] User '{}' subscribed to /exchange/amq.topic/test", username);
            log.info("[DEV] Connected creator Principal information: {}", principal);
        }
    }
}
