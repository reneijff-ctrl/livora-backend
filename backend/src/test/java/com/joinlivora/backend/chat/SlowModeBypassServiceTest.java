package com.joinlivora.backend.chat;

import com.joinlivora.backend.analytics.AnalyticsEventPublisher;
import com.joinlivora.backend.analytics.AnalyticsEventType;
import com.joinlivora.backend.streaming.Stream;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.websocket.RealtimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SlowModeBypassServiceTest {

    @Mock
    private SlowModeBypassRepository repository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private AnalyticsEventPublisher analyticsEventPublisher;

    @InjectMocks
    private SlowModeBypassService service;

    private User user;
    private Stream room;
    private UUID roomId;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setEmail("test@test.com");

        roomId = UUID.randomUUID();
        room = Stream.builder().id(roomId).build();
    }

    @Test
    void grantBypass_NoExisting_ShouldCreateNewAndBroadcastAndLogAnalytics() {
        when(repository.findActiveByUserIdAndRoomId(eq(1L), eq(roomId), any())).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.grantBypass(user, room, 60, SlowModeBypassSource.SUPERTIP);

        verify(repository).save(argThat(b -> 
            b.getUserId().equals(user) && 
            b.getRoomId().equals(room) && 
            b.getSource().equals(SlowModeBypassSource.SUPERTIP)
        ));

        verify(messagingTemplate).convertAndSend(eq("/exchange/amq.topic/chat." + roomId), argThat((RealtimeMessage msg) -> 
            "SLOW_MODE_BYPASS_GRANTED".equals(msg.getType()) &&
            roomId.equals(msg.getPayload().get("roomId")) &&
            Long.valueOf(1L).equals(msg.getPayload().get("creator"))
        ));

        verify(analyticsEventPublisher).publishEvent(
                eq(AnalyticsEventType.SLOW_MODE_BYPASS_GRANTED),
                eq(user),
                argThat(map -> 
                    map.get("roomId").equals(roomId) &&
                    map.get("creator").equals(1L) &&
                    map.get("source").equals("SUPERTIP") &&
                    map.get("durationSeconds").equals(60)
                )
        );
    }

    @Test
    void grantBypass_Existing_ShouldExtendAndBroadcastAndLogAnalytics() {
        Instant oldExpiry = Instant.now().plusSeconds(30);
        SlowModeBypass existing = SlowModeBypass.builder()
                .userId(user)
                .roomId(room)
                .expiresAt(oldExpiry)
                .source(SlowModeBypassSource.PPV)
                .build();

        when(repository.findActiveByUserIdAndRoomId(eq(1L), eq(roomId), any())).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.grantBypass(user, room, 60, SlowModeBypassSource.SUPERTIP);

        verify(repository).save(existing);
        assertTrue(existing.getExpiresAt().isAfter(oldExpiry));
        assertEquals(SlowModeBypassSource.SUPERTIP, existing.getSource());

        verify(messagingTemplate).convertAndSend(eq("/exchange/amq.topic/chat." + roomId), argThat((RealtimeMessage msg) -> 
            "SLOW_MODE_BYPASS_GRANTED".equals(msg.getType()) &&
            existing.getExpiresAt().equals(msg.getPayload().get("expiresAt"))
        ));

        verify(analyticsEventPublisher).publishEvent(
                eq(AnalyticsEventType.SLOW_MODE_BYPASS_GRANTED),
                eq(user),
                argThat(map -> 
                    map.get("source").equals("SUPERTIP") &&
                    map.get("durationSeconds").equals(60)
                )
        );
    }

    @Test
    void isBypassing_ShouldReturnRepositoryResult() {
        when(repository.findActiveByUserIdAndRoomId(eq(1L), eq(roomId), any())).thenReturn(Optional.of(new SlowModeBypass()));
        assertTrue(service.isBypassing(1L, roomId));

        when(repository.findActiveByUserIdAndRoomId(eq(1L), eq(roomId), any())).thenReturn(Optional.empty());
        assertFalse(service.isBypassing(1L, roomId));
    }

    @Test
    void revokeBypass_Existing_ShouldDeleteAndBroadcastAndLogAnalytics() {
        SlowModeBypass existing = SlowModeBypass.builder()
                .userId(user)
                .roomId(room)
                .source(SlowModeBypassSource.SUPERTIP)
                .build();

        when(repository.findActiveByUserIdAndRoomId(eq(1L), eq(roomId), any())).thenReturn(Optional.of(existing));

        service.revokeBypass(1L, roomId);

        verify(repository).delete(existing);
        verify(messagingTemplate).convertAndSend(eq("/exchange/amq.topic/chat." + roomId), argThat((RealtimeMessage msg) -> 
            "SLOW_MODE_BYPASS_REVOKED".equals(msg.getType()) &&
            roomId.equals(msg.getPayload().get("roomId")) &&
            Long.valueOf(1L).equals(msg.getPayload().get("creator"))
        ));

        verify(analyticsEventPublisher).publishEvent(
                eq(AnalyticsEventType.SLOW_MODE_BYPASS_REVOKED),
                eq(user),
                argThat(map -> 
                    map.get("roomId").equals(roomId) &&
                    map.get("creator").equals(1L) &&
                    map.get("source").equals("SUPERTIP")
                )
        );
    }

    @Test
    void revokeBypass_NotExisting_ShouldDoNothing() {
        when(repository.findActiveByUserIdAndRoomId(eq(1L), eq(roomId), any())).thenReturn(Optional.empty());

        service.revokeBypass(1L, roomId);

        verify(repository, never()).delete(any());
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(RealtimeMessage.class));
    }

    @Test
    void cleanupExpired_ShouldCallRepositoryAndLogAnalytics() {
        Instant now = Instant.now();
        SlowModeBypass expired = SlowModeBypass.builder()
                .userId(user)
                .roomId(room)
                .createdAt(now.minusSeconds(120))
                .expiresAt(now.minusSeconds(60))
                .source(SlowModeBypassSource.SUPERTIP)
                .build();
        
        when(repository.findAllByExpiresAtBefore(any())).thenReturn(List.of(expired));

        service.cleanupExpired();

        verify(analyticsEventPublisher).publishEvent(
                eq(AnalyticsEventType.SLOW_MODE_BYPASS_EXPIRED),
                eq(user),
                argThat(map -> 
                    map.get("roomId").equals(roomId) &&
                    map.get("creator").equals(1L) &&
                    map.get("source").equals("SUPERTIP") &&
                    map.get("durationSeconds").equals(60L) // 120 - 60 = 60
                )
        );
        verify(repository).deleteExpired(any());
    }

    @Test
    void scheduledCleanup_ShouldCallCleanupExpired() {
        SlowModeBypass expired = SlowModeBypass.builder()
                .userId(user)
                .roomId(room)
                .createdAt(Instant.now().minusSeconds(120))
                .expiresAt(Instant.now().minusSeconds(60))
                .source(SlowModeBypassSource.SUPERTIP)
                .build();
        when(repository.findAllByExpiresAtBefore(any())).thenReturn(List.of(expired));
        service.scheduledCleanup();
        verify(repository).deleteExpired(any());
    }
}








