package com.joinlivora.backend.streaming.service;

import com.joinlivora.backend.websocket.RealtimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ViewerBroadcastCoordinatorTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private LiveViewerCounterService liveViewerCounterService;

    @Mock
    private SetOperations<String, String> setOperations;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private ViewerBroadcastCoordinator coordinator;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOperations);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        coordinator = new ViewerBroadcastCoordinator(redisTemplate, messagingTemplate, liveViewerCounterService);
    }

    // --- scheduleBroadcast tests ---

    @Test
    void scheduleBroadcast_addsCreatorIdToRedisSet() {
        coordinator.scheduleBroadcast(42L);

        verify(setOperations).add("viewer:pending", "42");
    }

    @Test
    void scheduleBroadcast_nullCreatorId_doesNothing() {
        coordinator.scheduleBroadcast(null);

        verify(redisTemplate, never()).opsForSet();
    }

    @Test
    void scheduleBroadcast_redisError_doesNotThrow() {
        when(setOperations.add(anyString(), any(String[].class)))
                .thenThrow(new RuntimeException("Redis connection lost"));

        assertDoesNotThrow(() -> coordinator.scheduleBroadcast(99L));
    }

    @Test
    void scheduleBroadcast_multipleCreators_addsEach() {
        coordinator.scheduleBroadcast(1L);
        coordinator.scheduleBroadcast(2L);
        coordinator.scheduleBroadcast(3L);

        verify(setOperations).add("viewer:pending", "1");
        verify(setOperations).add("viewer:pending", "2");
        verify(setOperations).add("viewer:pending", "3");
    }

    // --- processBroadcasts tests ---

    @Test
    void processBroadcasts_acquiresLockAndBroadcasts() {
        when(valueOperations.setIfAbsent("viewer:broadcast:lock", "1", Duration.ofSeconds(3)))
                .thenReturn(true);
        when(setOperations.members("viewer:pending"))
                .thenReturn(Set.of("42"));
        when(liveViewerCounterService.getViewerCount(42L))
                .thenReturn(150L);

        coordinator.processBroadcasts();

        verify(redisTemplate).delete("viewer:pending");
        verify(messagingTemplate).convertAndSend(
                eq("/exchange/amq.topic/viewers.42"),
                any(RealtimeMessage.class)
        );
    }

    @Test
    void processBroadcasts_lockNotAcquired_doesNotBroadcast() {
        when(valueOperations.setIfAbsent("viewer:broadcast:lock", "1", Duration.ofSeconds(3)))
                .thenReturn(false);

        coordinator.processBroadcasts();

        verify(setOperations, never()).members(anyString());
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void processBroadcasts_lockReturnsNull_doesNotBroadcast() {
        when(valueOperations.setIfAbsent("viewer:broadcast:lock", "1", Duration.ofSeconds(3)))
                .thenReturn(null);

        coordinator.processBroadcasts();

        verify(setOperations, never()).members(anyString());
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void processBroadcasts_emptyPendingSet_doesNotBroadcast() {
        when(valueOperations.setIfAbsent("viewer:broadcast:lock", "1", Duration.ofSeconds(3)))
                .thenReturn(true);
        when(setOperations.members("viewer:pending"))
                .thenReturn(Set.of());

        coordinator.processBroadcasts();

        verify(redisTemplate, never()).delete("viewer:pending");
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void processBroadcasts_nullPendingSet_doesNotBroadcast() {
        when(valueOperations.setIfAbsent("viewer:broadcast:lock", "1", Duration.ofSeconds(3)))
                .thenReturn(true);
        when(setOperations.members("viewer:pending"))
                .thenReturn(null);

        coordinator.processBroadcasts();

        verify(redisTemplate, never()).delete("viewer:pending");
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void processBroadcasts_multipleCreators_broadcastsAll() {
        when(valueOperations.setIfAbsent("viewer:broadcast:lock", "1", Duration.ofSeconds(3)))
                .thenReturn(true);
        Set<String> pending = new HashSet<>();
        pending.add("10");
        pending.add("20");
        pending.add("30");
        when(setOperations.members("viewer:pending")).thenReturn(pending);
        when(liveViewerCounterService.getViewerCount(10L)).thenReturn(100L);
        when(liveViewerCounterService.getViewerCount(20L)).thenReturn(200L);
        when(liveViewerCounterService.getViewerCount(30L)).thenReturn(0L);

        coordinator.processBroadcasts();

        verify(messagingTemplate).convertAndSend(eq("/exchange/amq.topic/viewers.10"), any(RealtimeMessage.class));
        verify(messagingTemplate).convertAndSend(eq("/exchange/amq.topic/viewers.20"), any(RealtimeMessage.class));
        verify(messagingTemplate).convertAndSend(eq("/exchange/amq.topic/viewers.30"), any(RealtimeMessage.class));
        verify(redisTemplate).delete("viewer:pending");
    }

    @Test
    void processBroadcasts_invalidCreatorId_skipsAndContinues() {
        when(valueOperations.setIfAbsent("viewer:broadcast:lock", "1", Duration.ofSeconds(3)))
                .thenReturn(true);
        Set<String> pending = new HashSet<>();
        pending.add("not-a-number");
        pending.add("42");
        when(setOperations.members("viewer:pending")).thenReturn(pending);
        when(liveViewerCounterService.getViewerCount(42L)).thenReturn(5L);

        coordinator.processBroadcasts();

        verify(messagingTemplate).convertAndSend(eq("/exchange/amq.topic/viewers.42"), any(RealtimeMessage.class));
    }

    @Test
    void processBroadcasts_oneCreatorFails_othersContinue() {
        when(valueOperations.setIfAbsent("viewer:broadcast:lock", "1", Duration.ofSeconds(3)))
                .thenReturn(true);
        Set<String> pending = new HashSet<>();
        pending.add("10");
        pending.add("20");
        when(setOperations.members("viewer:pending")).thenReturn(pending);
        when(liveViewerCounterService.getViewerCount(10L)).thenThrow(new RuntimeException("Redis timeout"));
        when(liveViewerCounterService.getViewerCount(20L)).thenReturn(50L);

        assertDoesNotThrow(() -> coordinator.processBroadcasts());

        verify(messagingTemplate).convertAndSend(eq("/exchange/amq.topic/viewers.20"), any(RealtimeMessage.class));
    }

    @Test
    void processBroadcasts_broadcastMessageContainsCorrectPayload() {
        when(valueOperations.setIfAbsent("viewer:broadcast:lock", "1", Duration.ofSeconds(3)))
                .thenReturn(true);
        when(setOperations.members("viewer:pending")).thenReturn(Set.of("42"));
        when(liveViewerCounterService.getViewerCount(42L)).thenReturn(250L);

        coordinator.processBroadcasts();

        ArgumentCaptor<RealtimeMessage> captor = ArgumentCaptor.forClass(RealtimeMessage.class);
        verify(messagingTemplate).convertAndSend(eq("/exchange/amq.topic/viewers.42"), captor.capture());

        RealtimeMessage message = captor.getValue();
        assertEquals("viewer_count:update", message.getType());
        assertNotNull(message.getTimestamp());
        Map<String, Object> payload = message.getPayload();
        assertEquals(42L, payload.get("creatorUserId"));
        assertEquals(250L, payload.get("viewerCount"));
    }

    @Test
    void processBroadcasts_zeroViewerCount_stillBroadcasts() {
        when(valueOperations.setIfAbsent("viewer:broadcast:lock", "1", Duration.ofSeconds(3)))
                .thenReturn(true);
        when(setOperations.members("viewer:pending")).thenReturn(Set.of("99"));
        when(liveViewerCounterService.getViewerCount(99L)).thenReturn(0L);

        coordinator.processBroadcasts();

        verify(messagingTemplate).convertAndSend(
                eq("/exchange/amq.topic/viewers.99"),
                any(RealtimeMessage.class)
        );
    }

    @Test
    void processBroadcasts_redisErrorOnLock_doesNotThrow() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new RuntimeException("Redis connection lost"));

        assertDoesNotThrow(() -> coordinator.processBroadcasts());

        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void processBroadcasts_redisErrorOnMembers_doesNotThrow() {
        when(valueOperations.setIfAbsent("viewer:broadcast:lock", "1", Duration.ofSeconds(3)))
                .thenReturn(true);
        when(setOperations.members("viewer:pending"))
                .thenThrow(new RuntimeException("Redis timeout"));

        assertDoesNotThrow(() -> coordinator.processBroadcasts());
    }

    @Test
    void processBroadcasts_deletesPendingSetBeforeBroadcasting() {
        when(valueOperations.setIfAbsent("viewer:broadcast:lock", "1", Duration.ofSeconds(3)))
                .thenReturn(true);
        when(setOperations.members("viewer:pending")).thenReturn(Set.of("42"));
        when(liveViewerCounterService.getViewerCount(42L)).thenReturn(10L);

        coordinator.processBroadcasts();

        // Verify delete happens
        verify(redisTemplate).delete("viewer:pending");
    }

    @Test
    void processBroadcasts_correctDestinationFormat() {
        when(valueOperations.setIfAbsent("viewer:broadcast:lock", "1", Duration.ofSeconds(3)))
                .thenReturn(true);
        when(setOperations.members("viewer:pending")).thenReturn(Set.of("123"));
        when(liveViewerCounterService.getViewerCount(123L)).thenReturn(77L);

        coordinator.processBroadcasts();

        verify(messagingTemplate).convertAndSend(
                eq("/exchange/amq.topic/viewers.123"),
                any(RealtimeMessage.class)
        );
    }
}
