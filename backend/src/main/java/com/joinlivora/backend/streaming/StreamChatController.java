package com.joinlivora.backend.streaming;

import com.joinlivora.backend.chat.dto.ModerateResult;
import com.joinlivora.backend.chat.dto.ModerationSeverity;
import com.joinlivora.backend.websocket.RealtimeMessage;
import com.joinlivora.backend.monetization.dto.SuperTipRequest;
import com.joinlivora.backend.exception.SuperTipException;
import com.joinlivora.backend.exception.ChatAccessException;
import com.joinlivora.backend.exception.UserRestrictedException;
import com.joinlivora.backend.exception.BusinessException;
import com.joinlivora.backend.exception.InsufficientBalanceException;
import com.joinlivora.backend.abuse.model.RestrictionLevel;
import com.joinlivora.backend.abuse.model.UserRestriction;
import com.joinlivora.backend.token.TokenService;
import com.joinlivora.backend.wallet.WalletTransactionType;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import com.joinlivora.backend.payout.CreatorEarningsService;
import com.joinlivora.backend.payment.PaymentRepository;
import com.joinlivora.backend.streaming.service.StreamModerationService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Controller
@Slf4j
public class StreamChatController {

    private final SimpMessagingTemplate messagingTemplate;
    private final UserService userService;
    private final TokenService tokenService;
    private final StreamService streamService;
    private final CreatorEarningsService creatorEarningsService;
    private final PaymentRepository paymentRepository;
    private final com.joinlivora.backend.chat.ChatModerationService moderationService;
    private final com.joinlivora.backend.chat.service.ChatRoomService chatRoomService;
    private final com.joinlivora.backend.chat.ChatTipService chatTipService;
    private final com.joinlivora.backend.chat.SlowModeBypassService slowModeBypassService;
    private final com.joinlivora.backend.chat.ChatRateLimitService chatRateLimitService;
    private final com.joinlivora.backend.monetization.HighlightedMessageService highlightedMessageService;
    private final com.joinlivora.backend.analytics.AnalyticsEventPublisher analyticsEventPublisher;
    private final com.joinlivora.backend.abuse.AbuseDetectionService abuseDetectionService;
    private final com.joinlivora.backend.abuse.RestrictionService restrictionService;
    private final StreamModerationService streamModerationService;
    private final com.joinlivora.backend.streaming.service.StreamAssistantBotService streamAssistantBotService;

    // Optional V2 services for state gating (not required for tests/legacy wiring)
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.joinlivora.backend.livestream.service.LiveStreamService liveStreamServiceV2;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.joinlivora.backend.chat.repository.ChatRoomRepository chatRoomRepositoryV2;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.joinlivora.backend.creator.repository.CreatorRepository creatorRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.joinlivora.backend.presence.service.CreatorPresenceService creatorPresenceService;

