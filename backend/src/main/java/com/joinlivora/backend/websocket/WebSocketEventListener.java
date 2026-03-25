package com.joinlivora.backend.websocket;

import com.joinlivora.backend.analytics.AnalyticsEventType;
import com.joinlivora.backend.analytics.DomainAnalyticsEvent;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@Service
@Slf4j
public class WebSocketEventListener {

    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketEventListener(@org.springframework.context.annotation.Lazy SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @EventListener
    public void handleDomainAnalyticsEvent(DomainAnalyticsEvent event) {
        log.debug("WebSocket: Handling domain event for realtime broadcast: {}", event.getEventType());
        
        RealtimeMessage message = RealtimeMessage.of(
                event.getEventType().name().toLowerCase(),
                event.getMetadata()
        );

        User user = event.getUser();
        
        // 1. User-specific notifications
        if (user != null) {
            String destination = getDestinationForEventType(event.getEventType());
            if (destination != null) {
                messagingTemplate.convertAndSendToUser(
                        user.getId().toString(),
                        destination,
                        message
                );
            }
        }

        // 2. Admin-specific notifications
        broadcastToAdmins(event, message);

        // 3. Public/System status
        broadcastToPublic(event, message);
        
        // 4. Room-specific tips
        broadcastTipToRoom(event, message);
    }

    private void broadcastTipToRoom(DomainAnalyticsEvent event, RealtimeMessage message) {
        if (event.getEventType() == AnalyticsEventType.PAYMENT_SUCCEEDED) {
            Map<String, Object> metadata = event.getMetadata();
            if ("tip".equals(metadata.get("type")) && metadata.get("roomId") != null) {
                String roomId = String.valueOf(metadata.get("roomId"));
                long amount = 0;
                Object amountObj = metadata.get("amount");
                if (amountObj instanceof Number n) {
                    amount = n.longValue();
                } else if (amountObj != null) {
                    try {
                        amount = new java.math.BigDecimal(String.valueOf(amountObj)).longValue();
                    } catch (Exception e) {
                        log.warn("WebSocket: Could not parse amount: {}", amountObj);
                    }
                }
                
                RealtimeMessage tipBroadcast = RealtimeMessage.builder()
                        .type("ROOM_TIP")
                        .timestamp(Instant.now())
                        .payload(Map.of(
                                "senderEmail", event.getUser() != null ? event.getUser().getEmail().split("@")[0] : "Anonymous",
                                "amount", amount,
                                "animationType", getAnimationForAmount(amount)
                        ))
                        .build();
                
                messagingTemplate.convertAndSend("/exchange/amq.topic/rooms." + roomId + ".tips", tipBroadcast);
            }
        }
    }

    private String getAnimationForAmount(long amount) {
        if (amount >= 1000) return "fireworks";
        if (amount >= 500) return "diamond";
        if (amount >= 100) return "heart";
        return "coin";
    }

    private String getDestinationForEventType(AnalyticsEventType type) {
        return switch (type) {
            case PAYMENT_SUCCEEDED, PAYMENT_FAILED -> "/queue/payments";
            case SUBSCRIPTION_STARTED, SUBSCRIPTION_CANCELED -> "/queue/subscription";
            case USER_LOGIN_SUCCESS, USER_LOGIN_FAILED -> "/queue/notifications";
            default -> null;
        };
    }

    @EventListener
    public void handleSubscriptionLost(DomainAnalyticsEvent event) {
        if (event.getEventType() == AnalyticsEventType.SUBSCRIPTION_CANCELED || 
            event.getEventType() == AnalyticsEventType.PAYMENT_FAILED) {
            
            log.info("SECURITY: Subscription lost for creator {}, checking if watching any premium stream", event.getUser().getEmail());
            // In a real system, we'd find all active rooms and if the creator is a viewer, send a kill signal.
            // For now, we broadcast an ACCESS_DENIED signaling message to the creator's webrtc queue.
            messagingTemplate.convertAndSendToUser(
                event.getUser().getId().toString(),
                "/queue/webrtc",
                RealtimeMessage.builder()
                    .type("ACCESS_DENIED")
                    .timestamp(Instant.now())
                    .payload(Map.of("message", "Subscription expired. Stream access revoked."))
                    .build()
            );
        }
    }

    private void broadcastToAdmins(DomainAnalyticsEvent event, RealtimeMessage message) {
        AnalyticsEventType type = event.getEventType();
        if (type == AnalyticsEventType.PAYMENT_SUCCEEDED || 
            type == AnalyticsEventType.SUBSCRIPTION_STARTED) {
            messagingTemplate.convertAndSend("/exchange/amq.topic/admin.payments", message);
            messagingTemplate.convertAndSend("/exchange/amq.topic/admin.metrics", Map.of(
                    "event", type.name(),
                    "timestamp", message.getTimestamp()
            ));
        }
    }

    private void broadcastToPublic(DomainAnalyticsEvent event, RealtimeMessage message) {
        // Example: Broadcast system announcements or maintenance (not usually from analytics events)
        // But for this task, we can show a placeholder if needed.
    }
}
