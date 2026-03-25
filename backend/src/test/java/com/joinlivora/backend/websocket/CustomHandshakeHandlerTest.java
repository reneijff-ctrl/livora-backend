package com.joinlivora.backend.websocket;

import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class CustomHandshakeHandlerTest {

    private final CustomHandshakeHandler handler = new CustomHandshakeHandler();

    @Test
    void determineUser_WithAttributes_ShouldReturnStompPrincipal() {
        // Arrange
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        WebSocketHandler wsHandler = mock(WebSocketHandler.class);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("username", "test@creator.com");
        attributes.put("userId", "12345");

        // Act
        Principal principal = handler.determineUser(request, wsHandler, attributes);

        // Assert
        assertNotNull(principal);
        assertTrue(principal instanceof StompPrincipal);
        StompPrincipal stompPrincipal = (StompPrincipal) principal;
        assertEquals("test@creator.com", stompPrincipal.getName());
        assertEquals("12345", stompPrincipal.getUserId());
    }

    @Test
    void determineUser_WithoutAttributes_ShouldReturnDefault() {
        // Arrange
        ServerHttpRequest request = mock(ServerHttpRequest.class);
        WebSocketHandler wsHandler = mock(WebSocketHandler.class);
        Map<String, Object> attributes = new HashMap<>();

        // Act
        Principal principal = handler.determineUser(request, wsHandler, attributes);

        // Assert - Default behavior of DefaultHandshakeHandler is to return null if no creator is authenticated via other means
        assertNull(principal);
    }
}








