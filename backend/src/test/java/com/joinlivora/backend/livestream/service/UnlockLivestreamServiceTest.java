package com.joinlivora.backend.livestream.service;

import com.joinlivora.backend.exception.InsufficientBalanceException;
import com.joinlivora.backend.livestream.dto.UnlockResponse;
import com.joinlivora.backend.streaming.Stream;
import com.joinlivora.backend.streaming.StreamRepository;
import com.joinlivora.backend.streaming.service.LiveAccessService;
import com.joinlivora.backend.streaming.service.LivestreamAccessService;
import com.joinlivora.backend.token.TokenWalletService;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import com.joinlivora.backend.wallet.WalletTransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class UnlockLivestreamServiceTest {

    private UserRepository userRepository;
    private TokenWalletService tokenWalletService;
    private StreamRepository streamRepository;
    private LivestreamAccessService accessService;
    private LiveAccessService liveAccessService;
    private UnlockLivestreamService unlockService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        tokenWalletService = mock(TokenWalletService.class);
        streamRepository = mock(StreamRepository.class);
        accessService = mock(LivestreamAccessService.class);
        liveAccessService = mock(LiveAccessService.class);
        unlockService = new UnlockLivestreamService(
                userRepository,
                tokenWalletService,
                streamRepository,
                accessService,
                liveAccessService
        );
    }

    @Test
    void unlockStream_Success_DelegatesToTokenWalletService() {
        Long creatorUserId = 10L;
        Long viewerUserId = 20L;
        UUID streamId = UUID.randomUUID();
        User viewer = new User();
        viewer.setId(viewerUserId);
        User creator = new User();
        creator.setId(creatorUserId);

        Stream stream = Stream.builder()
                .id(streamId)
                .creator(creator)
                .isLive(true)
                .isPaid(true)
                .admissionPrice(new BigDecimal("15"))
                .build();

        when(streamRepository.findAllByCreatorIdAndIsLiveTrueOrderByStartedAtDesc(creatorUserId))
                .thenReturn(List.of(stream));
        when(accessService.hasAccess(eq(streamId), eq(viewerUserId))).thenReturn(false);
        when(userRepository.findByIdForUpdate(viewerUserId)).thenReturn(Optional.of(viewer));
        when(tokenWalletService.getTotalBalance(viewerUserId)).thenReturn(85L);

        UnlockResponse response = unlockService.unlockStream(creatorUserId, viewerUserId);

        assertTrue(response.isSuccess());
        assertEquals(85L, response.getRemainingTokens());

        verify(tokenWalletService).deductTokens(eq(viewerUserId), eq(15L), eq(WalletTransactionType.LIVESTREAM_ADMISSION), eq(creatorUserId.toString()));
        verify(accessService).grantAccess(eq(streamId), eq(viewerUserId), any(Duration.class));
        verify(liveAccessService).grantAccess(eq(creatorUserId), eq(viewerUserId), any(Duration.class));
    }

    @Test
    void unlockStream_AlreadyHasAccess_IdempotentSuccess() {
        Long creatorUserId = 10L;
        Long viewerUserId = 20L;
        UUID streamId = UUID.randomUUID();
        User viewer = new User();
        viewer.setId(viewerUserId);
        User creator = new User();
        creator.setId(creatorUserId);

        Stream stream = Stream.builder()
                .id(streamId)
                .creator(creator)
                .isLive(true)
                .isPaid(true)
                .admissionPrice(new BigDecimal("15"))
                .build();

        when(streamRepository.findAllByCreatorIdAndIsLiveTrueOrderByStartedAtDesc(creatorUserId))
                .thenReturn(List.of(stream));
        when(userRepository.findByIdForUpdate(viewerUserId)).thenReturn(Optional.of(viewer));
        when(accessService.hasAccess(eq(streamId), eq(viewerUserId))).thenReturn(true);
        when(tokenWalletService.getTotalBalance(viewerUserId)).thenReturn(100L);

        UnlockResponse response = unlockService.unlockStream(creatorUserId, viewerUserId);

        assertTrue(response.isSuccess());
        assertEquals(100L, response.getRemainingTokens());

        verify(tokenWalletService, never()).deductTokens(anyLong(), anyLong(), any(), anyString());
        verify(accessService, never()).grantAccess(any(UUID.class), anyLong(), any());
        verify(liveAccessService, never()).grantAccess(anyLong(), anyLong(), any());
    }

    @Test
    void unlockStream_NotLive_ThrowsException() {
        Long creatorUserId = 10L;
        Long viewerUserId = 20L;

        when(streamRepository.findAllByCreatorIdAndIsLiveTrueOrderByStartedAtDesc(creatorUserId))
                .thenReturn(List.of());

        assertThrows(IllegalStateException.class, () ->
                unlockService.unlockStream(creatorUserId, viewerUserId));
    }

    @Test
    void unlockStream_InsufficientBalance_ThrowsFromTokenWalletService() {
        Long creatorUserId = 10L;
        Long viewerUserId = 20L;
        UUID streamId = UUID.randomUUID();
        User viewer = new User();
        viewer.setId(viewerUserId);
        User creator = new User();
        creator.setId(creatorUserId);

        Stream stream = Stream.builder()
                .id(streamId)
                .creator(creator)
                .isLive(true)
                .isPaid(true)
                .admissionPrice(new BigDecimal("15"))
                .build();

        when(streamRepository.findAllByCreatorIdAndIsLiveTrueOrderByStartedAtDesc(creatorUserId))
                .thenReturn(List.of(stream));
        when(userRepository.findByIdForUpdate(viewerUserId)).thenReturn(Optional.of(viewer));
        when(accessService.hasAccess(eq(streamId), eq(viewerUserId))).thenReturn(false);
        doThrow(new InsufficientBalanceException("Insufficient token balance"))
                .when(tokenWalletService).deductTokens(eq(viewerUserId), eq(15L), eq(WalletTransactionType.LIVESTREAM_ADMISSION), eq(creatorUserId.toString()));

        assertThrows(InsufficientBalanceException.class, () ->
                unlockService.unlockStream(creatorUserId, viewerUserId));

        verify(accessService, never()).grantAccess(any(UUID.class), anyLong(), any());
        verify(liveAccessService, never()).grantAccess(anyLong(), anyLong(), any());
    }

    @Test
    void unlockStream_FreeStream_NoDeduction() {
        Long creatorUserId = 10L;
        Long viewerUserId = 20L;
        UUID streamId = UUID.randomUUID();

        User creator = new User();
        creator.setId(creatorUserId);

        Stream stream = Stream.builder()
                .id(streamId)
                .creator(creator)
                .isLive(true)
                .isPaid(false)
                .build();

        when(streamRepository.findAllByCreatorIdAndIsLiveTrueOrderByStartedAtDesc(creatorUserId))
                .thenReturn(List.of(stream));
        when(tokenWalletService.getTotalBalance(viewerUserId)).thenReturn(100L);

        UnlockResponse response = unlockService.unlockStream(creatorUserId, viewerUserId);

        assertTrue(response.isSuccess());
        assertEquals(100L, response.getRemainingTokens());

        verify(tokenWalletService, never()).deductTokens(anyLong(), anyLong(), any(), anyString());
        verify(accessService).grantAccess(eq(streamId), eq(viewerUserId), any(Duration.class));
    }

    @Test
    void unlockStream_RedisFailure_ThrowsAfterRetries_TriggeringRollback() {
        Long creatorUserId = 10L;
        Long viewerUserId = 20L;
        UUID streamId = UUID.randomUUID();
        User viewer = new User();
        viewer.setId(viewerUserId);
        User creator = new User();
        creator.setId(creatorUserId);

        Stream stream = Stream.builder()
                .id(streamId)
                .creator(creator)
                .isLive(true)
                .isPaid(true)
                .admissionPrice(new BigDecimal("10"))
                .build();

        when(streamRepository.findAllByCreatorIdAndIsLiveTrueOrderByStartedAtDesc(creatorUserId))
                .thenReturn(List.of(stream));
        when(userRepository.findByIdForUpdate(viewerUserId)).thenReturn(Optional.of(viewer));
        when(accessService.hasAccess(eq(streamId), eq(viewerUserId))).thenReturn(false);
        // Simulate Redis being unavailable on every attempt
        doThrow(new QueryTimeoutException("Redis timeout"))
                .when(accessService).grantAccess(any(UUID.class), anyLong(), any(Duration.class));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                unlockService.unlockStream(creatorUserId, viewerUserId));

        assertTrue(ex.getMessage().contains("CRITICAL"),
                "Exception message must contain CRITICAL to signal rollback requirement");

        // Token deduction was called once before the failed Redis write
        verify(tokenWalletService).deductTokens(eq(viewerUserId), eq(10L),
                eq(WalletTransactionType.LIVESTREAM_ADMISSION), eq(creatorUserId.toString()));

        // All 3 retry attempts were made against Redis
        verify(accessService, times(3)).grantAccess(eq(streamId), eq(viewerUserId), any(Duration.class));

        // DB access grant must NOT have been called — it comes after Redis in the flow
        verify(liveAccessService, never()).grantAccess(anyLong(), anyLong(), any());
    }
}
