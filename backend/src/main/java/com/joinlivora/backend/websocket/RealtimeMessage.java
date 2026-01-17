package com.joinlivora.backend.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
public class RealtimeMessage {
    private String type;
    private java.time.Instant timestamp;
    private java.util.Map<String, Object> payload;
    private ChatMessage chatMessage;

    public static RealtimeMessage of(String type, java.util.Map<String, Object> payload) {
        return RealtimeMessage.builder()
                .type(type)
                .timestamp(java.time.Instant.now())
                .payload(payload)
                .build();
    }

    public static RealtimeMessage ofChat(ChatMessage chatMessage) {
        return RealtimeMessage.builder()
                .type("chat")
                .timestamp(java.time.Instant.now())
                .chatMessage(chatMessage)
                .build();
    }
}
