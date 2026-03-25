package com.joinlivora.backend.creator.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatorDashboardStatsDto {
    private BigDecimal totalEarnings;
    private Long totalFollowers;
    private boolean isVerified;
    private BigDecimal availableBalance;
    private Integer activeStreams;
    private Long contentCount;
    private String status;
}
