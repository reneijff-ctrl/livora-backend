package com.joinlivora.backend.chat.websocket;

import lombok.Data;

@Data
public class ChatInboundMessage {
    private Long roomId;
    private Long senderId;
    private String senderRole;
    private String content;
}
