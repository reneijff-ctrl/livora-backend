package com.joinlivora.backend.chat.service;

import com.joinlivora.backend.analytics.AnalyticsEventPublisher;
import com.joinlivora.backend.analytics.AnalyticsEventType;
import com.joinlivora.backend.chat.ChatMode;
import com.joinlivora.backend.chat.dto.ChatErrorCode;
import com.joinlivora.backend.chat.dto.ChatRoomDto;
import com.joinlivora.backend.chat.dto.ChatPpvAccessResponse;
import com.joinlivora.backend.chat.domain.ChatRoom;
import com.joinlivora.backend.chat.domain.ChatRoomStatus;
import com.joinlivora.backend.chat.repository.ChatRoomRepository;
import com.joinlivora.backend.creator.model.Creator;
import com.joinlivora.backend.presence.service.CreatorPresenceService;
import com.joinlivora.backend.creator.repository.CreatorRepository;
import com.joinlivora.backend.exception.BusinessException;
import com.joinlivora.backend.exception.ChatAccessException;
import com.joinlivora.backend.exception.ResourceNotFoundException;
import com.joinlivora.backend.exception.chat.ChatRoomAlreadyExistsException;
import com.joinlivora.backend.exception.chat.PpvRoomAlreadyExistsException;
import com.joinlivora.backend.livestream.service.LiveStreamService;
import com.joinlivora.backend.monetization.PPVPurchaseValidationService;
import com.joinlivora.backend.monetization.PpvContent;
import com.joinlivora.backend.monetization.PpvService;
import com.joinlivora.backend.payment.SubscriptionStatus;
import com.joinlivora.backend.payment.UserSubscriptionRepository;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import com.joinlivora.backend.user.UserStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Primary
@Slf4j
public class ChatRoomService {

    // V2 Repositories
    private final ChatRoomRepository chatRoomRepository;
    private final CreatorPresenceService creatorPresenceService;
    private final CreatorRepository creatorRepository;
    private final LiveStreamService liveStreamService;

    // Dependencies from V1
    private final UserRepository userRepository;
    private final UserSubscriptionRepository subscriptionRepository;
    private final PpvService ppvService;
    private final PPVPurchaseValidationService purchaseValidationService;
    private final com.joinlivora.backend.chat.PPVChatAccessService ppvChatAccessService;
    private final AnalyticsEventPublisher analyticsEventPublisher;
    private final com.joinlivora.backend.privateshow.PrivateSessionRepository privateSessionRepository;

    public ChatRoomService(
            ChatRoomRepository chatRoomRepository,
            CreatorPresenceService creatorPresenceService,
            CreatorRepository creatorRepository,
            @org.springframework.context.annotation.Lazy LiveStreamService liveStreamService,
            UserRepository userRepository,
            UserSubscriptionRepository subscriptionRepository,
            @org.springframework.context.annotation.Lazy PpvService ppvService,
            PPVPurchaseValidationService purchaseValidationService,
            com.joinlivora.backend.chat.PPVChatAccessService ppvChatAccessService,
            AnalyticsEventPublisher analyticsEventPublisher,
            com.joinlivora.backend.privateshow.PrivateSessionRepository privateSessionRepository) {
        this.chatRoomRepository = chatRoomRepository;
        this.creatorPresenceService = creatorPresenceService;
        this.creatorRepository = creatorRepository;
        this.liveStreamService = liveStreamService;
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.ppvService = ppvService;
        this.purchaseValidationService = purchaseValidationService;
        this.ppvChatAccessService = ppvChatAccessService;
        this.analyticsEventPublisher = analyticsEventPublisher;
        this.privateSessionRepository = privateSessionRepository;
    }

    // --- V2 Methods ---

