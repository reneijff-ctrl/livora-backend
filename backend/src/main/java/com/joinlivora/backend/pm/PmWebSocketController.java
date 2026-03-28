package com.joinlivora.backend.pm;

import com.joinlivora.backend.chat.domain.ChatMessage;
import com.joinlivora.backend.chat.domain.ChatRoom;
import com.joinlivora.backend.chat.domain.ChatRoomType;
import com.joinlivora.backend.chat.repository.ChatMessageRepository;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@Slf4j
public class PmWebSocketController {

    private final PmService pmService;
    private final UserService userService;
    private final ChatMessageRepository chatMessageRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    @MessageMapping("/v2/pm.send")
    public void handlePmMessage(@Payload PmInboundMessage payload, Principal principal) {
        if (principal == null) {
            throw new RuntimeException("User not authenticated");
        }

        if (payload.getContent() == null || payload.getContent().trim().isEmpty()) {
            throw new IllegalArgumentException("Message content cannot be empty");
        }

        User user = userService.resolveUserFromSubject(principal.getName())
                .orElseThrow(() -> new RuntimeException("User not found: " + principal.getName()));

        ChatRoom room = pmService.getRoomById(payload.getRoomId());

        if (room.getRoomType() != ChatRoomType.PM) {
            throw new RuntimeException("Not a PM room");
        }

        Long userId = user.getId();
        if (!userId.equals(room.getCreatorId()) && !userId.equals(room.getViewerId())) {
            throw new RuntimeException("Not a participant of this PM session");
        }

        ChatMessage message = ChatMessage.builder()
                .roomId(room.getId())
                .senderId(userId)
                .senderRole(user.getRole().name())
                .content(payload.getContent())
                .build();

        chatMessageRepository.save(message);

        pmService.updateReadStateOnMessage(room.getId(), userId, message.getId());

        Map<String, Object> messagePayload = Map.of(
                "type", "PM_MESSAGE",
                "roomId", room.getId(),
                "senderId", userId,
                "senderUsername", user.getUsername(),
                "senderRole", user.getRole().name(),
                "content", payload.getContent(),
                "createdAt", message.getCreatedAt().toString()
        );

        try {
            messagingTemplate.convertAndSendToUser(
                    room.getCreatorId().toString(),
                    "/queue/pm-messages",
                    messagePayload
            );
        } catch (Exception e) {
            log.warn("Failed to send PM to creator: {}", e.getMessage());
        }

        try {
            messagingTemplate.convertAndSendToUser(
                    room.getViewerId().toString(),
                    "/queue/pm-messages",
                    messagePayload
            );
        } catch (Exception e) {
            log.warn("Failed to send PM to viewer: {}", e.getMessage());
        }

        log.debug("PM message sent in room {} by user {}", room.getId(), userId);
    }

    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    public Map<String, Object> handleException(Exception e) {
        log.warn("PM WebSocket Error: {}", e.getMessage());
        Map<String, Object> response = new HashMap<>();
        response.put("error", e.getClass().getSimpleName());
        response.put("message", e.getMessage());
        response.put("timestamp", Instant.now());
        return response;
    }

    @Data
    public static class PmInboundMessage {
        private Long roomId;
        private String content;
    }
}
