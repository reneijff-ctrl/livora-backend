package com.joinlivora.backend.streaming.service;

import com.joinlivora.backend.livestream.domain.LivestreamSession;
import com.joinlivora.backend.livestream.domain.LivestreamStatus;
import com.joinlivora.backend.livestream.repository.LivestreamSessionRepository;
import com.joinlivora.backend.privateshow.*;
import com.joinlivora.backend.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LivestreamAccessServiceTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private LivestreamSessionRepository sessionRepository;
    @Mock private PrivateSessionRepository privateSessionRepository;
    @Mock private PrivateSpySessionRepository privateSpySessionRepository;

    @InjectMocks private LivestreamAccessService service;

    private User creator;
    private User viewer;
    private User unauthorizedViewer;
    private LivestreamSession session;

    @BeforeEach
    void setUp() {
        creator = new User(); creator.setId(1L);
        viewer = new User(); viewer.setId(2L);
        unauthorizedViewer = new User(); unauthorizedViewer.setId(99L);

        session = LivestreamSession.builder()
                .id(100L)
                .creator(creator)
                .status(LivestreamStatus.LIVE)
                .isPaid(false)
                .build();
    }

    @Test
    void noActivePrivateSession_FreeStream_ShouldAllowAnyone() {
        when(sessionRepository.findById(100L)).thenReturn(Optional.of(session));
        when(privateSessionRepository.findFirstByCreator_IdAndStatusOrderByStartedAtDesc(1L, PrivateSessionStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertTrue(service.hasAccess(100L, 99L));
    }

    @Test
    void creatorAlwaysAllowed_DuringActivePrivate() {
        PrivateSession ps = PrivateSession.builder().id(UUID.randomUUID()).viewer(viewer).creator(creator).status(PrivateSessionStatus.ACTIVE).build();
        when(sessionRepository.findById(100L)).thenReturn(Optional.of(session));
        // Creator check happens before private session check
        assertTrue(service.hasAccess(100L, 1L));
    }

    @Test
    void assignedPrivateViewer_AllowedDuringActivePrivate() {
        PrivateSession ps = PrivateSession.builder().id(UUID.randomUUID()).viewer(viewer).creator(creator).status(PrivateSessionStatus.ACTIVE).build();
        when(sessionRepository.findById(100L)).thenReturn(Optional.of(session));
        when(privateSessionRepository.findFirstByCreator_IdAndStatusOrderByStartedAtDesc(1L, PrivateSessionStatus.ACTIVE))
                .thenReturn(Optional.of(ps));

        assertTrue(service.hasAccess(100L, 2L));
    }

    @Test
    void activeSpyViewer_AllowedDuringActivePrivate() {
        UUID psId = UUID.randomUUID();
        PrivateSession ps = PrivateSession.builder().id(psId).viewer(viewer).creator(creator).status(PrivateSessionStatus.ACTIVE).build();
        when(sessionRepository.findById(100L)).thenReturn(Optional.of(session));
        when(privateSessionRepository.findFirstByCreator_IdAndStatusOrderByStartedAtDesc(1L, PrivateSessionStatus.ACTIVE))
                .thenReturn(Optional.of(ps));
        when(privateSpySessionRepository.existsBySpyViewer_IdAndPrivateSession_IdAndStatus(99L, psId, SpySessionStatus.ACTIVE))
                .thenReturn(true);

        assertTrue(service.hasAccess(100L, 99L));
    }

    @Test
    void unauthorizedViewer_DeniedDuringActivePrivate() {
        UUID psId = UUID.randomUUID();
        PrivateSession ps = PrivateSession.builder().id(psId).viewer(viewer).creator(creator).status(PrivateSessionStatus.ACTIVE).build();
        when(sessionRepository.findById(100L)).thenReturn(Optional.of(session));
        when(privateSessionRepository.findFirstByCreator_IdAndStatusOrderByStartedAtDesc(1L, PrivateSessionStatus.ACTIVE))
                .thenReturn(Optional.of(ps));
        when(privateSpySessionRepository.existsBySpyViewer_IdAndPrivateSession_IdAndStatus(99L, psId, SpySessionStatus.ACTIVE))
                .thenReturn(false);

        assertFalse(service.hasAccess(100L, 99L));
    }

    @Test
    void anonymousViewer_DeniedDuringActivePrivate() {
        UUID psId = UUID.randomUUID();
        PrivateSession ps = PrivateSession.builder().id(psId).viewer(viewer).creator(creator).status(PrivateSessionStatus.ACTIVE).build();
        when(sessionRepository.findById(100L)).thenReturn(Optional.of(session));
        when(privateSessionRepository.findFirstByCreator_IdAndStatusOrderByStartedAtDesc(1L, PrivateSessionStatus.ACTIVE))
                .thenReturn(Optional.of(ps));

        assertFalse(service.hasAccess(100L, null));
    }

    @Test
    void afterPrivateEnds_NormalAccessRestored() {
        when(sessionRepository.findById(100L)).thenReturn(Optional.of(session));
        when(privateSessionRepository.findFirstByCreator_IdAndStatusOrderByStartedAtDesc(1L, PrivateSessionStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertTrue(service.hasAccess(100L, 99L));
    }

    @Test
    void nullSessionId_ReturnsFalse() {
        assertFalse(service.hasAccess(null, 1L));
    }
}
