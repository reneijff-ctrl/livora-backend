package com.joinlivora.backend.chat;

import com.joinlivora.backend.analytics.AnalyticsEventPublisher;
import com.joinlivora.backend.analytics.AnalyticsEventType;
import com.joinlivora.backend.chat.dto.ChatErrorCode;
import com.joinlivora.backend.chat.dto.ChatRoomDto;
import com.joinlivora.backend.chat.dto.ChatPpvAccessResponse;
import com.joinlivora.backend.creator.service.CreatorProfileService;
import com.joinlivora.backend.exception.ChatAccessException;
import com.joinlivora.backend.exception.ResourceNotFoundException;
import com.joinlivora.backend.exception.chat.ChatRoomAlreadyExistsException;
import com.joinlivora.backend.exception.chat.PpvRoomAlreadyExistsException;
import com.joinlivora.backend.monetization.PPVPurchaseValidationService;
import com.joinlivora.backend.monetization.PpvContent;
import com.joinlivora.backend.monetization.PpvService;
import com.joinlivora.backend.payment.SubscriptionStatus;
import com.joinlivora.backend.payment.UserSubscriptionRepository;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * @deprecated Use com.joinlivora.backend.chat.service.ChatRoomService instead.
 * All logic has been migrated to the V2 canonical service.
 */
@Deprecated
@Slf4j
@RequiredArgsConstructor
public class ChatRoomService {

    private final ChatRoomRepository chatRoomRepositoryV1;
    private final UserRepository userRepository;
    private final UserSubscriptionRepository subscriptionRepository;
    private final PpvService ppvService;
    private final PPVPurchaseValidationService purchaseValidationService;
    private final PPVChatAccessService ppvChatAccessService;
    private final AnalyticsEventPublisher analyticsEventPublisher;
    private final com.joinlivora.backend.privateshow.PrivateSessionRepository privateSessionRepository;

    public ChatRoom createPublicRoom(String name, Long creatorId) {
        log.info("CHAT: Creating public room '{}' for creator ID {}", name, creatorId);
        validateRoomNameUnique(name);

        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new ResourceNotFoundException("Creator not found: " + creatorId));

        ChatRoom chatRoom = ChatRoom.builder()
                .name(name)
                .createdBy(creator)
                .isPrivate(false)
                .build();