    public StreamChatController(
            @org.springframework.context.annotation.Lazy SimpMessagingTemplate messagingTemplate,
            UserService userService,
            TokenService tokenService,
            StreamService streamService,
            CreatorEarningsService creatorEarningsService,
            PaymentRepository paymentRepository,
            com.joinlivora.backend.chat.ChatModerationService moderationService,
            com.joinlivora.backend.chat.service.ChatRoomService chatRoomService,
            com.joinlivora.backend.chat.ChatTipService chatTipService,
            com.joinlivora.backend.chat.SlowModeBypassService slowModeBypassService,
            com.joinlivora.backend.chat.ChatRateLimitService chatRateLimitService,
            com.joinlivora.backend.monetization.HighlightedMessageService highlightedMessageService,
            @org.springframework.context.annotation.Lazy com.joinlivora.backend.analytics.AnalyticsEventPublisher analyticsEventPublisher,
            com.joinlivora.backend.abuse.AbuseDetectionService abuseDetectionService,
            com.joinlivora.backend.abuse.RestrictionService restrictionService,
            StreamModerationService streamModerationService,
            com.joinlivora.backend.streaming.service.StreamAssistantBotService streamAssistantBotService) {
        this.messagingTemplate = messagingTemplate;
        this.userService = userService;
        this.tokenService = tokenService;
        this.streamService = streamService;
        this.creatorEarningsService = creatorEarningsService;
        this.paymentRepository = paymentRepository;
        this.moderationService = moderationService;
        this.chatRoomService = chatRoomService;
        this.chatTipService = chatTipService;
        this.slowModeBypassService = slowModeBypassService;
        this.chatRateLimitService = chatRateLimitService;
        this.highlightedMessageService = highlightedMessageService;
        this.analyticsEventPublisher = analyticsEventPublisher;
        this.abuseDetectionService = abuseDetectionService;
        this.restrictionService = restrictionService;
        this.streamModerationService = streamModerationService;
        this.streamAssistantBotService = streamAssistantBotService;
    }


    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatTipRequest {
        @Min(value = 1, message = "Amount must be at least 1")
        private long amount;
        private String message;
        @NotBlank(message = "clientRequestId is required")
        private String clientRequestId;
    }

    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    public Map<String, Object> handleException(Exception e) {
        if (e instanceof RuntimeException re && re.getCause() instanceof com.stripe.exception.StripeException se) {
            e = se;
        }
        
        log.error("WS Error: {}", e.getMessage());
        
        String errorCode = null;
        Map<String, Object> extraPayload = new java.util.HashMap<>();
        
        if (e instanceof com.stripe.exception.StripeException se) {
            errorCode = "PAYMENT_PROVIDER_ERROR";
            extraPayload.put("message", "Payment provider error");
            if (se instanceof com.stripe.exception.CardException) {
                errorCode = "CARD_DECLINED";
                extraPayload.put("message", "Your card was declined.");
            }
        } else if (e instanceof SuperTipException ste) {
            errorCode = ste.getErrorCode().name();
        } else if (e instanceof UserRestrictedException ure) {
            errorCode = "USER_RESTRICTED_" + ure.getLevel();
            if (ure.getExpiresAt() != null) extraPayload.put("expiresAt", ure.getExpiresAt());
        } else if (e instanceof ChatAccessException cae) {
            errorCode = cae.getErrorCode().name();
            if (cae.getRoomId() != null) extraPayload.put("roomId", cae.getRoomId());
            if (cae.getPpvContentId() != null) extraPayload.put("ppvContentId", cae.getPpvContentId());
            if (cae.getRequiredPrice() != null) extraPayload.put("requiredPrice", cae.getRequiredPrice());
        } else if (e instanceof InsufficientBalanceException) {
            errorCode = "INSUFFICIENT_TOKENS";
        } else if (e instanceof BusinessException be) {
            String msg = be.getMessage() != null ? be.getMessage() : "";
            int idx = msg.indexOf(":");
            if (idx > 0) {
                errorCode = msg.substring(0, idx).trim();
            } else {
                errorCode = "BUSINESS_ERROR";
            }
        }

        java.util.Map<String, Object> response = new java.util.HashMap<>();
        response.put("error", e.getClass().getSimpleName());
        response.put("errorCode", errorCode != null ? errorCode : "");
        String message = e.getMessage();
        if (extraPayload.containsKey("message")) {
            message = (String) extraPayload.get("message");
        }
        response.put("message", message);
        response.put("timestamp", Instant.now());
        response.putAll(extraPayload);

        return response;
    }


    @MessageMapping("/stream/{streamId}/tip")
    public void handleChatTip(
            @DestinationVariable UUID streamId,
            @Payload @Valid ChatTipRequest tipRequest,
            Principal principal,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        if (principal == null) {
            throw new AccessDeniedException("User must be authenticated to tip");
        }
        User user = userService.resolveUserFromSubject(principal.getName())
                .orElseThrow(() -> new com.joinlivora.backend.exception.ResourceNotFoundException("User not found"));

        String roomId = "stream-" + streamId;
        if (moderationService.isBanned(user.getId(), roomId)) {
            log.warn("Moderation: Banned creator {} attempted to tip in stream {}", user.getEmail(), streamId);
            throw new AccessDeniedException("You are banned from this stream");
        }

        // Enforcement of Abuse Restrictions
        java.util.Optional<UserRestriction> activeRestriction = restrictionService.getActiveRestriction(new UUID(0L, user.getId()));
        if (activeRestriction.isPresent()) {
            RestrictionLevel level = activeRestriction.get().getRestrictionLevel();
            if (level == RestrictionLevel.TIP_COOLDOWN || level == RestrictionLevel.TEMP_SUSPENSION) {
                log.warn("Abuse: Restricted creator {} (Level: {}) attempted to tip in stream {}", user.getEmail(), level, streamId);
                throw new UserRestrictedException(level, "Your tipping access is restricted.", activeRestriction.get().getExpiresAt());
            }
        }

        // Validate access (PPV etc.)
        chatRoomService.validateAccess("stream-" + streamId, user.getId());

        // Preconditions: stream LIVE + chat ACTIVE (before any token operations inside services)
        StreamRoom tipRoom = null;
        try { tipRoom = streamService.getRoom(streamId); } catch (Exception ignored) {}
        if (tipRoom != null) {
            enforcePaidActionPreconditions(tipRoom.getCreator().getId());
        }

        String ipAddress = (String) headerAccessor.getSessionAttributes().get("ipAddress");

        // Silent check for soft block
        if (ipAddress != null && abuseDetectionService.isSoftBlocked(new java.util.UUID(0L, user.getId()), ipAddress)) {
            log.info("SILENT_DETECTION: User {} or IP {} is soft-blocked in AbuseDetectionService during tip", user.getId(), ipAddress);
        }

        String fingerprint = (String) headerAccessor.getSessionAttributes().get("fingerprint");

        chatTipService.processChatTip(user, streamId, tipRequest.getAmount(), tipRequest.getMessage(), tipRequest.getClientRequestId(), ipAddress, fingerprint);
        
        // Abuse detection: Rapid Tipping
        if (ipAddress != null) {
            abuseDetectionService.checkRapidTipping(new java.util.UUID(0L, user.getId()), ipAddress);
        }
    }

