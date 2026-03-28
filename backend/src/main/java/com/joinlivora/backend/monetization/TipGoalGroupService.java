package com.joinlivora.backend.monetization;

import com.joinlivora.backend.monetization.dto.MilestoneDto;
import com.joinlivora.backend.monetization.dto.TipGoalGroupDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TipGoalGroupService {

    private final TipGoalGroupRepository groupRepository;
    private final TipGoalRepository goalRepository;
    private final TipGoalService tipGoalService;

    private static final int MAX_GROUPS_PER_CREATOR = 10;
    private static final int MAX_MILESTONES_PER_GROUP = 15;

    @Transactional(readOnly = true)
    public List<TipGoalGroupDto> getGroups(Long creatorId) {
        return groupRepository.findAllByCreatorIdOrderByOrderIndexAsc(creatorId)
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<TipGoalGroup> getActiveGroup(Long creatorId) {
        return groupRepository.findFirstByCreatorIdAndActiveTrueOrderByOrderIndexAsc(creatorId);
    }

    @Transactional
    public TipGoalGroupDto createGroup(Long creatorId, TipGoalGroupDto dto) {
        long count = groupRepository.countByCreatorId(creatorId);
        if (count >= MAX_GROUPS_PER_CREATOR) {
            throw new RuntimeException("Maximum " + MAX_GROUPS_PER_CREATOR + " goal groups allowed per creator");
        }

        if (dto.isActive()) {
            deactivateActiveGroups(creatorId);
        }

        TipGoalGroup group = TipGoalGroup.builder()
                .creatorId(creatorId)
                .title(dto.getTitle())
                .targetAmount(dto.getTargetAmount())
                .currentAmount(0L)
                .active(dto.isActive())
                .autoReset(dto.isAutoReset())
                .orderIndex(dto.getOrderIndex() != null ? dto.getOrderIndex() : (int) count)
                .build();

        return mapToDto(groupRepository.save(group));
    }

    @Transactional
    public TipGoalGroupDto updateGroup(UUID id, Long creatorId, TipGoalGroupDto dto) {
        TipGoalGroup group = groupRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Goal group not found"));

        if (!group.getCreatorId().equals(creatorId)) {
            throw new RuntimeException("Unauthorized: This goal group belongs to another creator");
        }

        if (dto.isActive() && !group.isActive()) {
            deactivateActiveGroups(creatorId);
        }

        group.setTitle(dto.getTitle());
        group.setTargetAmount(dto.getTargetAmount());
        group.setAutoReset(dto.isAutoReset());
        group.setActive(dto.isActive());
        if (dto.getOrderIndex() != null) {
            group.setOrderIndex(dto.getOrderIndex());
        }

        return mapToDto(groupRepository.save(group));
    }

    @Transactional
    public void deleteGroup(UUID id, Long creatorId) {
        TipGoalGroup group = groupRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Goal group not found"));

        if (!group.getCreatorId().equals(creatorId)) {
            throw new RuntimeException("Unauthorized: This goal group belongs to another creator");
        }

        // Unlink milestones before deleting group
        List<TipGoal> milestones = goalRepository.findAllByGroupIdOrderByTargetAmountAsc(group.getId());
        for (TipGoal milestone : milestones) {
            milestone.setGroup(null);
            goalRepository.save(milestone);
        }

        groupRepository.delete(group);
    }

    @Transactional
    public TipGoalGroupDto addMilestone(UUID groupId, Long creatorId, String title, Long targetAmount) {
        TipGoalGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Goal group not found"));

        if (!group.getCreatorId().equals(creatorId)) {
            throw new RuntimeException("Unauthorized: This goal group belongs to another creator");
        }

        long milestoneCount = goalRepository.findAllByGroupIdOrderByTargetAmountAsc(groupId).size();
        if (milestoneCount >= MAX_MILESTONES_PER_GROUP) {
            throw new RuntimeException("Maximum " + MAX_MILESTONES_PER_GROUP + " milestones allowed per group");
        }

        TipGoal milestone = TipGoal.builder()
                .creatorId(creatorId)
                .group(group)
                .title(title)
                .targetAmount(targetAmount)
                .currentAmount(0L)
                .active(false)
                .autoReset(false)
                .orderIndex(null)
                .build();

        goalRepository.save(milestone);
        return mapToDto(groupRepository.findById(groupId).orElse(group));
    }

    @Transactional
    public void deleteMilestone(UUID milestoneId, Long creatorId) {
        TipGoal milestone = goalRepository.findById(milestoneId)
                .orElseThrow(() -> new RuntimeException("Milestone not found"));

        if (!milestone.getCreatorId().equals(creatorId)) {
            throw new RuntimeException("Unauthorized");
        }

        goalRepository.delete(milestone);
    }

    @Transactional
    public void resetGroup(UUID groupId, Long creatorId) {
        TipGoalGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Goal group not found"));

        if (!group.getCreatorId().equals(creatorId)) {
            throw new RuntimeException("Unauthorized: This goal group belongs to another creator");
        }

        group.setCurrentAmount(0L);
        groupRepository.save(group);

        // broadcast update to viewers
        tipGoalService.broadcastActiveGoalGroup(group.getCreatorId());
    }

    private void deactivateActiveGroups(Long creatorId) {
        groupRepository.findAllByCreatorIdOrderByOrderIndexAsc(creatorId)
                .stream()
                .filter(TipGoalGroup::isActive)
                .forEach(g -> {
                    g.setActive(false);
                    groupRepository.save(g);
                });
    }

    public TipGoalGroupDto mapToDto(TipGoalGroup group) {
        List<TipGoal> milestones = goalRepository.findAllByGroupIdOrderByTargetAmountAsc(group.getId());
        return TipGoalGroupDto.builder()
                .id(group.getId())
                .title(group.getTitle())
                .targetAmount(group.getTargetAmount())
                .currentAmount(group.getCurrentAmount())
                .active(group.isActive())
                .autoReset(group.isAutoReset())
                .orderIndex(group.getOrderIndex())
                .createdAt(group.getCreatedAt())
                .milestones(milestones.stream().map(m -> MilestoneDto.builder()
                        .id(m.getId())
                        .title(m.getTitle())
                        .targetAmount(m.getTargetAmount())
                        .reached(group.getCurrentAmount() >= m.getTargetAmount())
                        .build()).collect(Collectors.toList()))
                .build();
    }
}
