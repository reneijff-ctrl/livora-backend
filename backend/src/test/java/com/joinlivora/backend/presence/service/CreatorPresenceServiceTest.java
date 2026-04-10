package com.joinlivora.backend.presence.service;

import com.joinlivora.backend.presence.entity.CreatorPresence;
import com.joinlivora.backend.presence.repository.CreatorPresenceRepository;
import com.joinlivora.backend.streaming.StreamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreatorPresenceServiceTest {

    @Mock
    private CreatorPresenceRepository repository;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private OnlineCreatorRegistry onlineCreatorRegistry;

    @Mock
    private StreamRepository streamRepository;

    @Mock
    private com.joinlivora.backend.creator.repository.CreatorRepository creatorRepository;

    @InjectMocks
    private CreatorPresenceService creatorPresenceService;

    private final Long creatorId = 1L;

    @BeforeEach
    void setUp() {
    }

    @Test
    void isOnline_shouldReturnTrue_WhenMarkedOnlineAndRecent() {
        Long userId = 100L;
        com.joinlivora.backend.creator.model.Creator creator = new com.joinlivora.backend.creator.model.Creator();
        creator.setId(creatorId);
        creator.setUser(new com.joinlivora.backend.user.User());
        creator.getUser().setId(userId);
        
        when(creatorRepository.findById(creatorId)).thenReturn(Optional.of(creator));
        when(streamRepository.countByCreatorIdAndIsLiveTrue(userId)).thenReturn(0L);
        when(onlineCreatorRegistry.isOnline(creatorId)).thenReturn(true);

        assertTrue(creatorPresenceService.isOnline(creatorId));
    }

    @Test
    void isOnline_shouldReturnFalse_WhenMarkedOffline() {
        Long userId = 100L;
        com.joinlivora.backend.creator.model.Creator creator = new com.joinlivora.backend.creator.model.Creator();
        creator.setId(creatorId);
        creator.setUser(new com.joinlivora.backend.user.User());
        creator.getUser().setId(userId);
        
        when(creatorRepository.findById(creatorId)).thenReturn(Optional.of(creator));
        when(streamRepository.countByCreatorIdAndIsLiveTrue(userId)).thenReturn(0L);
        when(onlineCreatorRegistry.isOnline(creatorId)).thenReturn(false);

        assertFalse(creatorPresenceService.isOnline(creatorId));
    }

    @Test
    void isOnline_shouldReturnFalse_WhenNoCreatorEntry() {
        when(creatorRepository.findById(creatorId)).thenReturn(Optional.empty());

        assertFalse(creatorPresenceService.isOnline(creatorId));
    }

    @Test
    void isOnline_shouldReturnTrue_WhenLive() {
        Long userId = 100L;
        com.joinlivora.backend.creator.model.Creator creator = new com.joinlivora.backend.creator.model.Creator();
        creator.setId(creatorId);
        creator.setUser(new com.joinlivora.backend.user.User());
        creator.getUser().setId(userId);
        
        when(creatorRepository.findById(creatorId)).thenReturn(Optional.of(creator));
        when(streamRepository.countByCreatorIdAndIsLiveTrue(userId)).thenReturn(1L);

        assertTrue(creatorPresenceService.isOnline(creatorId));
    }

    @Test
    void markOnline_shouldSaveToRepoAndRedis() {
        when(repository.findByCreatorId(creatorId)).thenReturn(Optional.empty());
        when(redisTemplate.getConnectionFactory()).thenReturn(mock(org.springframework.data.redis.connection.RedisConnectionFactory.class));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        creatorPresenceService.markOnline(creatorId);

        verify(repository).save(any(CreatorPresence.class));
        verify(valueOperations).set(eq("creator:presence:" + creatorId), any(CreatorPresence.class), any(java.time.Duration.class));
    }

    @Test
    void markOffline_shouldSaveToRepoAndRedis() {
        when(repository.findByCreatorId(creatorId)).thenReturn(Optional.empty());
        when(redisTemplate.getConnectionFactory()).thenReturn(mock(org.springframework.data.redis.connection.RedisConnectionFactory.class));

        creatorPresenceService.markOffline(creatorId);

        verify(repository).save(any(CreatorPresence.class));
        verify(redisTemplate).delete(eq("creator:presence:" + creatorId));
    }

    @Test
    void getOnlineCreators_shouldReturnFromRepoWithTTL() {
        CreatorPresence presence1 = CreatorPresence.builder().creatorId(1L).online(true).lastSeen(java.time.Instant.now()).build();
        CreatorPresence presence2 = CreatorPresence.builder().creatorId(2L).online(true).lastSeen(java.time.Instant.now().minusSeconds(61)).build();
        
        when(repository.findByOnlineTrue()).thenReturn(List.of(presence1, presence2));

        List<CreatorPresence> result = creatorPresenceService.getOnlineCreators();

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getCreatorId());
    }

    @Test
    void getPresence_shouldReturnFromRepoAndApplyTTL() {
        CreatorPresence presence = CreatorPresence.builder()
                .creatorId(creatorId)
                .online(true)
                .lastSeen(java.time.Instant.now().minusSeconds(61))
                .build();
        when(repository.findByCreatorId(creatorId)).thenReturn(Optional.of(presence));

        Optional<CreatorPresence> result = creatorPresenceService.getPresence(creatorId);

        assertTrue(result.isPresent());
        assertFalse(result.get().isOnline()); // TTL should have marked it offline
    }

    @Test
    void refreshLastSeen_shouldNotChangeOnlineStatus() {
        CreatorPresence presence = CreatorPresence.builder()
                .creatorId(creatorId)
                .online(false)
                .lastSeen(java.time.Instant.now().minusSeconds(120))
                .build();
        when(repository.findByCreatorId(creatorId)).thenReturn(Optional.of(presence));

        creatorPresenceService.refreshLastSeen(creatorId);

        verify(repository).save(argThat(p -> p.getCreatorId().equals(creatorId) && !p.isOnline()));
        // lastSeen should be updated but we can't easily check it with argThat without capturing
    }
}








