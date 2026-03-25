package com.joinlivora.backend.streaming.service;

import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import com.joinlivora.backend.websocket.RealtimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StreamModerationServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private StreamModerationService liveStreamModerationService;

    private Long creatorId = 1L;
    private Long userId = 2L;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void muteUser_ShouldSetRedisKeyWithTTL() {
        liveStreamModerationService.muteUser(creatorId, userId, 10);
        
        String expectedKey = "liveStream:1:muted:2";
        verify(valueOperations).set(eq(expectedKey), eq("true"), eq(10L), eq(TimeUnit.MINUTES));
    }

    @Test
    void shadowMuteUser_ShouldSetRedisKey() {
        liveStreamModerationService.shadowMuteUser(creatorId, userId);
        
        String expectedKey = "liveStream:1:shadow:2";
        verify(valueOperations).set(eq(expectedKey), eq("true"), eq(24L), eq(TimeUnit.HOURS));
    }

    @Test
    void kickUser_ShouldSendWebSocketEvent() {
        User user = new User();
        user.setId(userId);
        user.setEmail("user@test.com");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        liveStreamModerationService.kickUser(creatorId, userId);

        ArgumentCaptor<RealtimeMessage> eventCaptor = ArgumentCaptor.forClass(RealtimeMessage.class);
        verify(messagingTemplate).convertAndSendToUser(eq(userId.toString()), eq("/queue/moderation"), eventCaptor.capture());

        RealtimeMessage capturedEvent = eventCaptor.getValue();
        assertEquals("KICK", capturedEvent.getType());
        assertEquals(creatorId, capturedEvent.getPayload().get("creatorId"));
    }

    @Test
    void unmuteUser_ShouldDeleteRedisKey() {
        liveStreamModerationService.unmuteUser(creatorId, userId);
        
        String expectedKey = "liveStream:1:muted:2";
        verify(redisTemplate).delete(eq(expectedKey));
    }

    @Test
    void isMuted_WhenKeyExists_ShouldReturnTrue() {
        String key = "liveStream:1:muted:2";
        when(redisTemplate.hasKey(key)).thenReturn(true);

        assertTrue(liveStreamModerationService.isMuted(creatorId, userId));
    }

    @Test
    void isMuted_WhenKeyDoesNotExist_ShouldReturnFalse() {
        String key = "liveStream:1:muted:2";
        when(redisTemplate.hasKey(key)).thenReturn(false);

        assertFalse(liveStreamModerationService.isMuted(creatorId, userId));
    }

    @Test
    void isShadowMuted_WhenKeyExists_ShouldReturnTrue() {
        String key = "liveStream:1:shadow:2";
        when(redisTemplate.hasKey(key)).thenReturn(true);

        assertTrue(liveStreamModerationService.isShadowMuted(creatorId, userId));
    }
}








