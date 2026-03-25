package com.joinlivora.backend.monetization;

import com.joinlivora.backend.chat.dto.GoalStatusEventDto;
import com.joinlivora.backend.monetization.dto.TipGoalDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TipGoalServiceTest {

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
                .targetAmount(100L)
                .currentAmount(50L)
                .active(true)
                .orderIndex(1)
                .build();
    }

    @Test
    void processTip_ShouldIncrementAmountAndBroadcastProgress() {
        when(tipGoalRepository.findFirstByCreatorIdAndActiveTrueOrderByCreatedAtDesc(creatorId))
                .thenReturn(Optional.of(activeGoal));
        
        // Mock increment
        activeGoal.setCurrentAmount(activeGoal.getCurrentAmount() + 10L);
        when(tipGoalRepository.findById(goalId)).thenReturn(Optional.of(activeGoal));

        tipGoalService.processTip(creatorId, 10L);

        verify(tipGoalRepository).incrementCurrentAmount(goalId, 10L);
        ArgumentCaptor<GoalStatusEventDto> captor = ArgumentCaptor.forClass(GoalStatusEventDto.class);
        verify(messagingTemplate).convertAndSend(eq("/exchange/amq.topic/chat." + creatorId), captor.capture());
        
        GoalStatusEventDto event = captor.getValue();
        assertEquals("GOAL_PROGRESS", event.getType());
        assertEquals(60L, event.getCurrentAmount());
    }

    @Test
    void processTip_ShouldHandleChainedGoals() {
        activeGoal.setCurrentAmount(90L);
        when(tipGoalRepository.findFirstByCreatorIdAndActiveTrueOrderByCreatedAtDesc(creatorId))
                .thenReturn(Optional.of(activeGoal));
        
        activeGoal.setCurrentAmount(100L);
        when(tipGoalRepository.findById(goalId)).thenReturn(Optional.of(activeGoal));

        TipGoal nextGoal = TipGoal.builder()
                .id(UUID.randomUUID())
                .creatorId(creatorId)
                .title("Next Goal")
                .targetAmount(200L)
                .currentAmount(0L)
                .active(false)
                .orderIndex(2)
                .build();

        when(tipGoalRepository.findFirstByCreatorIdAndOrderIndexGreaterThanOrderByOrderIndexAsc(eq(creatorId), eq(1)))
                .thenReturn(Optional.of(nextGoal));
        when(tipGoalRepository.save(any(TipGoal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        tipGoalService.processTip(creatorId, 10L);

        // Verify current goal deactivated
        assertFalse(activeGoal.isActive());
        // Verify next goal activated
        assertTrue(nextGoal.isActive());
        assertEquals(0L, nextGoal.getCurrentAmount());

        // Verify broadcasts: PROGRESS, COMPLETED, SWITCH
        ArgumentCaptor<GoalStatusEventDto> captor = ArgumentCaptor.forClass(GoalStatusEventDto.class);
        verify(messagingTemplate, times(3)).convertAndSend(eq("/exchange/amq.topic/chat." + creatorId), captor.capture());
        
        var events = captor.getAllValues();
        assertEquals("GOAL_PROGRESS", events.get(0).getType());
        assertEquals("GOAL_COMPLETED", events.get(1).getType());
        assertEquals("GOAL_SWITCH", events.get(2).getType());
        assertEquals("Next Goal", events.get(2).getTitle());
        assertEquals(200L, events.get(2).getTargetAmount());
    }

    @Test
    void processTip_ShouldHandleNoNextGoal() {
        activeGoal.setCurrentAmount(90L);
        activeGoal.setOrderIndex(1);
        when(tipGoalRepository.findFirstByCreatorIdAndActiveTrueOrderByCreatedAtDesc(creatorId))
                .thenReturn(Optional.of(activeGoal));
        
        activeGoal.setCurrentAmount(100L);
        when(tipGoalRepository.findById(goalId)).thenReturn(Optional.of(activeGoal));

        when(tipGoalRepository.findFirstByCreatorIdAndOrderIndexGreaterThanOrderByOrderIndexAsc(eq(creatorId), eq(1)))
                .thenReturn(Optional.empty());

        tipGoalService.processTip(creatorId, 10L);

        // Verify current goal deactivated
        assertFalse(activeGoal.isActive());
        
        // Only 2 broadcasts (PROGRESS, COMPLETED), no SWITCH
        ArgumentCaptor<GoalStatusEventDto> captor = ArgumentCaptor.forClass(GoalStatusEventDto.class);
        verify(messagingTemplate, times(2)).convertAndSend(eq("/exchange/amq.topic/chat." + creatorId), captor.capture());
        
        var events = captor.getAllValues();
        assertEquals("GOAL_PROGRESS", events.get(0).getType());
        assertEquals("GOAL_COMPLETED", events.get(1).getType());
    }

    @Test
    void createGoal_ShouldSetOrderIndex() {
        TipGoalDto dto = TipGoalDto.builder()
                .title("New Goal")
                .targetAmount(500L)
                .active(true)
                .orderIndex(5)
                .build();

        when(tipGoalRepository.save(any(TipGoal.class))).thenAnswer(invocation -> {
            TipGoal saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        TipGoalDto result = tipGoalService.createGoal(creatorId, dto);

        assertEquals(5, result.getOrderIndex());
        verify(tipGoalRepository).save(argThat(goal -> goal.getOrderIndex() == 5));
    }

    @Test
    void updateGoal_ShouldUpdateOrderIndex() {
        when(tipGoalRepository.findById(goalId)).thenReturn(Optional.of(activeGoal));
        when(tipGoalRepository.save(any(TipGoal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TipGoalDto dto = TipGoalDto.builder()
                .title("Updated Goal")
                .targetAmount(100L)
                .active(true)
                .orderIndex(10)
                .build();

        TipGoalDto result = tipGoalService.updateGoal(goalId, creatorId, dto);

        assertEquals(10, result.getOrderIndex());
        assertEquals(10, activeGoal.getOrderIndex());
    }

    @Test
    void reorderGoals_ShouldUpdateIndexes() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        TipGoal g1 = TipGoal.builder().id(id1).creatorId(creatorId).active(false).build();
        TipGoal g2 = TipGoal.builder().id(id2).creatorId(creatorId).active(false).build();

        when(tipGoalRepository.findById(id1)).thenReturn(Optional.of(g1));
        when(tipGoalRepository.findById(id2)).thenReturn(Optional.of(g2));
        // getGoals is called at the end
        when(tipGoalRepository.findAllByCreatorIdOrderByOrderIndexAsc(creatorId)).thenReturn(java.util.List.of(g2, g1));

        tipGoalService.reorderGoals(creatorId, java.util.List.of(id2, id1));

        assertEquals(0, g2.getOrderIndex());
        assertEquals(1, g1.getOrderIndex());
        verify(tipGoalRepository, times(2)).save(any(TipGoal.class));
    }

    @Test
    void createGoal_ShouldDeactivateOthersIfActive() {
        TipGoal existingActive = TipGoal.builder()
                .id(UUID.randomUUID())
                .creatorId(creatorId)
                .active(true)
                .build();
        
        when(tipGoalRepository.findAllByCreatorIdOrderByOrderIndexAsc(creatorId))
                .thenReturn(java.util.List.of(existingActive));
        
        TipGoalDto dto = TipGoalDto.builder()
                .title("New Active Goal")
                .targetAmount(100L)
                .active(true)
                .build();

        when(tipGoalRepository.save(any(TipGoal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        tipGoalService.createGoal(creatorId, dto);

        assertFalse(existingActive.isActive());
        verify(tipGoalRepository, atLeastOnce()).save(existingActive);
    }
}








