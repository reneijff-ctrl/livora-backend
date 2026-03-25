package com.joinlivora.backend.controller;

import com.joinlivora.backend.websocket.ChatMessage;
import com.joinlivora.backend.websocket.RealtimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/debug")
@Slf4j
public class DebugChatController {

    private final SimpMessagingTemplate messagingTemplate;

    public DebugChatController(@org.springframework.context.annotation.Lazy SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @PostMapping("/chat-test")
    public ResponseEntity<?> sendTestChatMessage() {
        log.info("Debug: Sending system test message to /exchange/amq.topic/chat.public");

        ChatMessage systemMessage = ChatMessage.builder()
                .id("public-" + java.util.UUID.randomUUID().toString())
                .senderEmail("SYSTEM")
                .content("This is a system test message for the public chat.")
                .timestamp(Instant.now())
                .build();

        RealtimeMessage realtimeMessage = RealtimeMessage.ofChat(systemMessage);

        messagingTemplate.convertAndSend("/exchange/amq.topic/chat.public", realtimeMessage);

        return ResponseEntity.ok(Map.of("message", "System chat message sent to public room"));
    }
}
