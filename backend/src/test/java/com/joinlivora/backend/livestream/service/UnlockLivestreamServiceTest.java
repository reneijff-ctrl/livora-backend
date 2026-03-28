package com.joinlivora.backend.livestream.service;

import com.joinlivora.backend.exception.InsufficientBalanceException;
import com.joinlivora.backend.livestream.dto.UnlockResponse;
import com.joinlivora.backend.streaming.service.LiveAccessService;
import com.joinlivora.backend.streaming.service.LivestreamAccessService;
import com.joinlivora.backend.token.TokenWalletService;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import com.joinlivora.backend.wallet.WalletTransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class UnlockLivestreamServiceTest {

    private UserRepository userRepository;
    private TokenWalletService tokenWalletService;
    private com.joinlivora.backend.livestream.repository.LivestreamSessionRepository sessionRepository;
    private LivestreamAccessService accessService;
    private LiveAccessService liveAccessService;
    private UnlockLivestreamService unlockService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        tokenWalletService = mock(TokenWalletService.class);
        sessionRepository = mock(com.joinlivora.backend.livestream.repository.LivestreamSessionRepository.class);
        accessService = mock(LivestreamAccessService.class);
        liveAccessService = mock(LiveAccessService.class);
        unlockService = new UnlockLivestreamService(
                userRepository,
                tokenWalletService,
                sessionRepository,
                accessService,
                liveAccessService
        );
    }

    @Test
    void unlockStream_Success_DelegatesToTokenWalletService() {
        Long creatorUserId = 10L;
        Long viewerUserId = 20L;
        User viewer = new User();
        viewer.setId(viewerUserId);
        User creator = new User();
        creator.setId(creatorUserId);

        com.joinlivora.backend.livestream.domain.LivestreamSession session = com.joinlivora.backend.livestream.domain.LivestreamSession.builder()
                .id(300L)
                .creator(creator)
                .status(com.joinlivora.backend.livestream.domain.LivestreamStatus.LIVE)
                .isPaid(true)
                .admissionPrice(new BigDecimal("15"))
                .build();

        when(sessionRepository.findTopByCreator_IdAndStatusOrderByStartedAtDesc(eq(creatorUserId), eq(com.joinlivora.backend.livestream.domain.LivestreamStatus.LIVE)))
                .thenReturn(Optional.of(session));
        when(accessService.hasAccess(eq(300L), eq(viewerUserId))).thenReturn(false);
        when(userRepository.findByIdForUpdate(viewerUserId)).thenReturn(Optional.of(viewer));
        when(tokenWalletService.getTotalBalance(viewerUserId)).thenReturn(85L);

        UnlockResponse response = unlockService.unlockStream(creatorUserId, viewerUserId);

        assertTrue(response.isSuccess());
        assertEquals(85L, response.getRemainingTokens());

        // Verify deduction goes through TokenWalletService (with pessimistic locking)
        verify(tokenWalletService).deductTokens(eq(viewerUserId), eq(15L), eq(WalletTransactionType.LIVESTREAM_ADMISSION), eq(creatorUserId.toString()));
        verify(accessService).grantAccess(eq(300L), eq(viewerUserId), any(Duration.class));
        verify(liveAccessService).grantAccess(eq(creatorUserId), eq(viewerUserId), any(Duration.class));
    }

    @Test
    void unlockStream_AlreadyHasAccess_IdempotentSuccess() {
        Long creatorUserId = 10L;
        Long viewerUserId = 20L;
        User viewer = new User();
        viewer.setId(viewerUserId);
        User creator = new User();
        creator.setId(creatorUserId);

        com.joinlivora.backend.livestream.domain.LivestreamSession session = com.joinlivora.backend.livestream.domain.LivestreamSession.builder()
                .id(300L)
                .creator(creator)
                .status(com.joinlivora.backend.livestream.domain.LivestreamStatus.LIVE)
                .isPaid(true)
                .admissionPrice(new BigDecimal("15"))
                .build();

        when(sessionRepository.findTopByCreator_IdAndStatusOrderByStartedAtDesc(eq(creatorUserId), eq(com.joinlivora.backend.livestream.domain.LivestreamStatus.LIVE)))
                .thenReturn(Optional.of(session));
        when(userRepository.findByIdForUpdate(viewerUserId)).thenReturn(Optional.of(viewer));
        when(accessService.hasAccess(eq(300L), eq(viewerUserId))).thenReturn(true);
        when(tokenWalletService.getTotalBalance(viewerUserId)).thenReturn(100L);

        UnlockResponse response = unlockService.unlockStream(creatorUserId, viewerUserId);

        assertTrue(response.isSuccess());
        assertEquals(100L, response.getRemainingTokens());

        // No deduction should happen
        verify(tokenWalletService, never()).deductTokens(anyLong(), anyLong(), any(), anyString());
        verify(accessService, never()).grantAccess(anyLong(), anyLong(), any());
        verify(liveAccessService, never()).grantAccess(anyLong(), anyLong(), any());
    }

    @Test
    void unlockStream_NotLive_ThrowsException() {
        Long creatorUserId = 10L;
        Long viewerUserId = 20L;

        when(sessionRepository.findTopByCreator_IdAndStatusOrderByStartedAtDesc(eq(creatorUserId), eq(com.joinlivora.backend.livestream.domain.LivestreamStatus.LIVE)))
                .thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () ->
                unlockService.unlockStream(creatorUserId, viewerUserId));
    }

    @Test
    void unlockStream_InsufficientBalance_ThrowsFromTokenWalletService() {
        Long creatorUserId = 10L;
        Long viewerUserId = 20L;
        User viewer = new User();
        viewer.setId(viewerUserId);
        User creator = new User();
        creator.setId(creatorUserId);

        com.joinlivora.backend.livestream.domain.LivestreamSession session = com.joinlivora.backend.livestream.domain.LivestreamSession.builder()
                .id(400L)
                .creator(creator)
                .status(com.joinlivora.backend.livestream.domain.LivestreamStatus.LIVE)
                .isPaid(true)
                .admissionPrice(new BigDecimal("15"))
                .build();

        when(sessionRepository.findTopByCreator_IdAndStatusOrderByStartedAtDesc(eq(creatorUserId), eq(com.joinlivora.backend.livestream.domain.LivestreamStatus.LIVE)))
                .thenReturn(Optional.of(session));
        when(userRepository.findByIdForUpdate(viewerUserId)).thenReturn(Optional.of(viewer));
        when(accessService.hasAccess(eq(400L), eq(viewerUserId))).thenReturn(false);
        // TokenWalletService throws InsufficientBalanceException when balance is too low
        doThrow(new InsufficientBalanceException("Insufficient token balance"))
                .when(tokenWalletService).deductTokens(eq(viewerUserId), eq(15L), eq(WalletTransactionType.LIVESTREAM_ADMISSION), eq(creatorUserId.toString()));

        assertThrows(InsufficientBalanceException.class, () ->
                unlockService.unlockStream(creatorUserId, viewerUserId));

        // Access should NOT be granted on failure
        verify(accessService, never()).grantAccess(anyLong(), anyLong(), any());
        verify(liveAccessService, never()).grantAccess(anyLong(), anyLong(), any());
    }

    @Test
    void unlockStream_FreeStream_NoDeduction() {
        Long creatorUserId = 10L;
        Long viewerUserId = 20L;

        User creator = new User();
        creator.setId(creatorUserId);

        com.joinlivora.backend.livestream.domain.LivestreamSession session = com.joinlivora.backend.livestream.domain.LivestreamSession.builder()
                .id(500L)
                .creator(creator)
                .status(com.joinlivora.backend.livestream.domain.LivestreamStatus.LIVE)
                .isPaid(false)
                .build();

        when(sessionRepository.findTopByCreator_IdAndStatusOrderByStartedAtDesc(eq(creatorUserId), eq(com.joinlivora.backend.livestream.domain.LivestreamStatus.LIVE)))
                .thenReturn(Optional.of(session));
        when(tokenWalletService.getTotalBalance(viewerUserId)).thenReturn(100L);

        UnlockResponse response = unlockService.unlockStream(creatorUserId, viewerUserId);

        assertTrue(response.isSuccess());
        assertEquals(100L, response.getRemainingTokens());

        // No deduction for free streams
        verify(tokenWalletService, never()).deductTokens(anyLong(), anyLong(), any(), anyString());
        verify(accessService).grantAccess(eq(500L), eq(viewerUserId), any(Duration.class));
    }
}
