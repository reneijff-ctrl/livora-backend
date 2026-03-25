package com.joinlivora.backend.chat;

import com.joinlivora.backend.chat.dto.ChatMessageDto;
import com.joinlivora.backend.chat.dto.ChatMessageRequest;
import com.joinlivora.backend.user.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

import java.security.Principal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

@Controller
@Slf4j
public class ChatController {

    private final ChatMessageService chatMessageService;
    private final UserService userService;
    private final RedisTemplate<String, Object> redisTemplate;

    public ChatController(
            ChatMessageService chatMessageService,
            UserService userService,
            RedisTemplate<String, Object> redisTemplate) {
        this.chatMessageService = chatMessageService;
        this.userService = userService;
        this.redisTemplate = redisTemplate;
    }

    @GetMapping("/api/chat/history/{creatorId}")
    @ResponseBody
    public List<ChatMessageDto> getChatHistory(@PathVariable Long creatorId) {
        String key = "chat:history:" + creatorId;
        List<Object> history = redisTemplate.opsForList().range(key, 0, 4);
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        Collections.reverse(history);
        return history.stream()
                .filter(ChatMessageDto.class::isInstance)
                .map(o -> (ChatMessageDto) o)
                .toList();
    }

    @MessageMapping("/chat.send")
    public void sendChatMessage(ChatMessageRequest request, Principal principal) {
        if (principal == null) {
            throw new RuntimeException("User not authenticated");
        }

        com.joinlivora.backend.user.User user = userService.resolveUserFromSubject(principal.getName())
                .orElseThrow(() -> new RuntimeException("User not found: " + principal.getName()));

        chatMessageService.processIncomingMessage(request, user);
    }

    /**
     * Joins a chat room and emits a system message.
     */
    @MessageMapping("/chat.join")
    public void joinRoom(@Payload ChatMessageDto joinRequest, Principal principal, SimpMessageHeaderAccessor headerAccessor) {
        if (principal == null) {
            throw new RuntimeException("User not authenticated");
        }

        com.joinlivora.backend.user.User user = userService.resolveUserFromSubject(principal.getName())
                .orElseThrow(() -> new RuntimeException("User not found: " + principal.getName()));

        chatMessageService.processJoinRoom(joinRequest, user, headerAccessor.getSessionId());
    }

    @org.springframework.messaging.handler.annotation.MessageExceptionHandler
    @SendToUser("/queue/errors")
    public java.util.Map<String, Object> handleException(Exception e) {
        log.error("WS Chat Error: {}", e.getMessage());

        String errorCode = null;
        if (e instanceof com.joinlivora.backend.exception.ChatAccessException cae) {
            errorCode = cae.getErrorCode().name();
        }

        return java.util.Map.of(
                "error", e.getClass().getSimpleName(),
                "errorCode", errorCode != null ? errorCode : "",
                "message", e.getMessage(),
                "timestamp", Instant.now()
        );
    }
}
