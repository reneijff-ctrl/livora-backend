package com.joinlivora.backend.monetization;

import com.joinlivora.backend.chat.ChatModerationService;
import com.joinlivora.backend.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TipValidationServiceTest {

    @Mock
    private ChatModerationService moderationService;

    @InjectMocks
    private TipValidationService tipValidationService;

    private User user;
    private UUID roomId;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setEmail("test@test.com");
        roomId = UUID.randomUUID();

        ReflectionTestUtils.setField(tipValidationService, "minTipTokens", 1L);
        ReflectionTestUtils.setField(tipValidationService, "maxTipsPerMinute", 5);
        ReflectionTestUtils.setField(tipValidationService, "maxSuperTipsPerMinute", 2);
        ReflectionTestUtils.setField(tipValidationService, "roomCooldownSeconds", 10);
        ReflectionTestUtils.setField(tipValidationService, "maxHighlightsPerMinute", 5);
    }

    @Test
    void validateTokenTip_WhenAmountTooLow_ShouldThrowException() {
        assertThrows(IllegalArgumentException.class, () -> 
            tipValidationService.validateTokenTip(user, 0, roomId)
        );
    }

    @Test
    void validateTokenTip_WhenUserMuted_ShouldThrowException() {
        when(moderationService.isMuted(eq(user.getId()), any())).thenReturn(true);

        assertThrows(AccessDeniedException.class, () -> 
            tipValidationService.validateTokenTip(user, 10, roomId)
        );
    }

    @Test
    void validateTokenTip_WhenUserBanned_ShouldThrowException() {
        when(moderationService.isMuted(eq(user.getId()), any())).thenReturn(false);
        when(moderationService.isBanned(eq(user.getId()), any())).thenReturn(true);

        assertThrows(AccessDeniedException.class, () -> 
            tipValidationService.validateTokenTip(user, 10, roomId)
        );
    }

    @Test
    void checkRateLimit_WhenExceeded_ShouldReturnFalse() {
        // Max is 5 per minute
        for (int i = 0; i < 5; i++) {
            assertTrue(tipValidationService.checkRateLimit(user));
        }

        assertFalse(tipValidationService.checkRateLimit(user));
    }

    @Test
    void validateStripeTip_WhenAmountTooLow_ShouldThrowException() {
        assertThrows(IllegalArgumentException.class, () -> 
            tipValidationService.validateStripeTip(user, new BigDecimal("0.50"))
        );
    }

    @Test
    void validateSuperTip_WhenRateLimitExceeded_ShouldThrowException() {
        when(moderationService.isMuted(eq(user.getId()), any())).thenReturn(false);
        when(moderationService.isBanned(eq(user.getId()), any())).thenReturn(false);

        tipValidationService.validateSuperTip(user, UUID.randomUUID());
        tipValidationService.validateSuperTip(user, UUID.randomUUID());

        assertThrows(RuntimeException.class, () -> 
            tipValidationService.validateSuperTip(user, UUID.randomUUID())
        );
    }

    @Test
    void validateSuperTip_WhenRoomCooldownActive_ShouldThrowException() {
        when(moderationService.isMuted(anyLong(), any())).thenReturn(false);
        when(moderationService.isBanned(anyLong(), any())).thenReturn(false);

        tipValidationService.validateSuperTip(user, roomId);

        User user2 = new User();
        user2.setId(2L);
        user2.setEmail("user2@test.com");

        assertThrows(RuntimeException.class, () -> 
            tipValidationService.validateSuperTip(user2, roomId)
        );
    }

    @Test
    void validateHighlight_WhenRateLimitExceeded_ShouldThrowException() {
        when(moderationService.isMuted(eq(user.getId()), any())).thenReturn(false);
        when(moderationService.isBanned(eq(user.getId()), any())).thenReturn(false);

        // Max is 5 per minute
        for (int i = 0; i < 5; i++) {
            tipValidationService.validateHighlight(user, roomId);
        }

        assertThrows(RuntimeException.class, () -> 
            tipValidationService.validateHighlight(user, roomId)
        );
    }
}









