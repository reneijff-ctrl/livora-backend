package com.joinlivora.backend.chat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatRateLimiterServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private ChatRateLimiterService rateLimiterService;

    private final Long userId = 123L;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void isAllowed_FirstMessage_ShouldBeAllowedAndSetExpiry() {
        when(valueOperations.increment(anyString())).thenReturn(1L);

        assertTrue(rateLimiterService.isAllowed(userId));

        verify(redisTemplate).expire(eq("chat:rate:" + userId), eq(10L), eq(TimeUnit.SECONDS));
    }

    @Test
    void isAllowed_WithinLimit_ShouldBeAllowed() {
        when(valueOperations.increment(anyString())).thenReturn(5L);

        assertTrue(rateLimiterService.isAllowed(userId));
        
        // Expiry only set on first increment in this implementation
        verify(redisTemplate, never()).expire(anyString(), anyLong(), any());
    }

    @Test
    void isAllowed_ExceedLimit_ShouldBeBlocked() {
        when(valueOperations.increment(anyString())).thenReturn(6L);

        assertFalse(rateLimiterService.isAllowed(userId));
    }

    @Test
    void isAllowed_SystemMessage_ShouldBeAllowed() {
        assertTrue(rateLimiterService.isAllowed(null));
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void isAllowed_RedisError_ShouldFailOpen() {
        when(valueOperations.increment(anyString())).thenThrow(new RuntimeException("Redis down"));

        assertTrue(rateLimiterService.isAllowed(userId));
    }
}








