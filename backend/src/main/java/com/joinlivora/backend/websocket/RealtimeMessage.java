package com.joinlivora.backend.websocket;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
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
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RealtimeMessage {
    private String type;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant timestamp;
    private Map<String, Object> payload;
    private ChatMessage chatMessage;

    public static RealtimeMessage of(String type, Map<String, Object> payload) {
        return RealtimeMessage.builder()
                .type(type)
                .timestamp(Instant.now())
                .payload(payload)
                .build();
    }

    public static RealtimeMessage ofChat(ChatMessage chatMessage) {
        return RealtimeMessage.builder()
                .type("chat")
                .timestamp(Instant.now())
                .chatMessage(chatMessage)
                .build();
    }
}
