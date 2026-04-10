package com.joinlivora.backend.streaming.service;

import com.joinlivora.backend.privateshow.*;
import com.joinlivora.backend.resilience.DatabaseCircuitBreakerService;
import com.joinlivora.backend.resilience.RedisCircuitBreakerService;
import com.joinlivora.backend.streaming.Stream;
import com.joinlivora.backend.streaming.StreamRepository;
import com.joinlivora.backend.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LivestreamAccessServiceTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private StreamRepository streamRepository;
    @Mock private PrivateSessionRepository privateSessionRepository;
    @Mock private PrivateSpySessionRepository privateSpySessionRepository;
    @Mock private RedisCircuitBreakerService redisCircuitBreaker;
    @Mock private DatabaseCircuitBreakerService dbCircuitBreaker;

    @InjectMocks private LivestreamAccessService service;

    private User creator;
    private User viewer;
    private UUID streamId;
    private Stream freeStream;

    @BeforeEach
    void setUp() {
        creator = new User(); creator.setId(1L);
        viewer = new User(); viewer.setId(2L);
        streamId = UUID.randomUUID();

        freeStream = new Stream();
        freeStream.setId(streamId);
        freeStream.setCreator(creator);
        freeStream.setPaid(false);

        // Make DB circuit breaker transparent: delegate to the real supplier
        lenient().when(dbCircuitBreaker.executeOptional(any(), anyString()))
                .thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(0)).get());
        lenient().when(dbCircuitBreaker.execute(any(java.util.function.Supplier.class), any(), anyString()))
                .thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(0)).get());

        // Redis circuit breaker: circuit is CLOSED, delegate to supplier
        lenient().when(redisCircuitBreaker.execute(any(java.util.function.Supplier.class), any()))
                .thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(0)).get());
        lenient().when(redisCircuitBreaker.execute(any(Runnable.class)))
                .thenAnswer(inv -> { ((Runnable) inv.getArgument(0)).run(); return true; });
    }

    @Test
    void nullStreamId_ReturnsFalse() {
        assertFalse(service.hasAccess((UUID) null, 1L));
    }

    @Test
    void unknownStreamId_ReturnsFalse() {
        when(streamRepository.findById(streamId)).thenReturn(Optional.empty());
        assertFalse(service.hasAccess(streamId, 99L));
    }

    @Test
    void noActivePrivateSession_FreeStream_ShouldAllowAnyone() {
        when(streamRepository.findById(streamId)).thenReturn(Optional.of(freeStream));
        when(privateSessionRepository.findFirstByCreator_IdAndStatusOrderByStartedAtDesc(1L, PrivateSessionStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertTrue(service.hasAccess(streamId, 99L));
    }

    @Test
    void creatorAlwaysAllowed_FreeStream() {
        when(streamRepository.findById(streamId)).thenReturn(Optional.of(freeStream));

        assertTrue(service.hasAccess(streamId, 1L));
        // Creator check short-circuits before any private session check
        verifyNoInteractions(privateSessionRepository);
    }

    @Test
    void assignedPrivateViewer_AllowedDuringActivePrivate() {
        PrivateSession ps = PrivateSession.builder().id(UUID.randomUUID()).viewer(viewer).creator(creator).status(PrivateSessionStatus.ACTIVE).build();
        when(streamRepository.findById(streamId)).thenReturn(Optional.of(freeStream));
        when(privateSessionRepository.findFirstByCreator_IdAndStatusOrderByStartedAtDesc(1L, PrivateSessionStatus.ACTIVE))
                .thenReturn(Optional.of(ps));

        assertTrue(service.hasAccess(streamId, 2L));
    }

    @Test
    void activeSpyViewer_AllowedDuringActivePrivate() {
        UUID psId = UUID.randomUUID();
        PrivateSession ps = PrivateSession.builder().id(psId).viewer(viewer).creator(creator).status(PrivateSessionStatus.ACTIVE).build();
        when(streamRepository.findById(streamId)).thenReturn(Optional.of(freeStream));
        when(privateSessionRepository.findFirstByCreator_IdAndStatusOrderByStartedAtDesc(1L, PrivateSessionStatus.ACTIVE))
                .thenReturn(Optional.of(ps));
        when(privateSpySessionRepository.existsBySpyViewer_IdAndPrivateSession_IdAndStatus(99L, psId, SpySessionStatus.ACTIVE))
                .thenReturn(true);

        assertTrue(service.hasAccess(streamId, 99L));
    }

    @Test
    void unauthorizedViewer_DeniedDuringActivePrivate() {
        UUID psId = UUID.randomUUID();
        PrivateSession ps = PrivateSession.builder().id(psId).viewer(viewer).creator(creator).status(PrivateSessionStatus.ACTIVE).build();
        when(streamRepository.findById(streamId)).thenReturn(Optional.of(freeStream));
        when(privateSessionRepository.findFirstByCreator_IdAndStatusOrderByStartedAtDesc(1L, PrivateSessionStatus.ACTIVE))
                .thenReturn(Optional.of(ps));
        when(privateSpySessionRepository.existsBySpyViewer_IdAndPrivateSession_IdAndStatus(99L, psId, SpySessionStatus.ACTIVE))
                .thenReturn(false);

        assertFalse(service.hasAccess(streamId, 99L));
    }

    @Test
    void anonymousViewer_DeniedDuringActivePrivate() {
        UUID psId = UUID.randomUUID();
        PrivateSession ps = PrivateSession.builder().id(psId).viewer(viewer).creator(creator).status(PrivateSessionStatus.ACTIVE).build();
        when(streamRepository.findById(streamId)).thenReturn(Optional.of(freeStream));
        when(privateSessionRepository.findFirstByCreator_IdAndStatusOrderByStartedAtDesc(1L, PrivateSessionStatus.ACTIVE))
                .thenReturn(Optional.of(ps));

        assertFalse(service.hasAccess(streamId, null));
    }

    @Test
    void afterPrivateEnds_NormalAccessRestored() {
        when(streamRepository.findById(streamId)).thenReturn(Optional.of(freeStream));
        when(privateSessionRepository.findFirstByCreator_IdAndStatusOrderByStartedAtDesc(1L, PrivateSessionStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertTrue(service.hasAccess(streamId, 99L));
    }

    @Test
    void paidStream_ViewerWithAccessKey_Allowed() {
        Stream paidStream = new Stream();
        paidStream.setId(streamId);
        paidStream.setCreator(creator);
        paidStream.setPaid(true);

        when(streamRepository.findById(streamId)).thenReturn(Optional.of(paidStream));
        when(privateSessionRepository.findFirstByCreator_IdAndStatusOrderByStartedAtDesc(1L, PrivateSessionStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(redisTemplate.hasKey("access:" + streamId + ":2")).thenReturn(true);

        assertTrue(service.hasAccess(streamId, 2L));
    }

    @Test
    void paidStream_ViewerWithoutAccessKey_Denied() {
        Stream paidStream = new Stream();
        paidStream.setId(streamId);
        paidStream.setCreator(creator);
        paidStream.setPaid(true);

        when(streamRepository.findById(streamId)).thenReturn(Optional.of(paidStream));
        when(privateSessionRepository.findFirstByCreator_IdAndStatusOrderByStartedAtDesc(1L, PrivateSessionStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(redisTemplate.hasKey("access:" + streamId + ":2")).thenReturn(false);

        assertFalse(service.hasAccess(streamId, 2L));
    }
}
