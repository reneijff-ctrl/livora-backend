package com.joinlivora.backend.monetization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joinlivora.backend.admin.service.AdminRealtimeEventService;
import com.joinlivora.backend.analytics.AnalyticsEventPublisher;
import com.joinlivora.backend.analytics.AnalyticsEventType;
import com.joinlivora.backend.chat.ChatModerationService;
import com.joinlivora.backend.chat.dto.ChatMessageDto;
import com.joinlivora.backend.chat.dto.ChatMessageResponse;
import com.joinlivora.backend.reputation.model.ReputationEventSource;
import com.joinlivora.backend.reputation.model.ReputationEventType;
import com.joinlivora.backend.reputation.service.ReputationEventService;
import com.joinlivora.backend.streaming.service.StreamAssistantBotService;
import com.joinlivora.backend.streaming.service.StreamModerationService;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.websocket.LiveEvent;
import com.joinlivora.backend.websocket.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class TipNotificationService {

    private final AnalyticsEventPublisher analyticsEventPublisher;
    @Lazy private final SimpMessagingTemplate messagingTemplate;
    private final ReputationEventService reputationEventService;
    private final ChatModerationService chatModerationService;
    private final StreamModerationService streamModerationService;
    private final StreamAssistantBotService streamAssistantBotService;
    private final SuperTipHighlightTracker highlightTracker;
    private final PresenceService presenceService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final TipGoalService tipGoalService;
    private final TipActionService tipActionService;
    private final AdminRealtimeEventService adminRealtimeEventService;
    private final WeeklyTipService weeklyTipService;

    public void notifyTip(Tip tip, String giftName) {
        // Analytics
        analyticsEventPublisher.publishEvent(
                AnalyticsEventType.PAYMENT_SUCCEEDED,
                tip.getSenderUserId(),
                Map.of(
                        "type", "tip",
                        "amount", tip.getAmount(),
                        "currency", tip.getCurrency(),
                        "creator", tip.getCreatorUserId().getId(),
                        "tipId", tip.getId() != null ? tip.getId() : ""
                )
        );

        // Reputation
        reputationEventService.recordEvent(
                new UUID(0L, tip.getCreatorUserId().getId()),
                ReputationEventType.TIP,
                2,
                ReputationEventSource.SYSTEM,
                Map.of("tipId", tip.getId() != null ? tip.getId() : "", "amount", tip.getAmount(), "currency", tip.getCurrency())
        );

        // Weekly leaderboard
        weeklyTipService.registerTip(tip.getCreatorUserId().getId(), tip.getSenderUserId().getUsername(), tip.getAmount());

        // WebSocket notifications
        sendWebSocketNotifications(tip, giftName);
    }

    private void sendWebSocketNotifications(Tip tip, String giftName) {
        User tipper = tip.getSenderUserId();
        Long creatorUserId = tip.getCreatorUserId().getId();
        String username = tipper.getUsername();

        if (tip.getRoom() != null) {
            String content = username + " tipped " + tip.getAmount() + " " + tip.getCurrency() + " 💎";
            String resolvedAnimation = getAnimationForAmount(tip.getAmount().longValue());
            String resolvedRarity = resolveRarityForAmount(tip.getAmount().longValue());

            ChatMessageResponse response = ChatMessageResponse.builder()
                    .messageId(UUID.randomUUID().toString())
                    .type("TIP")
                    .senderId(tipper.getId())
                    .senderUsername(username)
                    .senderRole(tipper.getRole().name())
                    .content(content)
                    .amount(tip.getAmount().intValue())
                    .timestamp(Instant.now())
                    .animationType(giftName != null ? resolvedAnimation : resolvedAnimation)
                    .rarity(resolvedRarity)
                    .giftName(giftName)
                    .soundProfile(resolvedRarity)
                    .build();

            if (chatModerationService.isShadowMuted(tipper.getId(), tip.getRoom().getId().toString()) ||
                    streamModerationService.isShadowMuted(creatorUserId, tipper.getId())) {
                messagingTemplate.convertAndSendToUser(tipper.getId().toString(), "/queue/chat", response);
            } else {
                messagingTemplate.convertAndSend("/exchange/amq.topic/chat." + creatorUserId, response);
                // Broadcast to dedicated monetization stream
                messagingTemplate.convertAndSend("/exchange/amq.topic/monetization." + creatorUserId,
                        LiveEvent.of(response.getMessageId(), "TIP", response));
            }

            // Pinning for high value tips
            if (tip.getAmount().compareTo(new BigDecimal("100")) > 0) {
                ChatMessageDto pinDto = ChatMessageDto.builder()
                        .id(response.getMessageId())
                        .type("TIP")
                        .senderId(tipper.getId())
                        .senderUsername(username)
                        .senderRole(tipper.getRole().name())
                        .content(content)
                        .amount(tip.getAmount().intValue())
                        .currency(tip.getCurrency())
                        .timestamp(response.getTimestamp())
                        .build();
                pinTipMessage(creatorUserId, pinDto);
                // Broadcast PIN_MESSAGE to dedicated monetization stream
                messagingTemplate.convertAndSend("/exchange/amq.topic/monetization." + creatorUserId,
                        LiveEvent.of(pinDto.getId(), "PIN_MESSAGE", pinDto));
            }

            // Bot Response
            String botDonorName = tipper.getDisplayName() != null ? tipper.getDisplayName() : tipper.getEmail().split("@")[0];
            streamAssistantBotService.onTipReceived(creatorUserId, botDonorName, tip.getAmount().doubleValue(), tip.getCurrency());

            // Tip Goal and Actions
            long tokens = getAmountInTokens(tip);
            tipGoalService.processTip(creatorUserId, tokens);
            tipActionService.checkAction(creatorUserId, tokens, botDonorName);
        }

        // Notify creator
        messagingTemplate.convertAndSendToUser(
                creatorUserId.toString(),
                "/queue/notifications",
                Map.of(
                        "type", "NEW_TIP",
                        "payload", Map.of(
                                "amount", tip.getAmount(),
                                "currency", tip.getCurrency(),
                                "senderId", tipper.getId(),
                                "senderUsername", tipper.getUsername(),
                                "message", tip.getMessage() != null ? tip.getMessage() : ""
                        )
                )
        );

        // Notify creator via private WebSocket queue (legacy/special format)
        messagingTemplate.convertAndSendToUser(creatorUserId.toString(), "/queue/tips", Map.of(
                "type", "TIP",
                "viewerId", tipper.getId(),
                "username", tipper.getUsername(),
                "displayName", tipper.getDisplayName() != null ? tipper.getDisplayName() : tipper.getUsername(),
                "amount", tip.getAmount(),
                "currency", tip.getCurrency(),
                "giftName", giftName != null ? giftName : "",
                "message", tip.getMessage() != null ? tip.getMessage() : "",
                "timestamp", tip.getCreatedAt() != null ? tip.getCreatedAt() : Instant.now(),
                "animationType", getAnimationForAmount(tip.getAmount().longValue())
        ));
    }

    public void pinTipMessage(Long creatorId, ChatMessageDto tipData) {
        String redisKey = String.format("stream:%d:pinned", creatorId);
        try {
            String json = objectMapper.writeValueAsString(tipData);
            redisTemplate.opsForValue().set(redisKey, json);

            messagingTemplate.convertAndSend("/exchange/amq.topic/chat." + creatorId, tipData);
            log.info("MONETIZATION: Pinned tip message for creator {}: {}", creatorId, json);
        } catch (Exception e) {
            log.error("Failed to pin tip message to Redis", e);
        }
    }

    public String getAnimationForAmount(long amount) {
        if (amount >= 1000) return "fireworks";
        if (amount >= 500) return "diamond";
        if (amount >= 100) return "heart";
        return "coin";
    }

    /**
     * Resolves rarity tier for a given token amount.
     * Must stay in sync with frontend resolveTipTier() in animationUtils.ts.
     */
    private String resolveRarityForAmount(long amount) {
        if (amount >= 500) return "legendary";
        if (amount >= 200) return "epic";
        if (amount >= 50) return "rare";
        return "common";
    }

    private long getAmountInTokens(Tip tip) {
        if ("TOKEN".equalsIgnoreCase(tip.getCurrency()) || "TOKENS".equalsIgnoreCase(tip.getCurrency())) {
            return tip.getAmount().longValue();
        } else {
            return tip.getAmount().multiply(new BigDecimal("100")).longValue();
        }
    }
}