    @Transactional
    public ChatRoom getOrCreateRoom(Long creatorUserId) {
        log.info("CHAT-V2: Get or create chat room for creatorUserId: {}", creatorUserId);

        // 1) Resolve Creator by userId explicitly (Harden entry point)
        Creator creator = creatorRepository.findByUser_Id(creatorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Creator profile not found for user ID: " + creatorUserId));

        // 2) Validate creator status (Banned accounts only)
        UserStatus userStatus = creator.getUser().getStatus();
        if (userStatus == UserStatus.SUSPENDED || userStatus == UserStatus.TERMINATED) {
            throw new BusinessException("Creator account is banned");
        }

        // 3) Unified availability status
        com.joinlivora.backend.presence.model.CreatorAvailabilityStatus availability = creatorPresenceService.getAvailability(creatorUserId);
        boolean isAvailable = availability != com.joinlivora.backend.presence.model.CreatorAvailabilityStatus.OFFLINE;
        log.debug("Creator userId {} availability: {}", creatorUserId, availability);

        // 4) Get or create chat room (persisted with creator/Creator PK)
        Long creatorId = creator.getId();
        ChatRoom room = chatRoomRepository.findMainRoom(creatorId, "private-session-")
                .orElseGet(() -> createRoomV2(creatorId, isAvailable ? ChatRoomStatus.ACTIVE : ChatRoomStatus.WAITING_FOR_CREATOR));
        
        // Ensure room status matches current availability
        boolean streamLive = liveStreamService.isStreamActive(creatorUserId);
        if (!isAvailable && !streamLive && (room.getStatus() == ChatRoomStatus.ACTIVE)) {
            log.info("CHAT-V2: Room {} status out of sync with availability (Offline). Pausing.", room.getId());
            room.setStatus(ChatRoomStatus.PAUSED);
            room = chatRoomRepository.save(room);
        } else if ((isAvailable || streamLive) && (room.getStatus() == ChatRoomStatus.WAITING_FOR_CREATOR || room.getStatus() == ChatRoomStatus.PAUSED)) {
            log.info("CHAT-V2: Room {} status out of sync with availability ({}). Activating.", room.getId(), availability);
            room.setStatus(ChatRoomStatus.ACTIVE);
            room.setActivatedAt(Instant.now());
            room = chatRoomRepository.save(room);
        }

        // Sync LIVE status
        boolean shouldBeLive = availability == com.joinlivora.backend.presence.model.CreatorAvailabilityStatus.LIVE;
        if (room.isLive() != shouldBeLive) {
            room.setLive(shouldBeLive);
            room = chatRoomRepository.save(room);
        }
        
        return room;
    }

    @Transactional
    public ChatRoom setRoomLiveStatus(Long roomId, boolean isLive) {
        log.info("CHAT-V2: Setting room ID {} live status to: {}", roomId, isLive);
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat room not found with ID: " + roomId));
        
        room.setLive(isLive);
        return chatRoomRepository.save(room);
    }

    @Transactional
    public void setCreatorRoomLiveStatus(Long creatorUserId, boolean isLive) {
        log.info("CHAT-V2: Setting creator user ID {} room live status to: {}", creatorUserId, isLive);
        creatorRepository.findByUser_Id(creatorUserId).ifPresent(creator -> {
            chatRoomRepository.findMainRoom(creator.getId(), "private-session-").ifPresent(room -> {
                room.setLive(isLive);
                chatRoomRepository.save(room);
                log.info("CHAT-V2: Room {} live status set to {} for creatorUserId {}", room.getId(), isLive, creatorUserId);
            });
        });
    }

    @Transactional
    public List<ChatRoom> activateWaitingRooms(Long creatorId) {
        log.info("CHAT-V2: Activating rooms for creator ID: {}", creatorId);
        List<ChatRoomStatus> statuses = java.util.List.of(ChatRoomStatus.WAITING_FOR_CREATOR, ChatRoomStatus.PAUSED);
        List<ChatRoom> roomsToActivate = chatRoomRepository.findAllByCreatorIdAndStatusIn(creatorId, statuses);
        roomsToActivate.forEach(room -> {
            log.info("CHAT-V2: Activating chat room {} for creator ID: {}", room.getId(), creatorId);
            room.setStatus(ChatRoomStatus.ACTIVE);
            room.setActivatedAt(Instant.now());
        });
        return chatRoomRepository.saveAll(roomsToActivate);
    }

    @Transactional
    public List<ChatRoom> pauseActiveRooms(Long creatorId) {
        log.info("CHAT-V2: Pausing active rooms for creator ID: {}", creatorId);
        List<ChatRoom> activeRooms = chatRoomRepository.findAllByCreatorIdAndStatus(creatorId, ChatRoomStatus.ACTIVE);
        activeRooms.forEach(room -> {
            log.info("CHAT-V2: Pausing chat room {} for creator ID: {}", room.getId(), creatorId);
            room.setStatus(ChatRoomStatus.PAUSED);
        });
        return chatRoomRepository.saveAll(activeRooms);
    }

    private ChatRoom createRoomV2(Long creatorId, ChatRoomStatus status) {
        log.info("CHAT-V2: Creating new chat room for creator ID: {} with status {}", creatorId, status);
        ChatRoom room = ChatRoom.builder()
                .creatorId(creatorId)
                .isLive(false)
                .status(status)
                .build();
        return chatRoomRepository.save(room);
    }

    // --- Consolidated V1 Methods (Targeting V2 Schema) ---

    @Transactional
    public ChatRoom createPublicRoom(String name, Long creatorUserId) {
        log.info("CHAT-V2: Creating public room '{}' for creator user ID {}", name, creatorUserId);
        validateRoomNameUnique(name);

        Creator creator = creatorRepository.findByUser_Id(creatorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Creator not found for user ID: " + creatorUserId));

        ChatRoom chatRoom = ChatRoom.builder()
                .name(name)
                .creatorId(creator.getId())
                .isPrivate(false)
                .status(ChatRoomStatus.ACTIVE)
                .build();

        return chatRoomRepository.save(chatRoom);
    }

    @Transactional
    public ChatRoom createPrivateRoom(String name, Long creatorUserId) {
        return createPrivateRoom(name, creatorUserId, null);
    }

    @Transactional
    public ChatRoom createPrivateRoom(String name, Long creatorUserId, UUID ppvContentId) {
        log.info("CHAT-V2: Creating private room '{}' for creator user ID {} (PPV: {})", name, creatorUserId, ppvContentId);
        validateRoomNameUnique(name);

        Creator creator = creatorRepository.findByUser_Id(creatorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Creator not found for user ID: " + creatorUserId));

        PpvContent ppvContent = null;
        if (ppvContentId != null) {
            if (chatRoomRepository.findByPpvContentId(ppvContentId).isPresent()) {
                throw new PpvRoomAlreadyExistsException("A chat room already exists for this PPV content: " + ppvContentId);
            }
            ppvContent = ppvService.getPpvContent(ppvContentId);
        }

        ChatRoom chatRoom = ChatRoom.builder()
                .name(name)
                .creatorId(creator.getId())
                .isPrivate(true)
                .ppvContent(ppvContent)
                .status(ChatRoomStatus.ACTIVE)
                .build();

        return chatRoomRepository.save(chatRoom);
    }

    @Transactional
    public ChatRoom createLiveStreamRoom(UUID streamRoomId, Long creatorUserId, boolean isPremium, UUID ppvContentId, boolean isPaid, Long pricePerMessage) {
        String name = "stream-" + streamRoomId;
        
        PpvContent ppvContent = null;
        if (ppvContentId != null) {
            ppvContent = ppvService.getPpvContent(ppvContentId);
        }

        PpvContent finalPpvContent = ppvContent;
        return chatRoomRepository.findByName(name)
                .map(room -> {
                    log.info("CHAT-V2: Updating existing stream room '{}' (Premium: {}, PPV: {}, Paid: {}, Price: {})", 
                            name, isPremium, ppvContentId, isPaid, pricePerMessage);
                    room.setPrivate(isPremium || finalPpvContent != null);
                    room.setPpvContent(finalPpvContent);
                    room.setPaid(isPaid);
                    room.setPricePerMessage(pricePerMessage);
                    return chatRoomRepository.save(room);
                })
                .orElseGet(() -> {
                    log.info("CHAT-V2: Creating new stream room '{}' (Premium: {}, PPV: {}, Paid: {}, Price: {})", 
                            name, isPremium, ppvContentId, isPaid, pricePerMessage);
                    
                    Creator creator = creatorRepository.findByUser_Id(creatorUserId)
                            .orElseThrow(() -> new ResourceNotFoundException("Creator not found for user ID: " + creatorUserId));

                    ChatRoom chatRoom = ChatRoom.builder()
                            .name(name)
                            .creatorId(creator.getId())
                            .isPrivate(isPremium || finalPpvContent != null)
                            .ppvContent(finalPpvContent)
                            .isPaid(isPaid)
                            .pricePerMessage(pricePerMessage)
                            .status(ChatRoomStatus.ACTIVE)
                            .build();

                    return chatRoomRepository.save(chatRoom);
                });
    }

    private void validateRoomNameUnique(String name) {
        if (chatRoomRepository.findByName(name).isPresent()) {
            throw new ChatRoomAlreadyExistsException("Room name already exists: " + name);
        }
    }

    public boolean validateAccess(String name, Long userId) {
        log.debug("CHAT-V2: validateAccess called for room '{}' and userId {}", name, userId);
        
        ChatRoom room = chatRoomRepository.findByName(name)
                .orElseGet(() -> {
                    // Fallback to creator room lookup if name looks like a creator ID or is missing
                    try {
                        Long creatorId = Long.parseLong(name);
                        return chatRoomRepository.findMainRoom(creatorId, "private-session-")
                                .orElseThrow(() -> new AccessDeniedException("Room not found: " + name));
                    } catch (NumberFormatException e) {
                        throw new AccessDeniedException("Room not found: " + name);
                    }
                });

        if (!room.isPrivate()) {
            return true;
        }

        if (userId == null) {
            throw new AccessDeniedException("Authentication required for private room");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        if (user.getRole() == Role.ADMIN || user.getRole() == Role.MODERATOR) {
            return true;
        }

        // Check if user is the creator (creatorId in ChatRoom refers to Creator.id, not User.id)
        Creator creator = creatorRepository.findById(room.getCreatorId()).orElse(null);
        if (creator != null && creator.getUser().getId().equals(userId)) {
            return true;
        }

        // Private session access check — only the assigned viewer and creator may access
        if (name != null && name.startsWith("private-session-")) {
            String sessionIdStr = name.substring("private-session-".length());
            try {
                UUID privateSessionId = UUID.fromString(sessionIdStr);
                com.joinlivora.backend.privateshow.PrivateSession privateSession = privateSessionRepository.findById(privateSessionId).orElse(null);
                if (privateSession == null) {
                    throw new AccessDeniedException("Private session not found: " + sessionIdStr);
                }
                if (privateSession.getStatus() != com.joinlivora.backend.privateshow.PrivateSessionStatus.ACTIVE) {
                    throw new AccessDeniedException("Private session is not active");
                }
                if (privateSession.getViewer().getId().equals(userId) || privateSession.getCreator().getId().equals(userId)) {
                    return true;
                }
                log.warn("SECURITY: User {} attempted to access private session {} without authorization", userId, privateSessionId);
                throw new AccessDeniedException("Access denied to private session chat");
            } catch (IllegalArgumentException e) {
                throw new AccessDeniedException("Invalid private session ID: " + sessionIdStr);
            }
        }

        // PPV access check
        if (room.getPpvContent() != null) {
            ChatPpvAccessResponse ppvAccess = checkPpvAccess(new UUID(0, room.getId()), userId);
            if (ppvAccess.isHasAccess()) {
                logPpvAccess(user, room, "GRANTED", "Access check passed via checkPpvAccess");
                return true;
            }
            logPpvAccess(user, room, "DENIED", "PPV purchase required");
            throw new ChatAccessException(ChatErrorCode.CHAT_ACCESS_REQUIRED, "PPV purchase required", 
                    new UUID(0, room.getId()), room.getPpvContent().getId(), null);
        }

        // Subscription check
        if (subscriptionRepository.findByUserAndStatus(user, SubscriptionStatus.ACTIVE).isPresent()) {
            return true;
        }

        throw new AccessDeniedException("Access denied to private room: " + name);
    }

    public ChatPpvAccessResponse checkPpvAccess(UUID roomId, Long userId) {
        // Handle UUID-encoded Long IDs
        Long longRoomId = roomId.getLeastSignificantBits();
        ChatRoom room = chatRoomRepository.findById(longRoomId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat room not found: " + roomId));

        UUID ppvContentId = room.getPpvContent() != null ? room.getPpvContent().getId() : null;

        if (ppvContentId == null) {
            return ChatPpvAccessResponse.builder()
                    .hasAccess(true)
                    .build();
        }

        if (userId == null) {
            return ChatPpvAccessResponse.builder()
                    .hasAccess(false)
                    .ppvContentId(ppvContentId)
                    .build();
        }

        Creator creator = creatorRepository.findById(room.getCreatorId()).orElse(null);
        if (creator != null && creator.getUser().getId().equals(userId)) {
            return ChatPpvAccessResponse.builder()
                    .hasAccess(true)
                    .ppvContentId(ppvContentId)
                    .build();
        }

        if (room.getPpvContent().getCreator() != null && 
            room.getPpvContent().getCreator().getId().equals(userId)) {
            return ChatPpvAccessResponse.builder()
                    .hasAccess(true)
                    .ppvContentId(ppvContentId)
                    .build();
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        if (user.getRole() == Role.ADMIN || user.getRole() == Role.MODERATOR) {
            return ChatPpvAccessResponse.builder()
                    .hasAccess(true)
                    .ppvContentId(ppvContentId)
                    .build();
        }

        Instant expiresAt = null;
        boolean hasAccess = false;

        if (room.getName() != null && room.getName().startsWith("stream-")) {
            try {
                UUID streamRoomId = UUID.fromString(room.getName().substring("stream-".length()));
                var accessOpt = ppvChatAccessService.getActiveAccess(user.getId(), streamRoomId);
                if (accessOpt.isPresent()) {
                    hasAccess = true;
                    expiresAt = accessOpt.get().getExpiresAt();
                }
            } catch (Exception e) {
            }
        }

        if (!hasAccess && purchaseValidationService.hasPurchased(user.getId(), ppvContentId)) {
            hasAccess = true;
        }

        return ChatPpvAccessResponse.builder()
                .hasAccess(hasAccess)
                .expiresAt(expiresAt)
                .ppvContentId(ppvContentId)
                .build();
    }

    @Transactional
    public void deleteRoom(String name) {
        log.info("CHAT-V2: Deleting room '{}'", name);
        chatRoomRepository.findByName(name).ifPresent(chatRoomRepository::delete);
    }

    public ChatRoom getRoomEntity(UUID id) {
        return chatRoomRepository.findById(id.getLeastSignificantBits())
                .orElseThrow(() -> new ResourceNotFoundException("Chat room not found: " + id));
    }

    public ChatRoom getRoomByCreatorUserId(Long creatorUserId) {
        Creator creator = creatorRepository.findByUser_Id(creatorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Creator not found for user ID: " + creatorUserId));
        return chatRoomRepository.findMainRoom(creator.getId(), "private-session-")
                .orElseThrow(() -> new ResourceNotFoundException("Chat room not found for creatorUserId: " + creatorUserId));
    }

    public java.util.Optional<ChatRoom> getRoomByName(String name) {
        return chatRoomRepository.findByName(name);
    }

    @Transactional(readOnly = true)
    public List<ChatRoomDto> getLiveRooms() {
        return chatRoomRepository.findAllByIsLiveTrue().stream()
                .map(ChatRoomDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ChatRoomDto getRoom(UUID roomId) {
        return chatRoomRepository.findById(roomId.getLeastSignificantBits())
                .map(ChatRoomDto::fromEntity)
                .orElseThrow(() -> new ResourceNotFoundException("Chat room not found: " + roomId));
    }

    public ChatRoom getRoomByPpv(UUID ppvContentId) {
        return chatRoomRepository.findByPpvContentId(ppvContentId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat room not found for PPV content: " + ppvContentId));
    }

    @Transactional
    public ChatRoom createPpvChatRoom(UUID ppvContentId, Long creatorUserId) {
        log.info("CHAT-V2: Request to create/get PPV chat room for content {}", ppvContentId);
        PpvContent ppvContent = ppvService.getPpvContent(ppvContentId);
        
        Creator creator = creatorRepository.findByUser_Id(creatorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Creator profile not found for user ID: " + creatorUserId));

        if (!ppvContent.getCreator().getId().equals(creator.getId())) {
            log.warn("SECURITY: User {} attempted to create/access PPV chat room for content {} owned by someone else", creatorUserId, ppvContentId);
            throw new AccessDeniedException("Only the creator can manage the chat room for this PPV content");
        }

        return chatRoomRepository.findByPpvContentId(ppvContentId)
                .orElseGet(() -> {
                    String name = "ppv-" + ppvContentId;
                    return createPrivateRoom(name, creatorUserId, ppvContentId);
                });
    }

    @Transactional
    public ChatRoom updateChatMode(UUID roomId, ChatMode chatMode, Long userId) {
        ChatRoom room = chatRoomRepository.findById(roomId.getLeastSignificantBits())
                .orElseThrow(() -> new ResourceNotFoundException("Chat room not found: " + roomId));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        Creator creator = creatorRepository.findById(room.getCreatorId()).orElse(null);
        boolean isCreator = creator != null && creator.getUser().getId().equals(userId);
        boolean isStaff = user.getRole() == Role.MODERATOR || user.getRole() == Role.ADMIN;

        if (!isCreator && !isStaff) {
            log.warn("SECURITY: User {} attempted to change chat mode for room {} without permission", user.getEmail(), roomId);
            throw new AccessDeniedException("You do not have permission to change the chat mode for this room.");
        }

        log.info("CHAT-V2: User {} updating chat mode for room {} to {}", user.getEmail(), roomId, chatMode);
        ChatMode oldMode = room.getChatMode();
        room.setChatMode(chatMode);
        ChatRoom saved = chatRoomRepository.save(room);

        analyticsEventPublisher.publishEvent(
                AnalyticsEventType.CHAT_MODE_CHANGED,
                user,
                java.util.Map.of(
                        "roomId", roomId.toString(),
                        "oldMode", oldMode.name(),
                        "newMode", chatMode.name()
                )
        );

        return saved;
    }

    private void logPpvAccess(User user, ChatRoom room, String status, String reason) {
        if (room.getPpvContent() == null) return;

        java.util.Map<String, Object> metadata = new java.util.HashMap<>();
        metadata.put("roomId", room.getId());
        metadata.put("ppvContentId", room.getPpvContent().getId());
        if (user != null) {
            metadata.put("userId", user.getId());
        }
        metadata.put("reason", reason);

        AnalyticsEventType eventType = "GRANTED".equals(status)
                ? AnalyticsEventType.PPV_CHAT_ACCESS_GRANTED
                : AnalyticsEventType.PPV_CHAT_MESSAGE_BLOCKED;

        analyticsEventPublisher.publishEvent(
                eventType,
                user,
                metadata
        );
    }
}
