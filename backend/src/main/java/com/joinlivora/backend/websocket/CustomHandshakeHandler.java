package com.joinlivora.backend.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;

/**
 * Custom handshake handler to assign a Principal to the WebSocket session.
 * It uses the attributes populated by JwtHandshakeInterceptor.
 */
@Component
@Slf4j
public class CustomHandshakeHandler extends DefaultHandshakeHandler {

    @Override
    protected Principal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String username = (String) attributes.get("senderEmail");
        String userId = (String) attributes.get("creator");

        if (username != null) {
            log.debug("Assigning StompPrincipal to WebSocket session: senderEmail={}, creator={}", username, userId);
            return new StompPrincipal(username, userId);
        }

        log.debug("No senderEmail found in attributes, falling back to default creator determination");
        return super.determineUser(request, wsHandler, attributes);
    }
}
