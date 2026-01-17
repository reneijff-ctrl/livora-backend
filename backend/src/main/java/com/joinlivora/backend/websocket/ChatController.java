package com.joinlivora.backend.websocket;

import com.joinlivora.backend.payment.SubscriptionService;
import com.joinlivora.backend.payment.SubscriptionStatus;
import com.joinlivora.backend.payment.dto.SubscriptionResponse;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.Instant;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final SimpMessagingTemplate messagingTemplate;
    private final UserService userService;
    private final SubscriptionService subscriptionService;

    @MessageMapping("/chat.send")
    public void sendMessage(@Payload ChatMessage chatMessage, Principal principal) {
        if (principal == null) {
            log.warn("WebSocket: Anonymous message attempt rejected");
            return;
        }

        User user = userService.getByEmail(principal.getName());
        chatMessage.setId(UUID.randomUUID().toString());
        chatMessage.setSenderEmail(user.getEmail());
        chatMessage.setSenderId(user.getId().toString());
        chatMessage.setTimestamp(Instant.now());

        if (chatMessage.getRoomType() == ChatMessage.RoomType.PREMIUM) {
            if (!hasPremiumAccess(user)) {
                log.warn("SECURITY: User {} attempted to send message to PREMIUM room without active subscription", user.getEmail());
                messagingTemplate.convertAndSendToUser(
                        user.getEmail(),
                        "/queue/errors",
                        RealtimeMessage.of("access_denied", java.util.Map.of("message", "Premium subscription required"))
                );
                return;
            }
            messagingTemplate.convertAndSend("/topic/premium", RealtimeMessage.ofChat(chatMessage));
        } else {
            messagingTemplate.convertAndSend("/topic/public", RealtimeMessage.ofChat(chatMessage));
        }
    }

    private boolean hasPremiumAccess(User user) {
        if (user.getRole() == com.joinlivora.backend.user.Role.ADMIN) {
            return true;
        }
        SubscriptionResponse sub = subscriptionService.getSubscriptionForUser(user);
        return sub != null && sub.getStatus() == SubscriptionStatus.ACTIVE;
    }
}
