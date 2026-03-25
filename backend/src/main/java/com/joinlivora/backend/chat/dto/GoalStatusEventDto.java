package com.joinlivora.backend.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoalStatusEventDto {
    @Builder.Default
    private String type = "GOAL_PROGRESS";
    private String title;
    private Long targetAmount;
    private Long currentAmount;
    private Integer percentage;
    private boolean isCompleted;
    private boolean active;
    private List<MilestoneStatusDto> milestones;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MilestoneStatusDto {
        private String title;
        private Long targetAmount;
        private boolean reached;
    }
}
