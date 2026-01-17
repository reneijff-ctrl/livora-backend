package com.joinlivora.backend.websocket;

import com.joinlivora.backend.analytics.DomainAnalyticsEvent;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventListener {

    private final SimpMessagingTemplate messagingTemplate;

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
                        user.getEmail(),
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
        if (event.getEventType() == com.joinlivora.backend.analytics.AnalyticsEventType.PAYMENT_SUCCEEDED) {
            Map<String, Object> metadata = event.getMetadata();
            if ("tip".equals(metadata.get("type"))) {
                String roomId = String.valueOf(metadata.get("roomId"));
                long amount = Long.parseLong(String.valueOf(metadata.get("amount")));
                
                RealtimeMessage tipBroadcast = RealtimeMessage.builder()
                        .type("ROOM_TIP")
                        .timestamp(java.time.Instant.now())
                        .payload(Map.of(
                                "username", event.getUser().getEmail().split("@")[0],
                                "amount", amount,
                                "animationType", getAnimationForAmount(amount)
                        ))
                        .build();
                
                messagingTemplate.convertAndSend("/topic/rooms/" + roomId + "/tips", tipBroadcast);
            }
        }
    }

    private String getAnimationForAmount(long amount) {
        if (amount >= 1000) return "fireworks";
        if (amount >= 500) return "diamond";
        if (amount >= 100) return "heart";
        return "coin";
    }

    private String getDestinationForEventType(com.joinlivora.backend.analytics.AnalyticsEventType type) {
        return switch (type) {
            case PAYMENT_SUCCEEDED, PAYMENT_FAILED -> "/queue/payments";
            case SUBSCRIPTION_STARTED, SUBSCRIPTION_CANCELED -> "/queue/subscription";
            case USER_LOGIN_SUCCESS, USER_LOGIN_FAILED -> "/queue/notifications";
            default -> null;
        };
    }

    @EventListener
    public void handleSubscriptionLost(com.joinlivora.backend.analytics.DomainAnalyticsEvent event) {
        if (event.getEventType() == com.joinlivora.backend.analytics.AnalyticsEventType.SUBSCRIPTION_CANCELED || 
            event.getEventType() == com.joinlivora.backend.analytics.AnalyticsEventType.PAYMENT_FAILED) {
            
            log.info("SECURITY: Subscription lost for user {}, checking if watching any premium stream", event.getUser().getEmail());
            // In a real system, we'd find all active rooms and if the user is a viewer, send a kill signal.
            // For now, we broadcast an ACCESS_DENIED signaling message to the user's webrtc queue.
            messagingTemplate.convertAndSendToUser(
                event.getUser().getEmail(),
                "/queue/webrtc",
                RealtimeMessage.builder()
                    .type("ACCESS_DENIED")
                    .timestamp(java.time.Instant.now())
                    .payload(java.util.Map.of("message", "Subscription expired. Stream access revoked."))
                    .build()
            );
        }
    }

    private void broadcastToAdmins(DomainAnalyticsEvent event, RealtimeMessage message) {
        com.joinlivora.backend.analytics.AnalyticsEventType type = event.getEventType();
        if (type == com.joinlivora.backend.analytics.AnalyticsEventType.PAYMENT_SUCCEEDED || 
            type == com.joinlivora.backend.analytics.AnalyticsEventType.SUBSCRIPTION_STARTED) {
            messagingTemplate.convertAndSend("/topic/admin/payments", message);
            messagingTemplate.convertAndSend("/topic/admin/metrics", Map.of(
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
