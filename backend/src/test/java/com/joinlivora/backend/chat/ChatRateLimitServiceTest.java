package com.joinlivora.backend.chat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChatRateLimitServiceTest {

    private ChatRateLimitService service;
    private final Long userId = 1L;
    private final UUID roomId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ChatRateLimitService();
        service.setSlowModeIntervalSeconds(1); // 1 second for testing
    }

    @Test
    void validateMessageRate_FirstMessage_ShouldSucceed() {
        assertDoesNotThrow(() -> service.validateMessageRate(userId, roomId));
    }

    @Test
    void validateMessageRate_SecondMessageImmediate_ShouldFail() {
        service.validateMessageRate(userId, roomId);
        assertThrows(RuntimeException.class, () -> service.validateMessageRate(userId, roomId));
    }

    @Test
    void validateMessageRate_SecondMessageAfterInterval_ShouldSucceed() throws InterruptedException {
        service.validateMessageRate(userId, roomId);
        Thread.sleep(1100);
        assertDoesNotThrow(() -> service.validateMessageRate(userId, roomId));
    }

    @Test
    void validateMessageRate_DifferentRooms_ShouldBeIndependent() {
        service.validateMessageRate(userId, roomId);
        assertDoesNotThrow(() -> service.validateMessageRate(userId, UUID.randomUUID()));
    }

    @Test
    void validateMessageRate_DifferentUsers_ShouldBeIndependent() {
        service.validateMessageRate(userId, roomId);
        assertDoesNotThrow(() -> service.validateMessageRate(2L, roomId));
    }

    @Test
    void validateMessageRate_DisabledSlowMode_ShouldAlwaysSucceed() {
        service.setSlowModeIntervalSeconds(0);
        service.validateMessageRate(userId, roomId);
        assertDoesNotThrow(() -> service.validateMessageRate(userId, roomId));
    }
}








