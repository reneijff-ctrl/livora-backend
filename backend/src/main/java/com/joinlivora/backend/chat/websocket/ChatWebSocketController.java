package com.joinlivora.backend.chat.websocket;

import com.joinlivora.backend.chat.domain.ChatMessage;
import com.joinlivora.backend.chat.domain.ChatRoom;
import com.joinlivora.backend.chat.domain.ChatRoomStatus;
import com.joinlivora.backend.chat.repository.ChatMessageRepository;
import com.joinlivora.backend.chat.repository.ChatRoomRepository;
import com.joinlivora.backend.exception.BusinessException;
import com.joinlivora.backend.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Controller("v2ChatWebSocketController")
@RequiredArgsConstructor
@Slf4j
public class ChatWebSocketController {

    private final com.joinlivora.backend.chat.repository.ChatRoomRepository chatRoomRepositoryV2;
    private final com.joinlivora.backend.user.UserService userService;
    private final com.joinlivora.backend.chat.ChatMessageService chatMessageService;

    @MessageMapping("/v2/chat.send")
    public void handleChatMessage(@Payload ChatInboundMessage payload, java.security.Principal principal) {
        if (principal == null) {
            throw new RuntimeException("User not authenticated");
        }

        com.joinlivora.backend.user.User user = userService.resolveUserFromSubject(principal.getName())
                .orElseThrow(() -> new RuntimeException("User not found: " + principal.getName()));

        log.debug("Chat V2: Received message for room {} from {}: {}", payload.getRoomId(), user.getUsername(), payload.getContent());

        // Delegate to ChatMessageService for unified validation, persistence, and broadcasting
        com.joinlivora.backend.chat.dto.ChatMessageDto dto = com.joinlivora.backend.chat.dto.ChatMessageDto.builder()
                .roomId(new java.util.UUID(0L, payload.getRoomId()))
                .content(payload.getContent())
                .senderId(user.getId())
                .senderUsername(user.getUsername())
                .senderRole(user.getRole().name())
                .type("CHAT")
                .build();

        chatMessageService.processMessage(user.getId(), dto);
    }

    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    public Map<String, Object> handleException(Exception e) {
        log.warn("Chat V2 Error: {}", e.getMessage());
        Map<String, Object> response = new HashMap<>();
        String errorCode = "";
        if (e instanceof BusinessException be) {
            // Convention: "CODE: message"
            String msg = be.getMessage();
            int idx = msg != null ? msg.indexOf(':') : -1;
            if (idx > 0) {
                errorCode = msg.substring(0, idx).trim();
            }
        }
        response.put("error", e.getClass().getSimpleName());
        response.put("errorCode", errorCode);
        response.put("message", e.getMessage());
        response.put("timestamp", Instant.now());
        return response;
    }
}
