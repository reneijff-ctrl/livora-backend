package com.joinlivora.backend.abuse;

import com.joinlivora.backend.abuse.model.RestrictionLevel;
import com.joinlivora.backend.abuse.model.UserRestriction;
import com.joinlivora.backend.abuse.repository.UserRestrictionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RestrictionServiceTest {

    @Mock
    private UserRestrictionRepository userRestrictionRepository;

    @InjectMocks
    private RestrictionService restrictionService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
    }

    @Test
    void applyRestriction_Score35_ShouldApplySlowMode() {
        when(userRestrictionRepository.findActiveByUserId(eq(userId), any(Instant.class)))
                .thenReturn(Optional.empty());

        restrictionService.applyRestriction(userId, 35, "Spamming");

        ArgumentCaptor<UserRestriction> captor = ArgumentCaptor.forClass(UserRestriction.class);
        verify(userRestrictionRepository).save(captor.capture());
        UserRestriction saved = captor.getValue();

        assertEquals(userId, saved.getUserId());
        assertEquals(RestrictionLevel.SLOW_MODE, saved.getRestrictionLevel());
        assertTrue(saved.getReason().contains("Spamming"));
        assertTrue(saved.getReason().contains("Score: 35"));
        assertNotNull(saved.getExpiresAt());
    }

    @Test
    void applyRestriction_Score105_ShouldApplyTempSuspension() {
        when(userRestrictionRepository.findActiveByUserId(eq(userId), any(Instant.class)))
                .thenReturn(Optional.empty());

        restrictionService.applyRestriction(userId, 105, "Severe abuse");

        ArgumentCaptor<UserRestriction> captor = ArgumentCaptor.forClass(UserRestriction.class);
        verify(userRestrictionRepository).save(captor.capture());
        UserRestriction saved = captor.getValue();

        assertEquals(RestrictionLevel.TEMP_SUSPENSION, saved.getRestrictionLevel());
    }

    @Test
    void applyRestriction_Score25_ShouldNotApply() {
        restrictionService.applyRestriction(userId, 25, "Minor issue");

        verify(userRestrictionRepository, never()).save(any());
    }

    @Test
    void applyRestriction_ExistingSlowMode_Score75_ShouldEscalateToTipLimit() {
        UserRestriction existing = UserRestriction.builder()
                .userId(userId)
                .restrictionLevel(RestrictionLevel.SLOW_MODE)
                .build();
        when(userRestrictionRepository.findActiveByUserId(eq(userId), any(Instant.class)))
                .thenReturn(Optional.of(existing));

        restrictionService.applyRestriction(userId, 75, "High frequency tips");

        verify(userRestrictionRepository).save(existing);
        assertEquals(RestrictionLevel.TIP_LIMIT, existing.getRestrictionLevel());
        assertTrue(existing.getReason().contains("Escalated from SLOW_MODE"));
    }

    @Test
    void applyRestriction_Score95_ShouldApplyFraudLock() {
        when(userRestrictionRepository.findActiveByUserId(eq(userId), any(Instant.class)))
                .thenReturn(Optional.empty());

        restrictionService.applyRestriction(userId, 95, "Critical risk");

        ArgumentCaptor<UserRestriction> captor = ArgumentCaptor.forClass(UserRestriction.class);
        verify(userRestrictionRepository).save(captor.capture());
        assertEquals(RestrictionLevel.FRAUD_LOCK, captor.getValue().getRestrictionLevel());
    }

    @Test
    void applyRestriction_ExistingChatMute_Score35_ShouldNotDowngrade() {
        UserRestriction existing = UserRestriction.builder()
                .userId(userId)
                .restrictionLevel(RestrictionLevel.CHAT_MUTE)
                .build();
        when(userRestrictionRepository.findActiveByUserId(eq(userId), any(Instant.class)))
                .thenReturn(Optional.of(existing));

        restrictionService.applyRestriction(userId, 35, "Minor thing");

        verify(userRestrictionRepository, never()).save(any());
        assertEquals(RestrictionLevel.CHAT_MUTE, existing.getRestrictionLevel());
    }

    @Test
    void applyRestriction_ExistingSlowMode_Score35_ShouldNotChange() {
        UserRestriction existing = UserRestriction.builder()
                .userId(userId)
                .restrictionLevel(RestrictionLevel.SLOW_MODE)
                .build();
        when(userRestrictionRepository.findActiveByUserId(eq(userId), any(Instant.class)))
                .thenReturn(Optional.of(existing));

        restrictionService.applyRestriction(userId, 35, "Same thing");

        verify(userRestrictionRepository, never()).save(any());
    }

    @Test
    void autoExpireRestrictions_ShouldCallRepository() {
        restrictionService.autoExpireRestrictions();
        verify(userRestrictionRepository).deleteByExpiresAtBefore(any(Instant.class));
    }

    @Test
    void validateTippingAccess_WithTipCooldown_ShouldThrowException() {
        Instant expiresAt = Instant.now().plusSeconds(300);
        UserRestriction restriction = UserRestriction.builder()
                .userId(userId)
                .restrictionLevel(RestrictionLevel.TIP_COOLDOWN)
                .expiresAt(expiresAt)
                .build();
        when(userRestrictionRepository.findActiveByUserId(eq(userId), any(Instant.class)))
                .thenReturn(Optional.of(restriction));

        com.joinlivora.backend.exception.UserRestrictedException ex = assertThrows(com.joinlivora.backend.exception.UserRestrictedException.class, () ->
                restrictionService.validateTippingAccess(userId)
        );

        assertEquals(RestrictionLevel.TIP_COOLDOWN, ex.getLevel());
        assertEquals(expiresAt, ex.getExpiresAt());
    }

    @Test
    void validateTippingAccess_WithTipLimit_Exceeding_ShouldThrow() {
        UserRestriction restriction = UserRestriction.builder()
                .userId(userId)
                .restrictionLevel(RestrictionLevel.TIP_LIMIT)
                .build();
        when(userRestrictionRepository.findActiveByUserId(eq(userId), any(Instant.class)))
                .thenReturn(Optional.of(restriction));

        assertThrows(com.joinlivora.backend.exception.UserRestrictedException.class, () ->
                restrictionService.validateTippingAccess(userId, RestrictionService.DEFAULT_TIP_LIMIT.add(BigDecimal.ONE))
        );
    }

    @Test
    void validateTippingAccess_WithTipLimit_Under_ShouldNotThrow() {
        UserRestriction restriction = UserRestriction.builder()
                .userId(userId)
                .restrictionLevel(RestrictionLevel.TIP_LIMIT)
                .build();
        when(userRestrictionRepository.findActiveByUserId(eq(userId), any(Instant.class)))
                .thenReturn(Optional.of(restriction));

        assertDoesNotThrow(() ->
                restrictionService.validateTippingAccess(userId, RestrictionService.DEFAULT_TIP_LIMIT)
        );
    }

    @Test
    void validateTippingAccess_WithFraudLock_ShouldThrow() {
        UserRestriction restriction = UserRestriction.builder()
                .userId(userId)
                .restrictionLevel(RestrictionLevel.FRAUD_LOCK)
                .build();
        when(userRestrictionRepository.findActiveByUserId(eq(userId), any(Instant.class)))
                .thenReturn(Optional.of(restriction));

        assertThrows(com.joinlivora.backend.exception.UserRestrictedException.class, () ->
                restrictionService.validateTippingAccess(userId, BigDecimal.ONE)
        );
    }

    @Test
    void validateTippingAccess_WithNoRestriction_ShouldNotThrow() {
        when(userRestrictionRepository.findActiveByUserId(eq(userId), any(Instant.class)))
                .thenReturn(Optional.empty());

        assertDoesNotThrow(() -> restrictionService.validateTippingAccess(userId));
    }

    @Test
    void validateTippingAccess_WithSlowMode_ShouldNotThrow() {
        UserRestriction restriction = UserRestriction.builder()
                .userId(userId)
                .restrictionLevel(RestrictionLevel.SLOW_MODE)
                .build();
        when(userRestrictionRepository.findActiveByUserId(eq(userId), any(Instant.class)))
                .thenReturn(Optional.of(restriction));

        assertDoesNotThrow(() -> restrictionService.validateTippingAccess(userId));
    }
}








