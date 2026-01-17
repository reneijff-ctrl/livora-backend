package com.joinlivora.backend.streaming;

import com.joinlivora.backend.token.TokenService;
import com.joinlivora.backend.token.TransactionReason;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import com.joinlivora.backend.payout.MonetizationService;
import com.joinlivora.backend.payment.PaymentRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.Instant;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
@Slf4j
public class StreamChatController {

    private final SimpMessagingTemplate messagingTemplate;
    private final UserService userService;
    private final TokenService tokenService;
    private final StreamService streamService;
    private final MonetizationService monetizationService;
    private final PaymentRepository paymentRepository;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatMessage {
        private String userId;
        private String username;
        private String message;
        private boolean isPaid;
        private long amount;
        private String badgeType;
        private Instant createdAt;
    }

    @MessageMapping("/stream/{streamId}/chat")
    public void handleChatMessage(
            @DestinationVariable UUID streamId,
            @Payload ChatMessage message,
            Principal principal
    ) {
        if (principal == null) return;
        User user = userService.getByEmail(principal.getName());
        StreamRoom room = streamService.getRoom(streamId);

        message.setUserId(user.getId().toString());
        message.setUsername(user.getEmail());
        message.setCreatedAt(Instant.now());

        if (message.isPaid()) {
            if (message.getAmount() <= 0) {
                throw new RuntimeException("Paid message must have a positive amount");
            }
            
            // Deduct tokens
            tokenService.deductTokens(user, message.getAmount(), TransactionReason.CHAT, "Paid message in stream " + streamId);
            
            // In a real app, record earning for creator too
            // monetizationService.recordChatEarning(...)
            
            log.info("CHAT: Paid message from {} in stream {}: {} tokens", user.getEmail(), streamId, message.getAmount());
        } else {
            // Check if room has minimum chat tokens required
            if (room.getMinChatTokens() != null && room.getMinChatTokens() > 0) {
                // If it's not a paid message, maybe we check if they HAVE tokens? 
                // Or if it's "paid-only mode".
                // For now, let's assume if room.minChatTokens is set, ALL messages must be paid or user must be premium?
                // The requirement says "Enforce minChatTokens if set".
                // Let's treat it as minimum cost per message.
                throw new RuntimeException("This stream requires a minimum of " + room.getMinChatTokens() + " tokens per message.");
            }
        }

        // Broadcast to all subscribers of this stream's chat
        messagingTemplate.convertAndSend("/topic/stream/" + streamId + "/chat", message);
    }
}
