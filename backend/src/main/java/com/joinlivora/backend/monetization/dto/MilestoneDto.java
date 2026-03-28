package com.joinlivora.backend.monetization.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MilestoneDto {
    private UUID id;
    private String title;
    private Long targetAmount;
    private boolean reached;
}
