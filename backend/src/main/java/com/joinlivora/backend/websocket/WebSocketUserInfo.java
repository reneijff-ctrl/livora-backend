package com.joinlivora.backend.websocket;

/**
 * Lightweight DTO stored in WebSocket session attributes.
 * Replaces storing a JPA User entity to avoid LazyInitializationException on WebSocket threads.
 */
public record WebSocketUserInfo(
        Long id,
        String email,
        String role,
        String status
) {}
