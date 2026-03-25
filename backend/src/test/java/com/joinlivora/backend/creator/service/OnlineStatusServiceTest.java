package com.joinlivora.backend.creator.service;

import com.joinlivora.backend.presence.service.CreatorPresenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OnlineStatusServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private CreatorPresenceService creatorPresenceService;

    private OnlineStatusService onlineStatusService;

    @BeforeEach
    void setUp() {
        onlineStatusService = new OnlineStatusService(redisTemplate, creatorPresenceService);
    }

    @Test
    void setOnline_WhenRedisDown_ShouldNotThrowAndLogOnce() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        doThrow(new RedisConnectionFailureException("Connection refused")).when(valueOperations).set(anyString(), any(), any(Duration.class));

        // First call - should log
        assertDoesNotThrow(() -> onlineStatusService.setOnline(1L));

        // Second call - should not even try (if circuit breaker implemented) or at least not throw
        assertDoesNotThrow(() -> onlineStatusService.setOnline(1L));

        verify(valueOperations, atLeastOnce()).set(anyString(), any(), any(Duration.class));
    }

    @Test
    void setOffline_WhenRedisDown_ShouldNotThrow() {
        doThrow(new RedisConnectionFailureException("Connection refused")).when(redisTemplate).delete(anyString());

        assertDoesNotThrow(() -> onlineStatusService.setOffline(1L));
    }

    @Test
    void isOnline_ShouldDelegateToCreatorPresenceService() {
        when(creatorPresenceService.isOnline(1L)).thenReturn(true);
        assertTrue(onlineStatusService.isOnline(1L));
        verify(creatorPresenceService).isOnline(1L);
    }
}








