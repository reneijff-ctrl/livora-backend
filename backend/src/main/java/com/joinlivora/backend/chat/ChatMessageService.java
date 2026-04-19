package com.joinlivora.backend.chat;

import com.joinlivora.backend.chat.domain.ChatRoom;
import com.joinlivora.backend.chat.dto.ChatMessageDto;
import com.joinlivora.backend.creator.repository.CreatorRepository;
import com.joinlivora.backend.exception.ResourceNotFoundException;
import com.joinlivora.backend.chat.dto.ModerateResult;
import com.joinlivora.backend.chat.dto.ModerationSeverity;
import com.joinlivora.backend.streaming.service.StreamAssistantBotService;
import com.joinlivora.backend.fraud.model.VelocityActionType;
import com.joinlivora.backend.fraud.service.VelocityTrackerService;
import com.joinlivora.backend.streaming.service.StreamModerationService;
import com.joinlivora.backend.streaming.service.StreamModeratorService;
import com.joinlivora.backend.websocket.RealtimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChatMessageService {

    private final VelocityTrackerService velocityTrackerService;
    private final SimpMessagingTemplate messagingTemplate;
    private final com.joinlivora.backend.user.UserRepository userRepository;
    private final com.joinlivora.backend.abuse.AbuseDetectionService abuseDetectionService;
    private final com.joinlivora.backend.analytics.AnalyticsEventPublisher analyticsEventPublisher;
    private final ChatModerationService moderationService;
    private final ChatRateLimiterService chatRateLimiterService;
    private final com.joinlivora.backend.chat.repository.ChatMessageRepository chatMessageRepository;
    private final ChatViolationLogRepository violationLogRepository;
    private final CreatorRepository creatorRepository;
    private final StreamModerationService streamModerationService;
    private final StreamModeratorService streamModeratorService;
    private final StreamAssistantBotService streamAssistantBotService;
    private final com.joinlivora.backend.token.TokenService tokenService;
    private final com.joinlivora.backend.payout.CreatorEarningsService creatorEarningsService;
    private final com.joinlivora.backend.abuse.RestrictionService restrictionService;
    private final com.joinlivora.backend.chat.SlowModeBypassService slowModeBypassService;
    private final com.joinlivora.backend.chat.ChatRateLimitService chatRateLimitService;
    private final com.joinlivora.backend.monetization.HighlightedMessageService highlightedMessageService;
    private final com.joinlivora.backend.streaming.StreamService streamService;
    private final com.joinlivora.backend.streaming.StreamRepository streamRepository;
    private final com.joinlivora.backend.streaming.chat.commands.ChatCommandRegistry commandRegistry;
    private final com.joinlivora.backend.websocket.PresenceService presenceService;
    private final com.joinlivora.backend.chat.service.ChatRoomService chatRoomService;
    private final ChatPersistenceService chatPersistenceService;
    private final RedisChatBatchService chatBatchService;
    private final org.springframework.data.redis.core.RedisTemplate<String, Object> redisTemplate;
    private final com.joinlivora.backend.moderation.CreatorRoomBanService creatorRoomBanService;

    private static final int MAX_MESSAGE_LENGTH = 500;

    /**
     * Processes an incoming chat message request from the controller.
     * Enforces all validation and moderation rules before broadcasting.
     */
    public void processIncomingMessage(com.joinlivora.backend.chat.dto.ChatMessageRequest request, com.joinlivora.backend.user.User sender) {
        if (request == null || sender == null) {
            throw new IllegalArgumentException("Request and sender cannot be null");
        }

        String content = request.getContent();

        // 0. Sanitation
        content = sanitize(content);
        if (content.isEmpty()) {
            return; // Don't process empty messages
        }

        // 1. Intercept Slash Commands
        if (content != null && content.startsWith("/")) {
            log.info("CHAT: Intercepting slash command '{}' for creatorUserId: {}", content, request.getCreatorUserId());
            com.joinlivora.backend.streaming.chat.commands.ChatCommandContext context = com.joinlivora.backend.streaming.chat.commands.ChatCommandContext.builder()
                    .creatorId(request.getCreatorUserId())
                    .senderPrincipalName(sender.getEmail()) // Use email as principal name
                    .senderUsername(sender.getUsername())
                    .fullMessage(content)
                    .messagingTemplate(messagingTemplate)
                    .build();
            
            commandRegistry.handleCommand(context);
            return; // Don't broadcast command itself to chat
        }

        // Determine senderRole: override to MODERATOR if user is a per-stream mod
        String resolvedRole = sender.getRole().name();
        if (request.getCreatorUserId() != null && streamModeratorService.isModerator(request.getCreatorUserId(), sender.getId())) {
            resolvedRole = "MODERATOR";
        }

        // Resolve creatorUserId from backend stream context — never trust frontend input for ownership
        Long resolvedCreatorUserId = request.getCreatorUserId();
        if (request.getStreamId() != null) {
            try {
                java.util.UUID streamUuid = java.util.UUID.fromString(request.getStreamId());
                com.joinlivora.backend.streaming.StreamRoom room = streamService.getRoom(streamUuid);
                if (room != null && room.getCreator() != null) {
                    resolvedCreatorUserId = room.getCreator().getId();
                }
            } catch (Exception e) {
                log.warn("CHAT: Could not resolve creatorUserId from streamId '{}': {}", request.getStreamId(), e.getMessage());
            }
        }

        // Compute senderType — OWNER must be checked before CREATOR
        boolean isOwner = resolvedCreatorUserId != null && resolvedCreatorUserId.equals(sender.getId());
        boolean isAdminUser = sender.getRole() == com.joinlivora.backend.user.Role.ADMIN;
        String senderType;
        if (isOwner) {
            senderType = "OWNER";
        } else if (isAdminUser) {
            senderType = "ADMIN";
        } else if (sender.getRole() == com.joinlivora.backend.user.Role.CREATOR) {
            senderType = "CREATOR";
        } else {
            senderType = "USER";
        }

        ChatMessageDto dto = ChatMessageDto.builder()
                .messageId(java.util.UUID.randomUUID().toString())
                .content(content)
                .message(content) // Backward compatibility
                .creatorUserId(resolvedCreatorUserId)
                .isStreamOwner(isOwner)
                .senderType(senderType)
                .type(request.getType() != null ? request.getType() : "CHAT")
                .senderId(sender.getId())
                .senderUsername(sender.getUsername())
                .senderRole(resolvedRole)
                .sender(sender.getUsername()) // Backward compatibility
                .build();

        processMessage(sender.getId(), dto);

        // 2. Trigger Bot Reminders (every 5 messages or random) — skip for OWNER and ADMIN
        if (resolvedCreatorUserId != null
                && !"OWNER".equals(senderType)
                && !"ADMIN".equals(senderType)) {
            streamAssistantBotService.onMessageReceived(resolvedCreatorUserId);
        }
    }

    private String sanitize(String content) {
        if (content == null) return "";
        String trimmed = content.trim();
        if (trimmed.length() > MAX_MESSAGE_LENGTH) {
            return trimmed.substring(0, MAX_MESSAGE_LENGTH).trim();
        }
        return trimmed;
    }

    /**
     * Processes a chat room join request and broadcasts a system message if allowed.
     */
    public void processJoinRoom(com.joinlivora.backend.chat.dto.ChatMessageDto joinRequest, com.joinlivora.backend.user.User user, String sessionId) {
        if (joinRequest == null || user == null) {
            throw new IllegalArgumentException("Join request and user cannot be null");
        }

        UUID roomId = joinRequest.getRoomId();
        ChatRoom room = chatRoomService.getRoomEntity(roomId);
        
        // Resolve creator User ID
        Long creatorUserId = creatorRepository.findById(room.getCreatorId())
                .map(creator -> creator.getUser().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Creator not found for room: " + roomId));
        
        joinRequest.setCreatorUserId(creatorUserId);

        // Fetch room identifier for moderation check
        String roomIdentifier = room.getName() != null ? room.getName() : "room-v2-" + room.getId();

        if (moderationService.isBanned(user.getId(), roomIdentifier)) {
            log.warn("Moderation: Banned user {} (id: {}) attempted to join creator's room {}", user.getEmail(), user.getId(), creatorUserId);
            throw new RuntimeException("User is banned from chat");
        }

        // Verify topic subscription
        String destination = "/exchange/amq.topic/chat." + creatorUserId;
        if (!presenceService.isSubscribedTo(sessionId, destination)) {
            log.warn("Presence: User {} attempted to join room {} without topic subscription", user.getEmail(), creatorUserId);
            throw new RuntimeException("Subscription to room topic required before joining");
        }

        chatRoomService.validateAccess(roomIdentifier, user.getId());

        ChatMessageDto systemMessage = ChatMessageDto.builder()
                .id(java.util.UUID.randomUUID().toString())
                .creatorUserId(creatorUserId)
                .sender("system")
                .content(user.getUsername() + " joined the room")
                .timestamp(Instant.now())
                .systemMessage(true)
                .build();

        log.info("Chat: User {} joined creator's room {}", user.getUsername(), creatorUserId);
        messagingTemplate.convertAndSend("/exchange/amq.topic/chat." + creatorUserId, systemMessage);
    }

    /**
     * Processes a chat message: tracks velocity and broadcasts to the room.
     */
    public void processMessage(Long userId, ChatMessageDto chatMessage) {
        processMessage(userId, chatMessage, null);
    }

    /**
     * Processes a chat message with IP address for abuse detection.
     */
    public void processMessage(Long userId, ChatMessageDto chatMessage, String ipAddress) {
        log.debug("Processing chat message from user {} to room {}", userId, chatMessage.getRoomId());

        com.joinlivora.backend.user.User senderUser = userRepository.findById(userId).orElse(null);
        if (senderUser != null) {
            if (senderUser.getStatus() == com.joinlivora.backend.user.UserStatus.SUSPENDED || 
                senderUser.getStatus() == com.joinlivora.backend.user.UserStatus.TERMINATED) {
                log.warn("SECURITY: Chat message blocked for RESTRICTED user: {}", senderUser.getEmail());
                throw new org.springframework.security.access.AccessDeniedException("Account is restricted.");
            }
        }

        // 0. Sanitation
        String sanitized = sanitize(chatMessage.getContent() != null ? chatMessage.getContent() : chatMessage.getMessage());
        chatMessage.setContent(sanitized);
        chatMessage.setMessage(sanitized);

        if (sanitized.isEmpty()) {
            return;
        }

        if (ipAddress != null) {
            abuseDetectionService.checkMessageSpam(new java.util.UUID(0L, userId), ipAddress);
        }

        // 0. Resolve Room and Creator
        Long creatorUserIdFromMsg = chatMessage.getCreatorUserId();
        ChatRoom room;
        Long creatorUserId;
        if (creatorUserIdFromMsg != null) {
            room = chatRoomService.getOrCreateRoom(creatorUserIdFromMsg);
            creatorUserId = creatorUserIdFromMsg;
        } else {
            UUID fallbackRoomId = chatMessage.getRoomId();
            room = chatRoomService.getRoomEntity(fallbackRoomId);
            creatorUserId = creatorRepository.findById(room.getCreatorId())
                    .map(creator -> creator.getUser().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Creator not found for room: " + fallbackRoomId));
        }
        UUID roomId = new UUID(0L, room.getId());
        
        chatMessage.setCreatorUserId(creatorUserId);

        // Override senderRole to MODERATOR if user is a per-stream moderator for this creator
        if (senderUser != null && streamModeratorService.isModerator(creatorUserId, senderUser.getId())) {
            chatMessage.setSenderRole("MODERATOR");
        }

        // Compute and set senderType + isStreamOwner if not already set by caller
        if (chatMessage.getSenderType() == null && senderUser != null) {
            boolean isOwner = senderUser.getId().equals(creatorUserId);
            boolean isAdminUser = senderUser.getRole() == com.joinlivora.backend.user.Role.ADMIN;
            String senderType;
            if (isOwner) {
                senderType = "OWNER";
            } else if (isAdminUser) {
                senderType = "ADMIN";
            } else if (senderUser.getRole() == com.joinlivora.backend.user.Role.CREATOR) {
                senderType = "CREATOR";
            } else {
                senderType = "USER";
            }
            chatMessage.setSenderType(senderType);
            chatMessage.setStreamOwner(isOwner);
        }

        log.info("CHAT_TOPIC_DEBUG: broadcast creatorUserId={}, topic=/exchange/amq.topic/chat.{}",
                creatorUserId, creatorUserId);

        // 0.2 Room Status Check
        if (room.getStatus() != com.joinlivora.backend.chat.domain.ChatRoomStatus.ACTIVE) {
            log.info("Moderation: Message blocked for inactive room {} (Status: {})", room.getId(), room.getStatus());
            if (senderUser != null) {
                RealtimeMessage errorMsg = RealtimeMessage.builder()
                        .type("MODERATION_BLOCKED")
                        .payload(Map.of(
                            "reason", "Messages are allowed only when the chat room is ACTIVE.",
                            "originalMessage", chatMessage.getMessage()
                        ))
                        .timestamp(Instant.now())
                        .build();
                messagingTemplate.convertAndSendToUser(senderUser.getId().toString(), "/queue/moderation", errorMsg);
            }
            throw new com.joinlivora.backend.exception.BusinessException("CHAT_ROOM_NOT_ACTIVE: Messages are allowed only when the chat room is ACTIVE.");
        }

        // 0.3 Ban Check
        String roomIdentifier = room.getName() != null ? room.getName() : "room-v2-" + room.getId();
        if (moderationService.isBanned(userId, roomIdentifier)) {
            log.info("Moderation: User {} is banned from room {}. Blocking message.", userId, roomIdentifier);
            if (senderUser != null) {
                RealtimeMessage errorMsg = RealtimeMessage.builder()
                        .type("MODERATION_BLOCKED")
                        .payload(Map.of(
                            "reason", "You are banned from this chat room.",
                            "severity", "HIGH",
                            "originalMessage", chatMessage.getMessage()
                        ))
                        .timestamp(Instant.now())
                        .build();
                messagingTemplate.convertAndSendToUser(senderUser.getId().toString(), "/queue/moderation", errorMsg);
            }
            return;
        }

        // 0.3b Creator Room Ban Check
        if (creatorRoomBanService.isUserBanned(creatorUserId, userId)) {
            log.info("RoomBan: User {} is room-banned from creator {} room. Blocking message.", userId, creatorUserId);
            if (senderUser != null) {
                RealtimeMessage errorMsg = RealtimeMessage.builder()
                        .type("MODERATION_BLOCKED")
                        .payload(Map.of(
                            "reason", "You are banned from this room.",
                            "severity", "HIGH",
                            "originalMessage", chatMessage.getMessage()
                        ))
                        .timestamp(Instant.now())
                        .build();
                messagingTemplate.convertAndSendToUser(senderUser.getId().toString(), "/queue/moderation", errorMsg);
            }
            return;
        }

        // 0.4 Automatic Moderation
        ModerateResult moderation = moderationService.moderate(chatMessage.getMessage(), userId, creatorUserId);
        if (!moderation.isAllowed()) {
            log.info("Moderation: Blocked message from user {}: {} (Reason: {}, Severity: {})",
                    userId, chatMessage.getMessage(), moderation.getReason(), moderation.getSeverity());
            
            // Log violation
            try {
                ChatViolationLog violationLog = ChatViolationLog.builder()
                        .userId(userId)
                        .message(chatMessage.getMessage())
                        .severity(moderation.getSeverity())
                        .timestamp(Instant.now())
                        .creatorId(creatorUserId)
                        .build();
                violationLogRepository.save(violationLog);
            } catch (Exception e) {
                log.error("Failed to log chat violation", e);
            }

            if (senderUser != null) {
                RealtimeMessage errorMsg = RealtimeMessage.builder()
                        .type("MODERATION_BLOCKED")
                        .payload(Map.of(
                            "reason", moderation.getReason(), 
                            "severity", moderation.getSeverity().name(),
                            "originalMessage", chatMessage.getMessage()
                        ))
                        .timestamp(Instant.now())
                        .build();
                messagingTemplate.convertAndSendToUser(senderUser.getId().toString(), "/queue/moderation", errorMsg);
            }
            return;
        }

        // 0.5 Highlight / Bot Reminders
        if (moderation.isPositive()) {
            chatMessage.setHighlight("POSITIVE");
            String senderDisplayName = senderUser != null ? (senderUser.getDisplayName() != null ? senderUser.getDisplayName() : senderUser.getEmail().split("@")[0]) : "User";
            streamAssistantBotService.onPositiveMessage(creatorUserId, senderDisplayName, chatMessage.getMessage());
        }

        // 0.6 Rate Limiting
        if (!chatRateLimiterService.isAllowed(userId)) {
            if (senderUser != null) {
                RealtimeMessage errorMsg = RealtimeMessage.builder()
                        .type("RATE_LIMIT_EXCEEDED")
                        .payload(Map.of(
                            "reason", "You are sending messages too fast. Please wait a moment.",
                            "originalMessage", chatMessage.getMessage()
                        ))
                        .timestamp(Instant.now())
                        .build();
                messagingTemplate.convertAndSendToUser(senderUser.getId().toString(), "/queue/moderation", errorMsg);
            }
            return;
        }

        // 0.7 Mute Check
        if (streamModerationService.isMuted(creatorUserId, userId)) {
            log.info("StreamModeration: User {} is muted in stream of creator {}. Blocking message.", userId, creatorUserId);
            if (senderUser != null) {
                RealtimeMessage errorMsg = RealtimeMessage.builder()
                        .type("MODERATION_BLOCKED")
                        .payload(Map.of(
                            "reason", "You are currently muted in this chat.",
                            "severity", "MEDIUM",
                            "originalMessage", chatMessage.getMessage()
                        ))
                        .timestamp(Instant.now())
                        .build();
                messagingTemplate.convertAndSendToUser(senderUser.getId().toString(), "/queue/moderation", errorMsg);
            }
            return;
        }

        // 0.8 Abuse Restrictions
        java.util.Optional<com.joinlivora.backend.abuse.model.UserRestriction> activeRestriction = restrictionService.getActiveRestriction(new java.util.UUID(0L, userId));
        if (activeRestriction.isPresent()) {
            com.joinlivora.backend.abuse.model.RestrictionLevel level = activeRestriction.get().getRestrictionLevel();
            if (level == com.joinlivora.backend.abuse.model.RestrictionLevel.CHAT_MUTE || level == com.joinlivora.backend.abuse.model.RestrictionLevel.TEMP_SUSPENSION) {
                log.warn("Abuse: Restricted user {} (Level: {}) attempted to send message to creator {}", userId, level, creatorUserId);
                throw new com.joinlivora.backend.exception.UserRestrictedException(level, "Your chat access is restricted.", activeRestriction.get().getExpiresAt());
            }
        }

        // 0.9 Paid Chat Enforcement
        if (senderUser != null) {
            if (room.isPaid()) {
                com.joinlivora.backend.creator.model.Creator creatorEntity = creatorRepository.findById(room.getCreatorId()).orElse(null);
                boolean isStaff = (creatorEntity != null && creatorEntity.getUser().getId().equals(userId)) ||
                        senderUser.getRole() == com.joinlivora.backend.user.Role.ADMIN ||
                        senderUser.getRole() == com.joinlivora.backend.user.Role.MODERATOR;

                if (!isStaff && creatorEntity != null) {
                    long priceToDeduct = 0;
                    if (chatMessage.isPaid() && chatMessage.getAmount() != null && chatMessage.getAmount() > 0) {
                        priceToDeduct = chatMessage.getAmount();
                    } else if (room.getPricePerMessage() != null && room.getPricePerMessage() > 0) {
                        priceToDeduct = room.getPricePerMessage();
                    }

                    if (priceToDeduct > 0) {
                        tokenService.deductTokens(senderUser, priceToDeduct, com.joinlivora.backend.wallet.WalletTransactionType.CHAT, "Paid message in room " + room.getId());
                        creatorEarningsService.recordChatEarning(senderUser, creatorEntity.getUser(), priceToDeduct, new UUID(0, room.getId()));
                        log.info("CHAT-V2: Deducted {} tokens for paid message from {} in room {}", priceToDeduct, senderUser.getEmail(), room.getId());
                        chatMessage.setPaid(true);
                        chatMessage.setAmount((int) priceToDeduct);
                    }
                }
            }
        }

        // 0.10 Highlight Intent
        if (chatMessage.isHighlighted() && senderUser != null) {
            try {
                String messageId = chatMessage.getClientRequestId() != null ? chatMessage.getClientRequestId() : java.util.UUID.randomUUID().toString();
                String clientSecret = highlightedMessageService.createHighlightIntent(
                        senderUser, roomId, messageId, chatMessage.getMessage(),
                        chatMessage.getHighlightType(),
                        chatMessage.getAmount() != null ? java.math.BigDecimal.valueOf(chatMessage.getAmount()) : java.math.BigDecimal.ZERO,
                        chatMessage.getClientRequestId(),
                        ipAddress, null, null
                );

                messagingTemplate.convertAndSendToUser(senderUser.getId().toString(), "/queue/highlight-intent", java.util.Map.of(
                        "clientSecret", clientSecret,
                        "clientRequestId", chatMessage.getClientRequestId() != null ? chatMessage.getClientRequestId() : ""
                ));
                return;
            } catch (Exception e) {
                log.error("Failed to create highlight intent", e);
                throw (e instanceof RuntimeException) ? (RuntimeException) e : new RuntimeException(e);
            }
        }

        // 0.11 Slow Mode Validation
        boolean isStaff = senderUser != null && (senderUser.getRole() == com.joinlivora.backend.user.Role.ADMIN || senderUser.getRole() == com.joinlivora.backend.user.Role.MODERATOR || streamModeratorService.isModerator(creatorUserId, senderUser.getId()));
        boolean hasSlowModeBypass = isStaff || slowModeBypassService.isBypassing(userId, roomId);
        boolean hasAbuseSlowMode = activeRestriction.isPresent() && activeRestriction.get().getRestrictionLevel() == com.joinlivora.backend.abuse.model.RestrictionLevel.SLOW_MODE;

        if (hasAbuseSlowMode || !hasSlowModeBypass) {
             try {
                 chatRateLimitService.validateMessageRate(userId, roomId);
             } catch (Exception e) {
                 if (senderUser != null) {
                     RealtimeMessage errorMsg = RealtimeMessage.builder()
                             .type("RATE_LIMIT_EXCEEDED")
                             .payload(Map.of("reason", e.getMessage(), "originalMessage", chatMessage.getMessage()))
                             .timestamp(Instant.now())
                             .build();
                     messagingTemplate.convertAndSendToUser(senderUser.getId().toString(), "/queue/moderation", errorMsg);
                 }
                 return;
             }
        }

        // Finalize and broadcast
        velocityTrackerService.trackAction(userId, VelocityActionType.MESSAGE);

        if (senderUser != null) {
            analyticsEventPublisher.publishEvent(
                    com.joinlivora.backend.analytics.AnalyticsEventType.CHAT_MESSAGE_SENT,
                    senderUser,
                    java.util.Map.of("roomId", roomId)
            );
        }

        // Prepare message metadata before broadcast
        Instant now = Instant.now();
        chatMessage.setId(UUID.randomUUID().toString());
        chatMessage.setTimestamp(now);
        chatMessage.setSystemMessage(false);

        // Ensure username is set for Redis history deserialization
        // (senderUsername has READ_ONLY access and is lost during Redis deserialization)
        if (senderUser != null && chatMessage.getUsername() == null) {
            chatMessage.setUsername(senderUser.getUsername());
        }

        log.info("Chat-V2: Broadcasting message to creator {}: {}", creatorUserId, chatMessage.getMessage());

        // Broadcast immediately (before persistence)
        boolean shadowMuted = moderationService.isShadowMuted(userId, roomId.toString()) || streamModerationService.isShadowMuted(creatorUserId, userId);

        if (shadowMuted) {
            if (senderUser != null) messagingTemplate.convertAndSendToUser(senderUser.getId().toString(), "/queue/chat", chatMessage);
        } else {
            chatBatchService.enqueueMessage(creatorUserId, chatMessage);

            // Buffer last 5 messages in Redis for chat history replay
            try {
                String historyKey = "chat:history:" + creatorUserId;
                redisTemplate.opsForList().leftPush(historyKey, chatMessage);
                redisTemplate.opsForList().trim(historyKey, 0, 4);
            } catch (Exception e) {
                log.warn("Failed to buffer chat history for creator {}: {}", creatorUserId, e.getMessage());
            }
        }

        // Persist asynchronously — does not block the WebSocket inbound thread
        com.joinlivora.backend.chat.domain.ChatMessage entity = com.joinlivora.backend.chat.domain.ChatMessage.builder()
                .roomId(room.getId())
                .senderId(userId)
                .senderRole(senderUser != null ? (streamModeratorService.isModerator(creatorUserId, senderUser.getId()) ? "MODERATOR" : senderUser.getRole().name()) : "USER")
                .content(chatMessage.getContent())
                .createdAt(now)
                .build();
        chatPersistenceService.persistChatMessage(entity);
    }
}
