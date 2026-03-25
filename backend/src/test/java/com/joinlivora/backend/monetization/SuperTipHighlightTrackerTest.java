package com.joinlivora.backend.monetization;

import com.joinlivora.backend.monetization.dto.SuperTipResponse;
import com.joinlivora.backend.websocket.RealtimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import com.joinlivora.backend.streaming.StreamRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SuperTipHighlightTrackerTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private StreamRepository streamRepository;

    private SuperTipHighlightTracker tracker;

    private UUID roomId;
    private SuperTipResponse tip1;
    private SuperTipResponse tip2;
    private Long creatorId = 123L;

    @BeforeEach
    void setUp() {
        tracker = new SuperTipHighlightTracker(messagingTemplate, streamRepository);
        roomId = UUID.randomUUID();

        com.joinlivora.backend.user.User creator = new com.joinlivora.backend.user.User();
        creator.setId(creatorId);
        com.joinlivora.backend.streaming.Stream room = com.joinlivora.backend.streaming.Stream.builder()
                .id(roomId)
                .creator(creator)
                .build();
        lenient().when(streamRepository.findById(roomId)).thenReturn(Optional.of(room));

        tip1 = SuperTipResponse.builder()
                .id(UUID.randomUUID())
                .senderEmail("user1@test.com")
                .amount(new BigDecimal("1000"))
                .highlightLevel(HighlightLevel.BASIC)
                .durationSeconds(1) // Short duration for testing
                .build();

        tip2 = SuperTipResponse.builder()
                .id(UUID.randomUUID())
                .senderEmail("user2@test.com")
                .amount(new BigDecimal("5000"))
                .highlightLevel(HighlightLevel.PREMIUM)
                .durationSeconds(2)
                .build();
    }

    @Test
    void addHighlight_NoActive_ShouldStartImmediately() {
        tracker.addHighlight(roomId, tip1);

        Optional<SuperTipResponse> active = tracker.getActiveHighlight(roomId);
        assertTrue(active.isPresent());
        assertEquals(tip1.getId(), active.get().getId());

        verify(messagingTemplate).convertAndSend(eq("/exchange/amq.topic/chat." + creatorId), any(RealtimeMessage.class));
    }

    @Test
    void addHighlight_ExistingActive_ShouldQueue() {
        tracker.addHighlight(roomId, tip1);
        tracker.addHighlight(roomId, tip2);

        // Still tip1 active
        Optional<SuperTipResponse> active = tracker.getActiveHighlight(roomId);
        assertTrue(active.isPresent());
        assertEquals(tip1.getId(), active.get().getId());

        // Only one start event sent so far
        verify(messagingTemplate, times(1)).convertAndSend(eq("/exchange/amq.topic/chat." + creatorId), any(RealtimeMessage.class));
    }

    @Test
    void cleanupExpiredHighlights_ShouldProcessNextInQueue() throws InterruptedException {
        tracker.addHighlight(roomId, tip1);
        tracker.addHighlight(roomId, tip2);

        // Simulate time passing
        Thread.sleep(1100); 

        tracker.cleanupExpiredHighlights();

        // tip1 should be expired, tip2 should be active
        Optional<SuperTipResponse> active = tracker.getActiveHighlight(roomId);
        assertTrue(active.isPresent());
        assertEquals(tip2.getId(), active.get().getId());

        // Verify end event for tip1 and start event for tip2
        ArgumentCaptor<RealtimeMessage> captor = ArgumentCaptor.forClass(RealtimeMessage.class);
        verify(messagingTemplate, times(3)).convertAndSend(eq("/exchange/amq.topic/chat." + creatorId), captor.capture());
        
        List<RealtimeMessage> messages = captor.getAllValues();
        // messages[0] = SUPER_TIP (tip1)
        // messages[1] = SUPER_TIP_END (tip1)
        // messages[2] = SUPER_TIP (tip2)
        assertEquals("SUPER_TIP", messages.get(0).getType());
        assertEquals("SUPER_TIP_END", messages.get(1).getType());
        assertEquals("SUPER_TIP", messages.get(2).getType());
    }

    @Test
    void getActiveHighlight_Expired_ShouldReturnEmpty() throws InterruptedException {
        tracker.addHighlight(roomId, tip1);
        
        Thread.sleep(1100);
        
        Optional<SuperTipResponse> active = tracker.getActiveHighlight(roomId);
        assertFalse(active.isPresent());
    }
}








