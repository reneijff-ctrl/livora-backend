package com.joinlivora.backend.pm;

import com.joinlivora.backend.chat.domain.ChatMessage;
import com.joinlivora.backend.chat.domain.ChatRoom;
import com.joinlivora.backend.chat.domain.ChatRoomStatus;
import com.joinlivora.backend.chat.domain.ChatRoomType;
import com.joinlivora.backend.chat.dto.ChatMessageDto;
import com.joinlivora.backend.chat.repository.ChatMessageRepository;
import com.joinlivora.backend.chat.repository.ChatRoomRepository;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PmService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserService userService;
    private final SimpMessagingTemplate messagingTemplate;
    private final PmReadStateRepository pmReadStateRepository;

    @Transactional
    public PmSessionDto startSession(Long creatorId, Long viewerId) {
        if (creatorId.equals(viewerId)) {
            throw new IllegalArgumentException("Cannot start PM with yourself");
        }

        User viewer = userService.getById(viewerId);
        User creator = userService.getById(creatorId);

        Optional<ChatRoom> existing = chatRoomRepository
                .findByCreatorIdAndViewerIdAndRoomTypeAndStatus(creatorId, viewerId, ChatRoomType.PM, ChatRoomStatus.ACTIVE);

        if (existing.isPresent()) {
            ChatRoom room = existing.get();
            return mapToDto(room, creator, viewer, creatorId);
        }

        ChatRoom room = ChatRoom.builder()
                .creatorId(creatorId)
                .viewerId(viewerId)
                .roomType(ChatRoomType.PM)
                .isPrivate(true)
                .isLive(true)
                .name("pm-" + creatorId + "-" + viewerId + "-" + UUID.randomUUID())
                .status(ChatRoomStatus.ACTIVE)
                .build();

        chatRoomRepository.save(room);
        log.info("Creating PM room: {}", room.getName());
        log.info("PM session created: room={} creator={} viewer={}", room.getId(), creatorId, viewerId);

        PmSessionDto dto = mapToDto(room, creator, viewer, creatorId);

        try {
            messagingTemplate.convertAndSendToUser(
                    viewerId.toString(),
                    "/queue/pm-events",
                    Map.of(
                            "type", "PM_SESSION_STARTED",
                            "roomId", room.getId(),
                            "creatorId", creatorId,
                            "creatorUsername", creator.getUsername()
                    )
            );
        } catch (Exception e) {
            log.warn("Failed to send PM_SESSION_STARTED WebSocket event: {}", e.getMessage());
        }

        return dto;
    }

    @Transactional(readOnly = true)
    public List<PmSessionDto> getActiveSessions(Long userId) {
        List<ChatRoom> creatorRooms = chatRoomRepository.findByCreatorIdAndRoomType(userId, ChatRoomType.PM);
        List<ChatRoom> viewerRooms = chatRoomRepository.findByViewerIdAndRoomType(userId, ChatRoomType.PM);

        return java.util.stream.Stream.concat(creatorRooms.stream(), viewerRooms.stream())
                .distinct()
                .filter(room -> room.getStatus() == ChatRoomStatus.ACTIVE)
                .map(room -> {
                    User creator = userService.getById(room.getCreatorId());
                    User viewer = userService.getById(room.getViewerId());
                    return mapToDto(room, creator, viewer, userId);
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ChatMessageDto> getMessages(Long roomId, Long userId) {
        ChatRoom room = getRoomById(roomId);
        if (!userId.equals(room.getCreatorId()) && !userId.equals(room.getViewerId())) {
            throw new IllegalArgumentException("Not a participant of this PM session");
        }

        List<ChatMessage> messages = chatMessageRepository.findByRoomIdOrderByCreatedAtAsc(roomId);
        return messages.stream().map(m -> {
            User sender = userService.getById(m.getSenderId());
            return ChatMessageDto.builder()
                    .id(String.valueOf(m.getId()))
                    .type("PM_MESSAGE")
                    .senderId(m.getSenderId())
                    .senderUsername(sender.getUsername())
                    .senderRole(m.getSenderRole())
                    .content(m.getContent())
                    .timestamp(m.getCreatedAt())
                    .build();
        }).collect(Collectors.toList());
    }

    @Transactional
    public void endSession(Long roomId, Long userId) {
        ChatRoom room = getRoomById(roomId);
        if (!userId.equals(room.getCreatorId()) && !userId.equals(room.getViewerId())) {
            throw new IllegalArgumentException("Not a participant of this PM session");
        }

        room.setStatus(ChatRoomStatus.ENDED);
        chatRoomRepository.save(room);
        log.info("PM session ended: room={} by user={}", roomId, userId);

        Map<String, Object> endPayload = Map.of(
                "type", "PM_SESSION_ENDED",
                "roomId", roomId
        );

        try {
            messagingTemplate.convertAndSendToUser(
                    room.getCreatorId().toString(),
                    "/queue/pm-events",
                    endPayload
            );
        } catch (Exception e) {
            log.warn("Failed to send PM_SESSION_ENDED to creator: {}", e.getMessage());
        }

        try {
            messagingTemplate.convertAndSendToUser(
                    room.getViewerId().toString(),
                    "/queue/pm-events",
                    endPayload
            );
        } catch (Exception e) {
            log.warn("Failed to send PM_SESSION_ENDED to viewer: {}", e.getMessage());
        }
    }

    @Transactional
    public void updateReadStateOnMessage(Long roomId, Long senderId, Long messageId) {
        ChatRoom room = getRoomById(roomId);
        Long creatorId = room.getCreatorId();
        Long viewerId = room.getViewerId();

        // Sender: reset unread to 0, update last read message
        PmReadState senderState = pmReadStateRepository.findByRoomIdAndUserId(roomId, senderId)
                .orElse(PmReadState.builder().roomId(roomId).userId(senderId).build());
        senderState.setUnreadCount(0);
        senderState.setLastReadMessageId(messageId);
        pmReadStateRepository.save(senderState);

        // Receiver: increment unread count
        Long receiverId = senderId.equals(creatorId) ? viewerId : creatorId;
        PmReadState receiverState = pmReadStateRepository.findByRoomIdAndUserId(roomId, receiverId)
                .orElse(PmReadState.builder().roomId(roomId).userId(receiverId).build());
        receiverState.setUnreadCount(Math.max(0, receiverState.getUnreadCount()) + 1);
        pmReadStateRepository.save(receiverState);
    }

    @Transactional
    public void markAsRead(Long roomId, Long userId) {
        ChatRoom room = getRoomById(roomId);
        if (!userId.equals(room.getCreatorId()) && !userId.equals(room.getViewerId())) {
            throw new IllegalArgumentException("Not a participant of this PM session");
        }

        PmReadState readState = pmReadStateRepository.findByRoomIdAndUserId(roomId, userId)
                .orElse(PmReadState.builder().roomId(roomId).userId(userId).build());

        readState.setUnreadCount(0);

        // Set last read message to the latest message in the room
        chatMessageRepository.findTopByRoomIdOrderByCreatedAtDesc(roomId)
                .ifPresent(msg -> readState.setLastReadMessageId(msg.getId()));

        pmReadStateRepository.save(readState);
        log.info("Marked PM room {} as read for user {}", roomId, userId);
    }

    @Transactional(readOnly = true)
    public ChatRoom getRoomById(Long roomId) {
        return chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("PM room not found: " + roomId));
    }

    private PmSessionDto mapToDto(ChatRoom room, User creator, User viewer, Long currentUserId) {
        Integer unreadCount = pmReadStateRepository.findByRoomIdAndUserId(room.getId(), currentUserId)
                .map(PmReadState::getUnreadCount)
                .orElse(0);

        Optional<ChatMessage> lastMsg = chatMessageRepository.findTopByRoomIdOrderByCreatedAtDesc(room.getId());
        String lastMessage = lastMsg.map(ChatMessage::getContent).orElse(null);
        java.time.Instant lastMessageTime = lastMsg.map(ChatMessage::getCreatedAt).orElse(null);

        return new PmSessionDto(
                room.getId(),
                creator.getId(),
                creator.getUsername(),
                viewer.getId(),
                viewer.getUsername(),
                room.getCreatedAt(),
                unreadCount,
                lastMessage,
                lastMessageTime
        );
    }
}
