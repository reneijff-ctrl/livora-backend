package com.joinlivora.backend.livestream.service;

import com.joinlivora.backend.exception.InsufficientBalanceException;
import com.joinlivora.backend.livestream.dto.UnlockResponse;
import com.joinlivora.backend.streaming.service.LiveAccessService;
import com.joinlivora.backend.streaming.service.LivestreamAccessService;
import com.joinlivora.backend.token.TokenTransaction;
import com.joinlivora.backend.token.TokenTransactionRepository;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import com.joinlivora.backend.wallet.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class UnlockLivestreamServiceTest {

    private UserRepository userRepository;
    private UserWalletRepository walletRepository;
    private TokenTransactionRepository tokenTransactionRepository;
    private com.joinlivora.backend.livestream.repository.LivestreamSessionRepository sessionRepository;
    private LivestreamAccessService accessService;
    private LiveAccessService liveAccessService;
    private UnlockLivestreamService unlockService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        walletRepository = mock(UserWalletRepository.class);
        tokenTransactionRepository = mock(TokenTransactionRepository.class);
        sessionRepository = mock(com.joinlivora.backend.livestream.repository.LivestreamSessionRepository.class);
        accessService = mock(LivestreamAccessService.class);
        liveAccessService = mock(LiveAccessService.class);
        unlockService = new UnlockLivestreamService(
                userRepository,
                walletRepository,
                tokenTransactionRepository,
                sessionRepository,
                accessService,
                liveAccessService
        );
    }

    @Test
    void unlockStream_Success() {
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

        UserWallet wallet = UserWallet.builder()
                .userId(viewer)
                .balance(100L)
                .build();

        when(sessionRepository.findTopByCreator_IdAndStatusOrderByStartedAtDesc(eq(creatorUserId), eq(com.joinlivora.backend.livestream.domain.LivestreamStatus.LIVE)))
                .thenReturn(Optional.of(session));
        when(accessService.hasAccess(eq(300L), eq(viewerUserId))).thenReturn(false);
        when(userRepository.findByIdForUpdate(viewerUserId)).thenReturn(Optional.of(viewer));
        when(walletRepository.findByUserIdWithLock(viewer)).thenReturn(Optional.of(wallet));

        UnlockResponse response = unlockService.unlockStream(creatorUserId, viewerUserId);

        assertTrue(response.isSuccess());
        assertEquals(85L, response.getRemainingTokens());
        assertEquals(85L, wallet.getBalance());

        verify(walletRepository).save(wallet);
        verify(tokenTransactionRepository).save(any(TokenTransaction.class));
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

        UserWallet wallet = UserWallet.builder()
                .userId(viewer)
                .balance(100L)
                .build();

        when(sessionRepository.findTopByCreator_IdAndStatusOrderByStartedAtDesc(eq(creatorUserId), eq(com.joinlivora.backend.livestream.domain.LivestreamStatus.LIVE)))
                .thenReturn(Optional.of(session));
        when(userRepository.findByIdForUpdate(viewerUserId)).thenReturn(Optional.of(viewer));
        when(accessService.hasAccess(eq(300L), eq(viewerUserId))).thenReturn(true);
        when(userRepository.findById(viewerUserId)).thenReturn(Optional.of(viewer));
        when(walletRepository.findByUserId(viewer)).thenReturn(Optional.of(wallet));

        UnlockResponse response = unlockService.unlockStream(creatorUserId, viewerUserId);

        assertTrue(response.isSuccess());
        assertEquals(100L, response.getRemainingTokens());

        verify(walletRepository, never()).save(any());
        verify(tokenTransactionRepository, never()).save(any());
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
    void unlockStream_InsufficientBalance_ThrowsException() {
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

        UserWallet wallet = UserWallet.builder()
                .userId(viewer)
                .balance(10L)
                .build();

        when(sessionRepository.findTopByCreator_IdAndStatusOrderByStartedAtDesc(eq(creatorUserId), eq(com.joinlivora.backend.livestream.domain.LivestreamStatus.LIVE)))
                .thenReturn(Optional.of(session));
        when(userRepository.findByIdForUpdate(viewerUserId)).thenReturn(Optional.of(viewer));
        when(walletRepository.findByUserIdWithLock(viewer)).thenReturn(Optional.of(wallet));

        assertThrows(InsufficientBalanceException.class, () -> 
                unlockService.unlockStream(creatorUserId, viewerUserId));
    }
}








