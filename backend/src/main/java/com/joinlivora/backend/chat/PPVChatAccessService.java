package com.joinlivora.backend.chat;

import com.joinlivora.backend.chat.domain.ChatRoom;
import com.joinlivora.backend.chat.repository.ChatRoomRepository;
import com.joinlivora.backend.exception.ResourceNotFoundException;
import com.joinlivora.backend.monetization.PpvContent;
import com.joinlivora.backend.streaming.Stream;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@Slf4j
public class PPVChatAccessService {

    private final PPVChatAccessRepository repository;
    private final UserRepository userRepository;
    private final com.joinlivora.backend.streaming.StreamRepository streamRepository;
    private final ChatRoomRepository chatRoomRepository;

    public PPVChatAccessService(
            PPVChatAccessRepository repository,
            UserRepository userRepository,
            com.joinlivora.backend.streaming.StreamRepository streamRepository,
            @Qualifier("chatRoomRepositoryV2") ChatRoomRepository chatRoomRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.streamRepository = streamRepository;
        this.chatRoomRepository = chatRoomRepository;
    }

    @Transactional
    public void grantAccess(User user, Stream room, PpvContent ppvContent, Instant expiresAt) {
        log.info("CHAT: Granting PPV chat access to creator {} for room {} (PPV: {})",
                user.getEmail(), room.getId(), ppvContent.getId());
        
        PPVChatAccess access = PPVChatAccess.builder()
                .userId(user)
                .roomId(room)
                .ppvContentId(ppvContent)
                .expiresAt(expiresAt)
                .build();
        
        repository.save(access);
    }

    @Transactional
    public void grantAccess(Long userId, UUID roomId, Instant expiresAt) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        // Resolve unified Stream entity
        Stream stream = streamRepository.findById(roomId)
                .orElseGet(() -> streamRepository.findByMediasoupRoomId(roomId)
                        .orElseThrow(() -> new ResourceNotFoundException("Active unified stream not found for roomId: " + roomId)));

        // Find associated PpvContent via ChatRoom (V2)
        ChatRoom chatRoom = chatRoomRepository.findByName("stream-" + roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat room not found for stream: " + roomId));

        if (chatRoom.getPpvContent() == null) {
            throw new IllegalStateException("No PPV content associated with this room");
        }

        grantAccess(user, stream, chatRoom.getPpvContent(), expiresAt);
    }

    @Transactional
    public void revokeAccess(Long userId, UUID roomId) {
        log.info("CHAT: Revoking PPV chat access for creator {} from room {}", userId, roomId);
        repository.deleteByUserAndRoom(userId, roomId);
    }

    public boolean hasAccess(Long userId, UUID roomId) {
        return repository.existsActiveAccess(userId, roomId, Instant.now());
    }

    public java.util.Optional<PPVChatAccess> getActiveAccess(Long userId, UUID roomId) {
        return repository.findByUserAndRoom(userId, roomId).stream()
                .filter(access -> access.getExpiresAt() == null || access.getExpiresAt().isAfter(Instant.now()))
                .findFirst();
    }

    @Transactional
    public void cleanupExpiredAccess() {
        log.info("CHAT: Cleaning up expired PPV chat access records");
        repository.deleteExpired(Instant.now());
    }

    @Scheduled(cron = "0 0 * * * *") // Run every hour
    public void scheduledCleanup() {
        cleanupExpiredAccess();
    }
}
