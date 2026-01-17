package com.joinlivora.backend.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    private String id;
    private String senderId;
    private String senderEmail;
    private String content;
    private Instant timestamp;
    private RoomType roomType;

    public enum RoomType {
        PUBLIC, PREMIUM
    }
}