    @MessageMapping("/stream/{streamId}/supertip")
    public void handleSuperTip(
            @DestinationVariable UUID streamId,
            @Payload @Valid SuperTipRequest superTipRequest,
            Principal principal,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        if (principal == null) {
            throw new AccessDeniedException("User must be authenticated to send a SuperTip");
        }
        User user = userService.resolveUserFromSubject(principal.getName())
                .orElseThrow(() -> new com.joinlivora.backend.exception.ResourceNotFoundException("User not found"));

        String roomId = "stream-" + streamId;
        if (moderationService.isBanned(user.getId(), roomId)) {
            log.warn("Moderation: Banned creator {} attempted to send a SuperTip in stream {}", user.getEmail(), streamId);
            throw new AccessDeniedException("You are banned from this stream");
        }

        // Enforcement of Abuse Restrictions
        java.util.Optional<UserRestriction> activeRestriction = restrictionService.getActiveRestriction(new UUID(0L, user.getId()));
        if (activeRestriction.isPresent()) {
            RestrictionLevel level = activeRestriction.get().getRestrictionLevel();
            if (level == RestrictionLevel.TIP_COOLDOWN || level == RestrictionLevel.TEMP_SUSPENSION) {
                log.warn("Abuse: Restricted creator {} (Level: {}) attempted to send a SuperTip in stream {}", user.getEmail(), level, streamId);
                throw new UserRestrictedException(level, "Your tipping access is restricted.", activeRestriction.get().getExpiresAt());
            }
        }

        // Validate access (PPV etc.)
        chatRoomService.validateAccess("stream-" + streamId, user.getId());

        // Preconditions: stream LIVE + chat ACTIVE (before any token operations inside services)
        StreamRoom stRoom = null;
        try { stRoom = streamService.getRoom(streamId); } catch (Exception ignored) {}
        if (stRoom != null) {
            enforcePaidActionPreconditions(stRoom.getCreator().getId());
        }

        String ipAddress = (String) headerAccessor.getSessionAttributes().get("ipAddress");

        // Silent check for soft block
        if (ipAddress != null && abuseDetectionService.isSoftBlocked(new java.util.UUID(0L, user.getId()), ipAddress)) {
            log.info("SILENT_DETECTION: User {} or IP {} is soft-blocked in AbuseDetectionService during SuperTip", user.getId(), ipAddress);
        }

        String fingerprint = (String) headerAccessor.getSessionAttributes().get("fingerprint");

        chatTipService.processSuperTip(user, streamId, superTipRequest.getAmount(), superTipRequest.getMessage(), superTipRequest.getClientRequestId(), ipAddress, fingerprint);
        
        // Abuse detection: Rapid Tipping
        if (ipAddress != null) {
            abuseDetectionService.checkRapidTipping(new java.util.UUID(0L, user.getId()), ipAddress);
        }
    }


    private void enforcePaidActionPreconditions(Long creatorUserId) {
        // 1) Unified availability check (V2 authoritative)
        if (creatorPresenceService != null) {
            com.joinlivora.backend.presence.model.CreatorAvailabilityStatus availability = creatorPresenceService.getAvailability(creatorUserId);
            if (availability != com.joinlivora.backend.presence.model.CreatorAvailabilityStatus.LIVE) {
                throw new BusinessException("STREAM_NOT_LIVE: Stream must be LIVE to perform this action");
            }
        } else {
            // Fallback if service not available
            if (liveStreamServiceV2 != null) {
                StreamCacheDTO active = null;
                try {
                    active = liveStreamServiceV2.getActiveStream(creatorUserId);
                } catch (Exception ignored) {}
                if (active == null) {
                    throw new BusinessException("STREAM_NOT_LIVE: Stream must be LIVE to perform this action");
                }
            }
        }

        // 2) Chat room must be ACTIVE (V2 authoritative when available)
        if (chatRoomRepositoryV2 != null) {
            // Resolve creator (Creator PK) from creatorUserId (User ID)
            Long creatorId = creatorRepository.findByUser_Id(creatorUserId)
                    .map(com.joinlivora.backend.creator.model.Creator::getId)
                    .orElse(null);
            
            if (creatorId == null) {
                throw new BusinessException("CHAT_NOT_ACTIVE: Creator record not found");
            }

            java.util.Optional<com.joinlivora.backend.chat.domain.ChatRoom> opt = chatRoomRepositoryV2.findByCreatorId(creatorId);
            if (opt.isEmpty() || opt.get().getStatus() != com.joinlivora.backend.chat.domain.ChatRoomStatus.ACTIVE) {
                throw new BusinessException("CHAT_NOT_ACTIVE: Chat room is not ACTIVE");
            }
        }
    }
}
