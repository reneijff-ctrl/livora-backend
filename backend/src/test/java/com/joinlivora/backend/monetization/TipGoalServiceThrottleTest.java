package com.joinlivora.backend.monetization;

import com.joinlivora.backend.chat.dto.GoalStatusEventDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TipGoalServiceThrottleTest {

    @Mock
    private TipGoalRepository tipGoalRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private TipGoalService tipGoalService;

    private Long creatorId = 1L;
    private UUID goalId = UUID.randomUUID();
    private TipGoal activeGoal;

    @BeforeEach
    void setUp() {
        activeGoal = TipGoal.builder()
                .id(goalId)
                .creatorId(creatorId)
                .title("Test Goal")
                .targetAmount(1000L)
                .currentAmount(100L)
                .active(true)
                .build();
    }

    @Test
    void processTip_ShouldThrottleRapidProgressUpdates() throws InterruptedException {
        when(tipGoalRepository.findFirstByCreatorIdAndActiveTrueOrderByCreatedAtDesc(creatorId))
                .thenReturn(Optional.of(activeGoal));
        when(tipGoalRepository.findById(goalId)).thenReturn(Optional.of(activeGoal));

        // Send 3 tips rapidly
        tipGoalService.processTip(creatorId, 10L);
        tipGoalService.processTip(creatorId, 10L);
        tipGoalService.processTip(creatorId, 10L);

        // Should only broadcast PROGRESS once due to throttle (1000ms default)
        // Note: The first one always goes through. Subsequent ones within 1s are blocked.
        verify(messagingTemplate, times(1)).convertAndSend(eq("/exchange/amq.topic/chat." + creatorId), any(GoalStatusEventDto.class));
    }

    @Test
    void processTip_ShouldNotThrottleCompletedEvents() {
        when(tipGoalRepository.findFirstByCreatorIdAndActiveTrueOrderByCreatedAtDesc(creatorId))
                .thenReturn(Optional.of(activeGoal));
        
        // Setup goal to be completed on first tip
        activeGoal.setCurrentAmount(1000L);
        when(tipGoalRepository.findById(goalId)).thenReturn(Optional.of(activeGoal));

        tipGoalService.processTip(creatorId, 900L);

        // Should send PROGRESS and COMPLETED
        ArgumentCaptor<GoalStatusEventDto> captor = ArgumentCaptor.forClass(GoalStatusEventDto.class);
        verify(messagingTemplate, times(2)).convertAndSend(eq("/exchange/amq.topic/chat." + creatorId), captor.capture());
        
        List<GoalStatusEventDto> events = captor.getAllValues();
        assertEquals("GOAL_PROGRESS", events.get(0).getType());
        assertEquals("GOAL_COMPLETED", events.get(1).getType());
    }
}
