package com.joinlivora.backend.streaming.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LiveViewerCounterServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private LivestreamAnalyticsService analyticsService;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    private LiveViewerCounterService service;

    @BeforeEach
    void setUp() {
        service = new LiveViewerCounterService(redisTemplate, messagingTemplate, analyticsService);
        lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
    }

    @Test
    void recordViewerHistory_ShouldAddAndLimitEntries() {
        // Given
        UUID streamId = UUID.randomUUID();
        long count = 100;
        String key = "viewer-history:" + streamId;

        // When
        service.recordViewerHistory(streamId, count);

        // Then
        verify(zSetOperations).add(eq(key), eq("100"), anyDouble());
        verify(zSetOperations).removeRange(eq(key), eq(0L), eq(-6L));
        verify(redisTemplate).expire(eq(key), any());
    }

    @Test
    void getPreviousViewerCount_ShouldReturnSecondNewest() {
        // Given
        UUID streamId = UUID.randomUUID();
        String key = "viewer-history:" + streamId;
        Set<String> history = Collections.singleton("150");
        when(zSetOperations.reverseRange(key, 1, 1)).thenReturn(history);

        // When
        Long result = service.getPreviousViewerCount(streamId);

        // Then
        assertEquals(150L, result);
    }

    @Test
    void getPreviousViewerCount_WhenEmpty_ShouldReturnNull() {
        // Given
        UUID streamId = UUID.randomUUID();
        String key = "viewer-history:" + streamId;
        when(zSetOperations.reverseRange(key, 1, 1)).thenReturn(null);

        // When
        Long result = service.getPreviousViewerCount(streamId);

        // Then
        assertNull(result);
    }
}
