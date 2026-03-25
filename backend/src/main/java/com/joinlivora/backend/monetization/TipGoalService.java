package com.joinlivora.backend.monetization;

import com.joinlivora.backend.chat.dto.GoalStatusEventDto;
import com.joinlivora.backend.streaming.service.StreamAssistantBotService;
import com.joinlivora.backend.websocket.LiveEvent;
import com.joinlivora.backend.monetization.dto.TipGoalDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TipGoalService {

    private final TipGoalRepository tipGoalRepository;
    private final TipGoalGroupRepository groupRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final StreamAssistantBotService streamAssistantBotService;

    private final ConcurrentHashMap<Long, Long> lastGoalBroadcast = new ConcurrentHashMap<>();
    private static final long GOAL_BROADCAST_INTERVAL_MS = 1000;

    @Transactional(readOnly = true)
    public Optional<TipGoal> getActiveGoal(Long creatorId) {
        return tipGoalRepository.findFirstByCreatorIdAndActiveTrueOrderByCreatedAtDesc(creatorId);
    }

    @Transactional(readOnly = true)
    public Optional<GoalStatusEventDto> getActiveGoalGroupEvent(Long creatorId) {
        return groupRepository.findFirstByCreatorIdAndActiveTrueOrderByOrderIndexAsc(creatorId)
                .map(group -> {
                    long current = group.getCurrentAmount();
                    long target = group.getTargetAmount();
                    int percentage = target > 0 ? (int) Math.min(100, (current * 100) / target) : 0;

                    List<GoalStatusEventDto.MilestoneStatusDto> milestoneStatuses = tipGoalRepository
                            .findAllByGroupIdOrderByTargetAmountAsc(group.getId()).stream()
                            .map(m -> GoalStatusEventDto.MilestoneStatusDto.builder()
                                    .title(m.getTitle())
                                    .targetAmount(m.getTargetAmount())
                                    .reached(current >= m.getTargetAmount())
                                    .build())
                            .collect(Collectors.toList());

                    return GoalStatusEventDto.builder()
                            .type("GOAL_GROUP_PROGRESS")
                            .title(group.getTitle())
                            .targetAmount(target)
                            .currentAmount(current)
                            .percentage(percentage)
                            .isCompleted(current >= target)
                            .active(true)
                            .milestones(milestoneStatuses)
                            .build();
                });
    }

    public void broadcastActiveGoalGroup(Long creatorId) {
        getActiveGoalGroupEvent(creatorId).ifPresent(event -> {
            messagingTemplate.convertAndSend("/exchange/amq.topic/goals." + creatorId,
                    LiveEvent.of("GOAL_GROUP_PROGRESS", event));
        });
    }

    @Transactional(readOnly = true)
    public List<TipGoalDto> getGoals(Long creatorId) {
        return tipGoalRepository.findAllByCreatorIdOrderByOrderIndexAsc(creatorId)
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public TipGoalDto createGoal(Long creatorId, TipGoalDto dto) {
        // Deactivate existing active goals if the new goal is set to active
        if (dto.isActive()) {
            deactivateActiveGoals(creatorId);
        }

        TipGoal goal = TipGoal.builder()
                .creatorId(creatorId)
                .title(dto.getTitle())
                .targetAmount(dto.getTargetAmount())
                .currentAmount(0L)
                .active(dto.isActive())
                .autoReset(dto.isAutoReset())
                .orderIndex(dto.getOrderIndex())
                .build();

        return mapToDto(tipGoalRepository.save(goal));
    }

    @Transactional
    public TipGoalDto updateGoal(UUID id, Long creatorId, TipGoalDto dto) {
        TipGoal goal = tipGoalRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Goal not found"));

        if (!goal.getCreatorId().equals(creatorId)) {
            throw new RuntimeException("Unauthorized: This goal belongs to another creator");
        }

        if (dto.isActive() && !goal.isActive()) {
            deactivateActiveGoals(creatorId);
        }

        goal.setTitle(dto.getTitle());
        goal.setTargetAmount(dto.getTargetAmount());
        goal.setAutoReset(dto.isAutoReset());
        goal.setActive(dto.isActive());
        goal.setOrderIndex(dto.getOrderIndex());

        return mapToDto(tipGoalRepository.save(goal));
    }

    private void deactivateActiveGoals(Long creatorId) {
        tipGoalRepository.findAllByCreatorIdOrderByOrderIndexAsc(creatorId)
                .stream()
                .filter(TipGoal::isActive)
                .forEach(g -> {
                    g.setActive(false);
                    tipGoalRepository.save(g);
                });
    }

    @Transactional
    public List<TipGoalDto> reorderGoals(Long creatorId, List<UUID> goalIds) {
        for (int i = 0; i < goalIds.size(); i++) {
            UUID id = goalIds.get(i);
            TipGoal goal = tipGoalRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Goal not found: " + id));

            if (!goal.getCreatorId().equals(creatorId)) {
                throw new RuntimeException("Unauthorized: This goal belongs to another creator");
            }

            goal.setOrderIndex(i);
            tipGoalRepository.save(goal);
        }
        return getGoals(creatorId);
    }

    @Transactional
    public void deleteGoal(UUID id, Long creatorId) {
        TipGoal goal = tipGoalRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Goal not found"));

        if (!goal.getCreatorId().equals(creatorId)) {
            throw new RuntimeException("Unauthorized: This goal belongs to another creator");
        }

        tipGoalRepository.delete(goal);
    }

    public TipGoalDto mapToDto(TipGoal goal) {
        return TipGoalDto.builder()
                .id(goal.getId())
                .title(goal.getTitle())
                .targetAmount(goal.getTargetAmount())
                .currentAmount(goal.getCurrentAmount())
                .active(goal.isActive())
                .autoReset(goal.isAutoReset())
                .orderIndex(goal.getOrderIndex())
                .createdAt(goal.getCreatedAt())
                .updatedAt(goal.getUpdatedAt())
                .build();
    }

    @Transactional
    public void processTip(Long creatorId, long amount) {
        log.info("MONETIZATION: Processing tip for goal. creatorId: {}, amount: {}", creatorId, amount);

        // Try group-based goal first
        Optional<TipGoalGroup> activeGroup = groupRepository.findFirstByCreatorIdAndActiveTrueOrderByOrderIndexAsc(creatorId);
        if (activeGroup.isPresent()) {
            processGroupTip(creatorId, activeGroup.get(), amount);
            return;
        }

        // Fallback to standalone goal logic
        getActiveGoal(creatorId).ifPresent(goal -> {
            tipGoalRepository.incrementCurrentAmount(goal.getId(), amount);

            // Refetch to get updated amount (or we could just assume atomic increment succeeded)
            TipGoal updated = tipGoalRepository.findById(goal.getId()).orElse(goal);
            long current = updated.getCurrentAmount();
            long target = updated.getTargetAmount();
            int percentage = (int) Math.min(100, (current * 100) / target);
            boolean reached = current >= target;

            GoalStatusEventDto progress = GoalStatusEventDto.builder()
                    .type("GOAL_PROGRESS")
                    .title(updated.getTitle())
                    .targetAmount(target)
                    .currentAmount(current)
                    .percentage(percentage)
                    .isCompleted(reached)
                    .active(updated.isActive())
                    .build();

            long now = System.currentTimeMillis();
            Long last = lastGoalBroadcast.get(creatorId);

            if (last == null || (now - last) > GOAL_BROADCAST_INTERVAL_MS) {
                messagingTemplate.convertAndSend("/exchange/amq.topic/chat." + creatorId, progress);
                // Broadcast to dedicated goals stream
                messagingTemplate.convertAndSend("/exchange/amq.topic/goals." + creatorId,
                        LiveEvent.of("GOAL_PROGRESS", progress));
                lastGoalBroadcast.put(creatorId, now);
            }

            // Bot announcement for goal milestones
            streamAssistantBotService.onGoalProgress(creatorId, updated.getTitle(), current, target, percentage);

            if (reached) {
                GoalStatusEventDto completed = GoalStatusEventDto.builder()
                        .type("GOAL_COMPLETED")
                        .title(updated.getTitle())
                        .targetAmount(target)
                        .currentAmount(current)
                        .percentage(100)
                        .isCompleted(true)
                        .active(updated.isActive())
                        .build();
                messagingTemplate.convertAndSend("/exchange/amq.topic/chat." + creatorId, completed);
                // Broadcast to dedicated goals stream
                messagingTemplate.convertAndSend("/exchange/amq.topic/goals." + creatorId,
                        LiveEvent.of("GOAL_COMPLETED", completed));

                // Bot celebration announcement
                streamAssistantBotService.onGoalCompleted(creatorId, updated.getTitle());

                if (updated.isAutoReset()) {
                    updated.setCurrentAmount(0L);
                    tipGoalRepository.save(updated);
                } else if (updated.getOrderIndex() != null) {
                    // mark current goal active = false
                    updated.setActive(false);
                    tipGoalRepository.save(updated);

                    // find next goal by orderIndex ASC
                    tipGoalRepository.findFirstByCreatorIdAndOrderIndexGreaterThanOrderByOrderIndexAsc(creatorId, updated.getOrderIndex())
                            .ifPresent(nextGoal -> {
                                // set next goal active = true
                                nextGoal.setActive(true);
                                // reset currentAmount of new goal to 0
                                nextGoal.setCurrentAmount(0L);
                                TipGoal savedNext = tipGoalRepository.save(nextGoal);

                                // broadcast GOAL_SWITCH event
                                GoalStatusEventDto switchEvent = GoalStatusEventDto.builder()
                                        .type("GOAL_SWITCH")
                                        .title(savedNext.getTitle())
                                        .targetAmount(savedNext.getTargetAmount())
                                        .currentAmount(0L)
                                        .percentage(0)
                                        .isCompleted(false)
                                        .active(true)
                                        .build();
                                messagingTemplate.convertAndSend("/exchange/amq.topic/chat." + creatorId, switchEvent);
                                // Broadcast to dedicated goals stream
                                messagingTemplate.convertAndSend("/exchange/amq.topic/goals." + creatorId,
                                        LiveEvent.of("GOAL_SWITCH", switchEvent));
                            });
                }
            }
        });
    }

    private void processGroupTip(Long creatorId, TipGoalGroup group, long amount) {
        groupRepository.incrementCurrentAmount(group.getId(), amount);

        TipGoalGroup updated = groupRepository.findById(group.getId()).orElse(group);
        long current = updated.getCurrentAmount();
        long target = updated.getTargetAmount();
        int percentage = (int) Math.min(100, (current * 100) / target);
        boolean reached = current >= target;

        // Build milestone status list
        List<TipGoal> milestones = tipGoalRepository.findAllByGroupIdOrderByTargetAmountAsc(group.getId());
        List<GoalStatusEventDto.MilestoneStatusDto> milestoneStatuses = milestones.stream()
                .map(m -> GoalStatusEventDto.MilestoneStatusDto.builder()
                        .title(m.getTitle())
                        .targetAmount(m.getTargetAmount())
                        .reached(current >= m.getTargetAmount())
                        .build())
                .collect(Collectors.toList());

        // Detect newly reached milestones
        long previousAmount = current - amount;
        for (TipGoal milestone : milestones) {
            if (previousAmount < milestone.getTargetAmount() && current >= milestone.getTargetAmount()) {
                // Milestone just reached
                GoalStatusEventDto milestoneEvent = GoalStatusEventDto.builder()
                        .type("MILESTONE_REACHED")
                        .title(milestone.getTitle())
                        .targetAmount(target)
                        .currentAmount(current)
                        .percentage(percentage)
                        .isCompleted(false)
                        .active(true)
                        .milestones(milestoneStatuses)
                        .build();
                messagingTemplate.convertAndSend("/exchange/amq.topic/chat." + creatorId, milestoneEvent);
                messagingTemplate.convertAndSend("/exchange/amq.topic/goals." + creatorId,
                        LiveEvent.of("MILESTONE_REACHED", milestoneEvent));

                streamAssistantBotService.onMilestoneReached(creatorId, milestone.getTitle());
            }
        }

        // Check if close to next milestone
        for (TipGoal milestone : milestones) {
            if (current < milestone.getTargetAmount()) {
                long remaining = milestone.getTargetAmount() - current;
                if (remaining <= 100 && remaining > 0) {
                    streamAssistantBotService.onMilestoneAlmostReached(creatorId, milestone.getTitle(), remaining);
                }
                break;
            }
        }

        // Broadcast group progress (throttled)
        GoalStatusEventDto progress = GoalStatusEventDto.builder()
                .type("GOAL_PROGRESS")
                .title(updated.getTitle())
                .targetAmount(target)
                .currentAmount(current)
                .percentage(percentage)
                .isCompleted(reached)
                .active(updated.isActive())
                .milestones(milestoneStatuses)
                .build();

        long now = System.currentTimeMillis();
        Long last = lastGoalBroadcast.get(creatorId);
        if (last == null || (now - last) > GOAL_BROADCAST_INTERVAL_MS) {
            messagingTemplate.convertAndSend("/exchange/amq.topic/chat." + creatorId, progress);
            messagingTemplate.convertAndSend("/exchange/amq.topic/goals." + creatorId,
                    LiveEvent.of("GOAL_PROGRESS", progress));
            lastGoalBroadcast.put(creatorId, now);
        }

        streamAssistantBotService.onGoalProgress(creatorId, updated.getTitle(), current, target, percentage);

        if (reached) {
            GoalStatusEventDto completed = GoalStatusEventDto.builder()
                    .type("GOAL_COMPLETED")
                    .title(updated.getTitle())
                    .targetAmount(target)
                    .currentAmount(current)
                    .percentage(100)
                    .isCompleted(true)
                    .active(updated.isActive())
                    .milestones(milestoneStatuses)
                    .build();
            messagingTemplate.convertAndSend("/exchange/amq.topic/chat." + creatorId, completed);
            messagingTemplate.convertAndSend("/exchange/amq.topic/goals." + creatorId,
                    LiveEvent.of("GOAL_COMPLETED", completed));

            streamAssistantBotService.onGoalCompleted(creatorId, updated.getTitle());

            if (updated.isAutoReset()) {
                updated.setCurrentAmount(0L);
                groupRepository.save(updated);
            } else {
                updated.setActive(false);
                groupRepository.save(updated);

                groupRepository.findFirstByCreatorIdAndOrderIndexGreaterThanOrderByOrderIndexAsc(creatorId, updated.getOrderIndex())
                        .ifPresent(nextGroup -> {
                            nextGroup.setActive(true);
                            nextGroup.setCurrentAmount(0L);
                            TipGoalGroup savedNext = groupRepository.save(nextGroup);

                            List<GoalStatusEventDto.MilestoneStatusDto> nextMilestones = tipGoalRepository
                                    .findAllByGroupIdOrderByTargetAmountAsc(savedNext.getId()).stream()
                                    .map(m -> GoalStatusEventDto.MilestoneStatusDto.builder()
                                            .title(m.getTitle())
                                            .targetAmount(m.getTargetAmount())
                                            .reached(false)
                                            .build())
                                    .collect(Collectors.toList());

                            GoalStatusEventDto switchEvent = GoalStatusEventDto.builder()
                                    .type("GOAL_SWITCH")
                                    .title(savedNext.getTitle())
                                    .targetAmount(savedNext.getTargetAmount())
                                    .currentAmount(0L)
                                    .percentage(0)
                                    .isCompleted(false)
                                    .active(true)
                                    .milestones(nextMilestones)
                                    .build();
                            messagingTemplate.convertAndSend("/exchange/amq.topic/chat." + creatorId, switchEvent);
                            messagingTemplate.convertAndSend("/exchange/amq.topic/goals." + creatorId,
                                    LiveEvent.of("GOAL_SWITCH", switchEvent));
                        });
            }
        }
    }
}
