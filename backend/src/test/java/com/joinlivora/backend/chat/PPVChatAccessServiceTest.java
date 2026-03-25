package com.joinlivora.backend.chat;

import com.joinlivora.backend.monetization.PpvContent;
import com.joinlivora.backend.streaming.Stream;
import com.joinlivora.backend.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PPVChatAccessServiceTest {

    @Mock
    private PPVChatAccessRepository repository;

    @Mock
    private com.joinlivora.backend.user.UserRepository userRepository;

    @Mock
    private com.joinlivora.backend.streaming.StreamRepository streamRepository;

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @InjectMocks
    private PPVChatAccessService service;

    private User user;
    private Stream room;
    private PpvContent content;
    private ChatRoom chatRoom;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setEmail("creator@test.com");

        room = new Stream();
        room.setId(UUID.randomUUID());

        content = new PpvContent();
        content.setId(UUID.randomUUID());

        chatRoom = ChatRoom.builder()
                .name("liveStream-" + room.getId())
                .ppvContent(content)
                .build();
    }

    @Test
    void grantAccess_WithEntities_ShouldSaveAccess() {
        Instant expiry = Instant.now().plusSeconds(3600);
        service.grantAccess(user, room, content, expiry);

        verify(repository).save(argThat(access -> 
            access.getUserId().equals(user) &&
            access.getRoomId().equals(room) &&
            access.getPpvContentId().equals(content) &&
            access.getExpiresAt().equals(expiry)
        ));
    }

    @Test
    void grantAccess_WithIds_ShouldFetchAndSave() {
        Instant expiry = Instant.now().plusSeconds(3600);
        when(userRepository.findById(1L)).thenReturn(java.util.Optional.of(user));
        when(streamRepository.findById(room.getId())).thenReturn(java.util.Optional.of(room));
        when(chatRoomRepository.findByName("liveStream-" + room.getId())).thenReturn(java.util.Optional.of(chatRoom));

        service.grantAccess(1L, room.getId(), expiry);

        verify(repository).save(any(PPVChatAccess.class));
    }

    @Test
    void revokeAccess_ShouldCallRepository() {
        service.revokeAccess(1L, room.getId());
        verify(repository).deleteByUserAndRoom(1L, room.getId());
    }

    @Test
    void hasAccess_ShouldCallRepository() {
        UUID roomId = UUID.randomUUID();
        when(repository.existsActiveAccess(eq(1L), eq(roomId), any(Instant.class))).thenReturn(true);

        assertTrue(service.hasAccess(1L, roomId));
        verify(repository).existsActiveAccess(eq(1L), eq(roomId), any(Instant.class));
    }

    @Test
    void cleanupExpiredAccess_ShouldCallRepository() {
        service.cleanupExpiredAccess();
        verify(repository).deleteExpired(any(Instant.class));
    }
}









