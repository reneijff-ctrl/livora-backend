package com.joinlivora.backend.chat.websocket;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatOutboundMessage {
    private Long id;
    private Long roomId;
    private Long senderId;
    private String senderUsername;
    private String senderRole;
    private String content;
    private String type;
    private Instant timestamp;
}
