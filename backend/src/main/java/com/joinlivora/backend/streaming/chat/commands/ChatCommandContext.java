package com.joinlivora.backend.streaming.chat.commands;

import com.joinlivora.backend.websocket.LiveEvent;
import lombok.Builder;
import lombok.Getter;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@Getter
@Builder
public class ChatCommandContext {
    private final Long creatorId;
    private final String senderPrincipalName;
    private final String senderUsername;
    private final String fullMessage;
    private final SimpMessagingTemplate messagingTemplate;

    public void sendToRoom(Object payload) {
        messagingTemplate.convertAndSend("/exchange/amq.topic/chat." + creatorId, payload);
    }

    public void sendToMonetization(String type, Object payload) {
        messagingTemplate.convertAndSend("/exchange/amq.topic/monetization." + creatorId,
                LiveEvent.of(type, payload));
    }

    public void sendToUser(Object payload) {
        messagingTemplate.convertAndSendToUser(senderPrincipalName, "/queue/errors", payload);
    }
}
