package com.joinlivora.backend.presence.service;

import com.joinlivora.backend.analytics.AnalyticsEventPublisher;
import com.joinlivora.backend.analytics.AnalyticsEventType;
import com.joinlivora.backend.chat.domain.ChatRoom;
import com.joinlivora.backend.chat.domain.ChatRoomStatus;
import com.joinlivora.backend.chat.service.ChatRoomService;
import com.joinlivora.backend.monetization.TipGoalService;
import com.joinlivora.backend.streaming.service.StreamAssistantBotService;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.websocket.ChatMessage;
import com.joinlivora.backend.websocket.RealtimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for orchestrating cross-domain events related to presence.
 * Responsibilities:
 * - notifications (activity, presence)
 * - analytics events
 * - chat updates (activation/status)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PresenceEventOrchestrator {

    private final SimpMessagingTemplate messagingTemplate;
    private final AnalyticsEventPublisher analyticsEventPublisher;
    private final StreamAssistantBotService streamAssistantBotService;
    private final ChatRoomService chatRoomServiceV2;
    private final BrokerAvailabilityListener brokerAvailabilityListener;
    private final TipGoalService tipGoalService;

    public void broadcastPresence(long onlineUsersCount) {
        if (messagingTemplate != null && brokerAvailabilityListener.isBrokerAvailable()) {
            messagingTemplate.convertAndSend("/exchange/amq.topic/presence", Map.of("onlineUsers", onlineUsersCount));
        } else if (messagingTemplate != null) {
            log.debug("Skipping broadcastPresence: broker not active");
        }
    }

    public void publishJoinEvent(String principalName, String email) {
        if (messagingTemplate != null && brokerAvailabilityListener.isBrokerAvailable()) {
            String activityEmail = (email != null) ? email : principalName;
            messagingTemplate.convertAndSend("/exchange/amq.topic/admin.activity", RealtimeMessage.of("user_join", Map.of("email", activityEmail)));
        } else if (messagingTemplate != null) {
            log.debug("Skipping publishJoinEvent: broker not active");
        }
    }

    public void publishLeaveEvent(String principalName, String email) {
        if (messagingTemplate != null && brokerAvailabilityListener.isBrokerAvailable()) {
            String activityEmail = (email != null) ? email : principalName;
            messagingTemplate.convertAndSend("/exchange/amq.topic/admin.activity", RealtimeMessage.of("user_leave", Map.of("email", activityEmail)));
        } else if (messagingTemplate != null) {
            log.debug("Skipping publishLeaveEvent: broker not active");
        }
    }

    public void publishAnalyticsEvent(AnalyticsEventType type, User user, Map<String, Object> properties) {
        if (analyticsEventPublisher != null) {
            analyticsEventPublisher.publishEvent(type, user, properties);
        }
    }

    public void notifyStreamJoin(Long creatorUserId, String viewerDisplayName) {
        notifyStreamJoin(creatorUserId, viewerDisplayName, null);
    }

    public void notifyStreamJoin(Long creatorUserId, String viewerDisplayName, String principalId) {
        if (streamAssistantBotService != null) {
            streamAssistantBotService.onUserJoined(creatorUserId, viewerDisplayName);
        }
        if (tipGoalService != null) {
            try {
                tipGoalService.broadcastActiveGoalGroup(creatorUserId);
            } catch (Exception e) {
                log.warn("Failed to broadcast active goal group on viewer join: {}", e.getMessage());
            }
        }
    }

    public java.util.List<ChatRoom> activateChatRooms(Long creatorId) {
        return chatRoomServiceV2 != null ? chatRoomServiceV2.activateWaitingRooms(creatorId) : java.util.Collections.emptyList();
    }

    public java.util.List<ChatRoom> pauseChatRooms(Long creatorId) {
        return chatRoomServiceV2 != null ? chatRoomServiceV2.pauseActiveRooms(creatorId) : java.util.Collections.emptyList();
    }

    public void notifyChatRoomStatus(String principalId, String chatTopic, Long chatRoomId, Long creatorUserId, Long creatorId, String status) {
        if (messagingTemplate != null && principalId != null && brokerAvailabilityListener.isBrokerAvailable()) {
            Map<String, Object> statusPayload = Map.of(
                    "chatRoomId", chatRoomId,
                    "creatorUserId", creatorUserId,
                    "creator", creatorId,
                    "status", status
            );
            messagingTemplate.convertAndSendToUser(principalId, "/queue/chat-status", statusPayload);
            log.debug("Sent private status update to viewer {} for room {}", principalId, chatRoomId);
        } else if (messagingTemplate != null && principalId != null) {
            log.debug("Skipping notifyChatRoomStatus: broker not active");
        }
    }

    public void broadcastCreatorPresence(Long creatorUserId, Long creatorId, boolean online) {
        if (messagingTemplate != null && brokerAvailabilityListener.isBrokerAvailable()) {
            try {
                Map<String, Object> payload = Map.of(
                        "creatorUserId", creatorUserId,
                        "creatorId", creatorId,
                        "status", online ? "ONLINE" : "OFFLINE",
                        "timestamp", System.currentTimeMillis()
                );

                messagingTemplate.convertAndSend("/exchange/amq.topic/creators.presence", payload);
                messagingTemplate.convertAndSend("/exchange/amq.topic/creators.presence." + creatorUserId, payload);
                log.debug("Broadcasted creator {}: userId={}, creatorId={}", online ? "ONLINE" : "OFFLINE", creatorUserId, creatorId);
            } catch (Exception e) {
                log.warn("Failed to broadcast creator presence: {}", e.getMessage());
            }
        } else if (messagingTemplate != null) {
            log.debug("Skipping broadcastCreatorPresence: broker not active");
        }
    }

    public void broadcastCreatorLeft(Long creatorUserId, Long creatorId) {
        if (messagingTemplate != null && brokerAvailabilityListener.isBrokerAvailable()) {
            RealtimeMessage leftEvent = RealtimeMessage.of("chat:creator:left", java.util.Map.of(
                    "creatorUserId", creatorUserId,
                    "creator", creatorId
            ));
            messagingTemplate.convertAndSend("/exchange/amq.topic/chat.v2.creator." + creatorUserId + ".status", leftEvent);
        } else if (messagingTemplate != null) {
            log.debug("Skipping broadcastCreatorLeft: broker not active");
        }
    }

    public void broadcastChatStateUpdate(Long creatorUserId, Long creatorId, Long roomId, ChatRoomStatus status) {
        if (messagingTemplate != null && brokerAvailabilityListener.isBrokerAvailable()) {
            RealtimeMessage msg = RealtimeMessage.builder()
                    .type("chat:state:update")
                    .timestamp(Instant.now())
                    .payload(java.util.Map.of(
                            "creatorUserId", creatorUserId,
                            "creator", creatorId,
                            "roomId", roomId,
                            "status", status.name()
                    ))
                    .build();
            messagingTemplate.convertAndSend("/exchange/amq.topic/chat.v2.creator." + creatorUserId + ".status", msg);
        } else if (messagingTemplate != null) {
            log.debug("Skipping broadcastChatStateUpdate: broker not active");
        }
    }

    public void broadcastSystemMessage(Long creatorUserId, String content) {
        if (messagingTemplate != null && brokerAvailabilityListener.isBrokerAvailable()) {
            ChatMessage systemMessage = ChatMessage.builder()
                    .id(java.util.UUID.randomUUID().toString())
                    .content(content)
                    .system(true)
                    .timestamp(Instant.now())
                    .build();
            messagingTemplate.convertAndSend("/exchange/amq.topic/chat." + creatorUserId, RealtimeMessage.ofChat(systemMessage));
        } else if (messagingTemplate != null) {
            log.debug("Skipping broadcastSystemMessage: broker not active");
        }
    }
}
