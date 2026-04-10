package com.joinlivora.backend.chat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatRateLimitServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    private ChatRateLimitService service;
    private final Long userId = 1L;
    private final UUID roomId = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        service = new ChatRateLimitService(redisTemplate);
        service.setSlowModeIntervalSeconds(3);
    }

    @Test
    void validateMessageRate_FirstMessage_ShouldSucceed() {
        when(valueOps.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(Boolean.TRUE);

        assertDoesNotThrow(() -> service.validateMessageRate(userId, roomId));
        verify(valueOps).setIfAbsent(eq("chat:slow:" + userId + ":" + roomId), eq("1"), eq(Duration.ofSeconds(3)));
    }

    @Test
    void validateMessageRate_SecondMessageImmediate_ShouldFail() {
        // Key already exists (slow mode active)
        when(valueOps.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(Boolean.FALSE);

        assertThrows(RuntimeException.class, () -> service.validateMessageRate(userId, roomId));
    }

    @Test
    void validateMessageRate_DifferentRooms_ShouldBeIndependent() {
        UUID otherRoom = UUID.fromString("00000000-0000-0000-0000-000000000002");
        when(valueOps.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(Boolean.TRUE);

        assertDoesNotThrow(() -> service.validateMessageRate(userId, roomId));
        assertDoesNotThrow(() -> service.validateMessageRate(userId, otherRoom));
    }

    @Test
    void validateMessageRate_DifferentUsers_ShouldBeIndependent() {
        when(valueOps.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(Boolean.TRUE);

        assertDoesNotThrow(() -> service.validateMessageRate(userId, roomId));
        assertDoesNotThrow(() -> service.validateMessageRate(2L, roomId));
    }

    @Test
    void validateMessageRate_DisabledSlowMode_ShouldAlwaysSucceed() {
        service.setSlowModeIntervalSeconds(0);

        assertDoesNotThrow(() -> service.validateMessageRate(userId, roomId));
        assertDoesNotThrow(() -> service.validateMessageRate(userId, roomId));
        // Should never touch Redis when slow mode is disabled
        verifyNoInteractions(valueOps);
    }

    @Test
    void validateMessageRate_RedisUnavailable_ShouldFailOpen() {
        // Simulate Redis throwing a non-RuntimeException-derived DataAccessException
        when(valueOps.setIfAbsent(anyString(), eq("1"), any(Duration.class)))
                .thenThrow(new org.springframework.dao.QueryTimeoutException("Redis unavailable"));

        // Should NOT throw — fail-open policy
        assertDoesNotThrow(() -> service.validateMessageRate(userId, roomId));
    }

    @Test
    void validateMessageRate_ExceptionMessageContainsSlowMode_IsRethrown() {
        when(valueOps.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(Boolean.FALSE);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.validateMessageRate(userId, roomId));
        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("Slow mode is active"));
    }
}
