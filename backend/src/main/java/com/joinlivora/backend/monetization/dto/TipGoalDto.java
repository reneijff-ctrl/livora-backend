package com.joinlivora.backend.monetization.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TipGoalDto {
    private UUID id;
    private String title;
    private Long targetAmount;
    private Long currentAmount;
    private boolean active;
    private boolean autoReset;
    private Integer orderIndex;
    private Instant createdAt;
    private Instant updatedAt;
}