        return chatRoomRepositoryV1.save(chatRoom);
    }

    public ChatRoom createPrivateRoom(String name, Long creatorId) {
        return createPrivateRoom(name, creatorId, null);
    }

    public ChatRoom createPrivateRoom(String name, Long creatorId, UUID ppvContentId) {
        log.info("CHAT: Creating private room '{}' for creator ID {} (PPV: {})", name, creatorId, ppvContentId);
        validateRoomNameUnique(name);

        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new ResourceNotFoundException("Creator not found: " + creatorId));

        PpvContent ppvContent = null;
        if (ppvContentId != null) {
            if (chatRoomRepositoryV1.findByPpvContent_Id(ppvContentId).isPresent()) {
                throw new PpvRoomAlreadyExistsException("A chat room already exists for this PPV content: " + ppvContentId);
            }
            ppvContent = ppvService.getPpvContent(ppvContentId);
        }

        ChatRoom chatRoom = ChatRoom.builder()
                .name(name)
                .createdBy(creator)
                .isPrivate(true)
                .ppvContent(ppvContent)
                .build();

        return chatRoomRepositoryV1.save(chatRoom);
    }

    public ChatRoom createLiveStreamRoom(UUID streamRoomId, Long creatorId, boolean isPremium, UUID ppvContentId, boolean isPaid, Long pricePerMessage) {
        String name = "stream-" + streamRoomId;
        
        PpvContent ppvContent = null;
        if (ppvContentId != null) {
            ppvContent = ppvService.getPpvContent(ppvContentId);
        }

        PpvContent finalPpvContent = ppvContent;
        return chatRoomRepositoryV1.findByName(name)
                .map(room -> {
                    log.info("CHAT: Updating existing stream room '{}' (Premium: {}, PPV: {}, Paid: {}, Price: {})", 
                            name, isPremium, ppvContentId, isPaid, pricePerMessage);
                    room.setPrivate(isPremium || finalPpvContent != null);
                    room.setPpvContent(finalPpvContent);
                    room.setPaid(isPaid);
                    room.setPricePerMessage(pricePerMessage);
                    return chatRoomRepositoryV1.save(room);
                })
                .orElseGet(() -> {
                    log.info("CHAT: Creating new stream room '{}' (Premium: {}, PPV: {}, Paid: {}, Price: {})", 
                            name, isPremium, ppvContentId, isPaid, pricePerMessage);
                    User creator = userRepository.findById(creatorId)
                            .orElseThrow(() -> new ResourceNotFoundException("Creator not found: " + creatorId));

                    ChatRoom chatRoom = ChatRoom.builder()
                            .name(name)
                            .createdBy(creator)
                            .isPrivate(isPremium || finalPpvContent != null)
                            .ppvContent(finalPpvContent)
                            .isPaid(isPaid)
                            .pricePerMessage(pricePerMessage)
                            .build();

                    return chatRoomRepositoryV1.save(chatRoom);
                });
    }

    private void validateRoomNameUnique(String name) {
        if (chatRoomRepositoryV1.findByName(name).isPresent()) {
            throw new ChatRoomAlreadyExistsException("Room name already exists: " + name);
        }
    }

    public boolean validateAccess(String name, Long userId) {
        log.debug("CHAT: validateAccess called for room '{}' and userId {}", name, userId);
        
        ChatRoom room = chatRoomRepositoryV1.findByName(name)
                .orElseThrow(() -> new AccessDeniedException("Room not found: " + name));

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

        if (room.getCreatedBy().getId().equals(userId)) {
            return true;
        }

        // PPV access check
        if (room.getPpvContent() != null) {
            ChatPpvAccessResponse ppvAccess = checkPpvAccess(room.getId(), userId);
            if (ppvAccess.isHasAccess()) {
                logPpvAccess(user, room, "GRANTED", "Access check passed via checkPpvAccess");
                return true;
            }
            logPpvAccess(user, room, "DENIED", "PPV purchase required");
            throw new ChatAccessException(ChatErrorCode.CHAT_ACCESS_REQUIRED, "PPV purchase required", room.getId(), room.getPpvContent().getId(), null);
        }

        // Subscription check
        if (subscriptionRepository.findByUserAndStatus(user, SubscriptionStatus.ACTIVE).isPresent()) {
            return true;
        }

        throw new AccessDeniedException("Access denied to private room: " + name);
    }

    public ChatPpvAccessResponse checkPpvAccess(UUID roomId, Long userId) {
        ChatRoom room = chatRoomRepositoryV1.findById(roomId)
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

        if (room.getCreatedBy().getId().equals(userId)) {
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

        if (room.getName().startsWith("stream-")) {
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

    public void deleteRoom(String name) {
        log.info("CHAT: Deleting room '{}'", name);
        chatRoomRepositoryV1.findByName(name).ifPresent(chatRoomRepositoryV1::delete);
    }

    public ChatRoom getRoomEntity(UUID id) {
        return chatRoomRepositoryV1.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Chat room not found: " + id));
    }

    public java.util.Optional<ChatRoom> getRoomByName(String name) {
        return chatRoomRepositoryV1.findByName(name);
    }

    public List<ChatRoomDto> getLiveRooms() {
        return chatRoomRepositoryV1.findAllByIsLiveTrue().stream()
                .map(ChatRoomDto::fromEntity)
                .collect(Collectors.toList());
    }

    public ChatRoomDto getRoom(UUID roomId) {
        return chatRoomRepositoryV1.findById(roomId)
                .map(ChatRoomDto::fromEntity)
                .orElseThrow(() -> new ResourceNotFoundException("Chat room not found: " + roomId));
    }

    public ChatRoom getRoomByPpv(UUID ppvContentId) {
        return chatRoomRepositoryV1.findByPpvContent_Id(ppvContentId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat room not found for PPV content: " + ppvContentId));
    }

    public ChatRoom createPpvChatRoom(UUID ppvContentId, Long creatorId) {
        log.info("CHAT: Request to create/get PPV chat room for content {}", ppvContentId);
        PpvContent ppvContent = ppvService.getPpvContent(ppvContentId);
        if (!ppvContent.getCreator().getId().equals(creatorId)) {
            log.warn("SECURITY: User {} attempted to create/access PPV chat room for content {} owned by someone else via POST", creatorId, ppvContentId);
            throw new AccessDeniedException("Only the creator can manage the chat room for this PPV content");
        }

        return chatRoomRepositoryV1.findByPpvContent_Id(ppvContentId)
                .orElseGet(() -> {
                    String name = "ppv-" + ppvContentId;
                    return createPrivateRoom(name, creatorId, ppvContentId);
                });
    }

    public ChatRoom updateChatMode(UUID roomId, ChatMode chatMode, Long userId) {
        ChatRoom room = chatRoomRepositoryV1.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat room not found: " + roomId));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        boolean isCreator = room.getCreatedBy().getId().equals(userId);
        boolean isStaff = user.getRole() == Role.MODERATOR || user.getRole() == Role.ADMIN;

        if (!isCreator && !isStaff) {
            log.warn("SECURITY: User {} attempted to change chat mode for room {} without permission", user.getEmail(), roomId);
            throw new AccessDeniedException("You do not have permission to change the chat mode for this room.");
        }

        log.info("CHAT: User {} updating chat mode for room {} to {}", user.getEmail(), roomId, chatMode);
        ChatMode oldMode = room.getChatMode();
        room.setChatMode(chatMode);
        ChatRoom saved = chatRoomRepositoryV1.save(room);

        analyticsEventPublisher.publishEvent(
                AnalyticsEventType.CHAT_MODE_CHANGED,
                user,
                java.util.Map.of(
                        "roomId", roomId,
                        "oldMode", oldMode.name(),
                        "newMode", chatMode.name()
                )
        );

        return saved;
    }

    private void logPpvAccess(User user, ChatRoom room, String status, String reason) {
        if (!room.isPpvRoom()) return;

        java.util.Map<String, Object> metadata = new java.util.HashMap<>();
        metadata.put("roomId", room.getId());
        if (room.getPpvContent() != null) {
            metadata.put("ppvContentId", room.getPpvContent().getId());
        }
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
