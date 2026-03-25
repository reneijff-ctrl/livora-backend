package com.joinlivora.backend.privateshow;

import com.joinlivora.backend.chat.service.ChatRoomService;
import com.joinlivora.backend.presence.entity.CreatorPresence;
import com.joinlivora.backend.presence.service.CreatorPresenceService;
import com.joinlivora.backend.exception.CreatorOfflineException;
import com.joinlivora.backend.exception.InsufficientBalanceException;
import com.joinlivora.backend.exception.ResourceNotFoundException;
import com.joinlivora.backend.creator.repository.CreatorRepository;
import com.joinlivora.backend.payout.CreatorEarningsService;
import com.joinlivora.backend.wallet.*;
import com.joinlivora.backend.token.TokenService;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import com.joinlivora.backend.websocket.RealtimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PrivateSessionServiceTest {

    @Mock
    private PrivateSessionRepository sessionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TokenService tokenService;
    @Mock
    private CreatorEarningsService creatorEarningsService;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private ChatRoomService chatRoomService;
    @Mock
    private CreatorPresenceService creatorPresenceService;
    @Mock
    private CreatorRepository creatorRepository;
    @Mock
    private PrivateSpySessionRepository spySessionRepository;
    @Mock
    private CreatorPrivateSettingsService creatorPrivateSettingsService;

    @InjectMocks
    private PrivateSessionService sessionService;

    private User viewer;
    private User creator;
    private Long viewerId = 1L;
    private Long creatorId = 2L;

    @BeforeEach
    void setUp() {
        viewer = new User();
        viewer.setId(viewerId);
        viewer.setEmail("viewer@test.com");

        creator = new User();
        creator.setId(creatorId);
        creator.setEmail("creator@test.com");
    }

    @Test
    void requestPrivateShow_ShouldSucceed() {
        when(creatorRepository.existsByUser_Id(creatorId)).thenReturn(true);
        when(creatorPrivateSettingsService.getOrCreate(creatorId)).thenReturn(buildSpySettings(true, 25, 5));
        when(userRepository.findById(creatorId)).thenReturn(Optional.of(creator));
        when(creatorPresenceService.getPresence(creatorId)).thenReturn(Optional.of(CreatorPresence.builder().online(true).build()));
        when(tokenService.getBalance(viewer)).thenReturn(UserWallet.builder().balance(100).build());
        when(sessionRepository.save(any())).thenAnswer(invocation -> {
            PrivateSession s = invocation.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        PrivateSessionDto result = sessionService.requestPrivateShow(viewer, creatorId, 50);

        assertNotNull(result);
        assertEquals(PrivateSessionStatus.REQUESTED, result.status());
        assertEquals(50, result.pricePerMinute());
        verify(messagingTemplate).convertAndSendToUser(eq(creator.getId().toString()), eq("/queue/private-show-requests"), any(RealtimeMessage.class));
    }

    @Test
    void requestPrivateShow_InsufficientTokens_ShouldThrowException() {
        when(creatorRepository.existsByUser_Id(creatorId)).thenReturn(true);
        when(creatorPrivateSettingsService.getOrCreate(creatorId)).thenReturn(buildSpySettings(true, 25, 5));
        when(userRepository.findById(creatorId)).thenReturn(Optional.of(creator));
        when(creatorPresenceService.getPresence(creatorId)).thenReturn(Optional.of(CreatorPresence.builder().online(true).build()));
        when(tokenService.getBalance(viewer)).thenReturn(UserWallet.builder().balance(10).build());

        assertThrows(RuntimeException.class, () -> sessionService.requestPrivateShow(viewer, creatorId, 50));
    }

    @Test
    void requestPrivateShow_CreatorOffline_ShouldNotBlock() {
        when(creatorRepository.existsByUser_Id(creatorId)).thenReturn(true);
        when(creatorPrivateSettingsService.getOrCreate(creatorId)).thenReturn(buildSpySettings(true, 25, 5));
        when(userRepository.findById(creatorId)).thenReturn(Optional.of(creator));
        when(creatorPresenceService.getPresence(creatorId)).thenReturn(Optional.of(CreatorPresence.builder().online(false).build()));
        when(tokenService.getBalance(viewer)).thenReturn(UserWallet.builder().balance(100).build());
        when(sessionRepository.save(any())).thenAnswer(invocation -> {
            PrivateSession s = invocation.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        PrivateSessionDto result = sessionService.requestPrivateShow(viewer, creatorId, 50);
        assertNotNull(result);
        assertEquals(PrivateSessionStatus.REQUESTED, result.status());
    }

    @Test
    void acceptRequest_ShouldUpdateStatus() {
        UUID sessionId = UUID.randomUUID();
        PrivateSession session = PrivateSession.builder()
                .id(sessionId)
                .viewer(viewer)
                .creator(creator)
                .status(PrivateSessionStatus.REQUESTED)
                .build();
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

        sessionService.acceptRequest(creator, sessionId);

        assertEquals(PrivateSessionStatus.ACCEPTED, session.getStatus());
        assertNotNull(session.getAcceptedAt());
        verify(messagingTemplate).convertAndSendToUser(eq(viewer.getId().toString()), eq("/queue/private-show-status"), any(RealtimeMessage.class));
    }

    @Test
    void startSession_ShouldBillFirstMinuteAndCreateRoom() {
        UUID sessionId = UUID.randomUUID();
        PrivateSession session = PrivateSession.builder()
                .id(sessionId)
                .viewer(viewer)
                .creator(creator)
                .pricePerMinute(50)
                .status(PrivateSessionStatus.ACCEPTED)
                .build();
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        com.joinlivora.backend.chat.domain.ChatRoom chatRoom = new com.joinlivora.backend.chat.domain.ChatRoom();
        chatRoom.setId(789L);
        when(chatRoomService.createPrivateRoom(anyString(), anyLong())).thenReturn(chatRoom);

        sessionService.startSession(creator, sessionId);

        assertEquals(PrivateSessionStatus.ACTIVE, session.getStatus());
        assertNotNull(session.getStartedAt());
        verify(tokenService).deductTokens(eq(viewer), eq(50L), eq(WalletTransactionType.PRIVATE_SHOW), anyString());
        verify(creatorEarningsService).recordPrivateShowEarning(eq(viewer), eq(creator), eq(50L), eq(sessionId));
        verify(chatRoomService).createPrivateRoom(eq("private-session-" + sessionId), eq(creatorId));
    }

    @Test
    void processBilling_ShouldBillActiveSessions() {
        UUID sessionId = UUID.randomUUID();
        PrivateSession session = PrivateSession.builder()
                .id(sessionId)
                .viewer(viewer)
                .creator(creator)
                .pricePerMinute(50)
                .status(PrivateSessionStatus.ACTIVE)
                .lastBilledAt(Instant.now().minusSeconds(70)) // More than 1 minute ago
                .build();
        when(sessionRepository.findAllByStatus(PrivateSessionStatus.ACTIVE)).thenReturn(java.util.List.of(session));

        sessionService.processBilling();

        verify(tokenService).deductTokens(eq(viewer), eq(50L), eq(WalletTransactionType.PRIVATE_SHOW), anyString());
        assertTrue(session.getLastBilledAt().isAfter(Instant.now().minusSeconds(5)));
    }

    @Test
    void processBilling_InsufficientTokens_ShouldEndSession() {
        UUID sessionId = UUID.randomUUID();
        PrivateSession session = PrivateSession.builder()
                .id(sessionId)
                .viewer(viewer)
                .creator(creator)
                .pricePerMinute(50)
                .status(PrivateSessionStatus.ACTIVE)
                .lastBilledAt(Instant.now().minusSeconds(70))
                .build();
        when(sessionRepository.findAllByStatus(PrivateSessionStatus.ACTIVE)).thenReturn(java.util.List.of(session));
        doThrow(new RuntimeException("Insufficient balance")).when(tokenService).deductTokens(any(), anyLong(), any(), any());

        sessionService.processBilling();

        assertEquals(PrivateSessionStatus.ENDED, session.getStatus());
        assertEquals("Insufficient tokens", session.getEndReason());
    }

    // ===================== SPY JOIN TESTS =====================

    private PrivateSession buildActiveSession() {
        return PrivateSession.builder()
                .id(UUID.randomUUID())
                .viewer(viewer)
                .creator(creator)
                .pricePerMinute(50)
                .status(PrivateSessionStatus.ACTIVE)
                .startedAt(Instant.now())
                .lastBilledAt(Instant.now())
                .build();
    }

    private CreatorPrivateSettings buildSpySettings(boolean allowSpy, long spyPrice, Integer maxViewers) {
        return CreatorPrivateSettings.builder()
                .creatorId(creatorId)
                .enabled(true)
                .pricePerMinute(50L)
                .allowSpyOnPrivate(allowSpy)
                .spyPricePerMinute(spyPrice)
                .maxSpyViewers(maxViewers)
                .build();
    }

    @Test
    void joinAsSpy_ShouldSucceed() {
        PrivateSession session = buildActiveSession();
        User spyUser = new User();
        spyUser.setId(99L);
        spyUser.setEmail("spy@test.com");

        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(creatorPrivateSettingsService.getOrCreate(creatorId)).thenReturn(buildSpySettings(true, 25, 5));
        when(spySessionRepository.existsBySpyViewer_IdAndPrivateSession_IdAndStatus(99L, session.getId(), SpySessionStatus.ACTIVE)).thenReturn(false);
        when(spySessionRepository.countByPrivateSession_IdAndStatus(session.getId(), SpySessionStatus.ACTIVE)).thenReturn(0);
        when(tokenService.getBalance(spyUser)).thenReturn(UserWallet.builder().balance(100).build());
        when(spySessionRepository.save(any())).thenAnswer(inv -> {
            PrivateSpySession s = inv.getArgument(0);
            if (s.getId() == null) s.setId(UUID.randomUUID());
            return s;
        });

        PrivateSpySessionDto result = sessionService.joinAsSpy(spyUser, session.getId());

        assertNotNull(result);
        assertEquals(SpySessionStatus.ACTIVE, result.status());
        assertEquals(25, result.spyPricePerMinute());
        verify(tokenService).deductTokens(eq(spyUser), eq(25L), eq(WalletTransactionType.PRIVATE_SHOW), anyString());
        verify(creatorEarningsService).recordPrivateShowEarning(eq(spyUser), eq(creator), eq(25L), eq(session.getId()));
    }

    @Test
    void joinAsSpy_SessionNotFound_ShouldThrow404() {
        UUID fakeId = UUID.randomUUID();
        when(sessionRepository.findById(fakeId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> sessionService.joinAsSpy(viewer, fakeId));
    }

    @Test
    void joinAsSpy_SessionNotActive_ShouldThrow() {
        PrivateSession session = buildActiveSession();
        session.setStatus(PrivateSessionStatus.ENDED);
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

        assertThrows(IllegalStateException.class, () -> sessionService.joinAsSpy(viewer, session.getId()));
    }

    @Test
    void joinAsSpy_SpyDisabled_ShouldThrow() {
        PrivateSession session = buildActiveSession();
        User spyUser = new User();
        spyUser.setId(99L);
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(creatorPrivateSettingsService.getOrCreate(creatorId)).thenReturn(buildSpySettings(false, 25, 5));

        assertThrows(IllegalStateException.class, () -> sessionService.joinAsSpy(spyUser, session.getId()));
    }

    @Test
    void joinAsSpy_IsCreator_ShouldThrow() {
        PrivateSession session = buildActiveSession();
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

        assertThrows(IllegalStateException.class, () -> sessionService.joinAsSpy(creator, session.getId()));
    }

    @Test
    void joinAsSpy_IsMainViewer_ShouldThrow() {
        PrivateSession session = buildActiveSession();
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

        assertThrows(IllegalStateException.class, () -> sessionService.joinAsSpy(viewer, session.getId()));
    }

    @Test
    void joinAsSpy_AlreadySpying_ShouldThrow() {
        PrivateSession session = buildActiveSession();
        User spyUser = new User();
        spyUser.setId(99L);
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(creatorPrivateSettingsService.getOrCreate(creatorId)).thenReturn(buildSpySettings(true, 25, 5));
        when(spySessionRepository.countByPrivateSession_IdAndStatus(session.getId(), SpySessionStatus.ACTIVE)).thenReturn(0);
        when(spySessionRepository.existsBySpyViewer_IdAndPrivateSession_IdAndStatus(99L, session.getId(), SpySessionStatus.ACTIVE)).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> sessionService.joinAsSpy(spyUser, session.getId()));
    }

    @Test
    void joinAsSpy_MaxSpiesReached_ShouldThrow() {
        PrivateSession session = buildActiveSession();
        User spyUser = new User();
        spyUser.setId(99L);
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(creatorPrivateSettingsService.getOrCreate(creatorId)).thenReturn(buildSpySettings(true, 25, 2));
        when(spySessionRepository.countByPrivateSession_IdAndStatus(session.getId(), SpySessionStatus.ACTIVE)).thenReturn(2);

        assertThrows(IllegalStateException.class, () -> sessionService.joinAsSpy(spyUser, session.getId()));
    }

    @Test
    void joinAsSpy_InsufficientTokens_ShouldThrow() {
        PrivateSession session = buildActiveSession();
        User spyUser = new User();
        spyUser.setId(99L);
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(creatorPrivateSettingsService.getOrCreate(creatorId)).thenReturn(buildSpySettings(true, 25, 5));
        when(spySessionRepository.countByPrivateSession_IdAndStatus(session.getId(), SpySessionStatus.ACTIVE)).thenReturn(0);
        when(spySessionRepository.existsBySpyViewer_IdAndPrivateSession_IdAndStatus(99L, session.getId(), SpySessionStatus.ACTIVE)).thenReturn(false);
        when(tokenService.getBalance(spyUser)).thenReturn(UserWallet.builder().balance(10).build());

        assertThrows(InsufficientBalanceException.class, () -> sessionService.joinAsSpy(spyUser, session.getId()));
    }
}








