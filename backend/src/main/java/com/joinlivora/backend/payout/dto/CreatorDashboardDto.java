package com.joinlivora.backend.payout.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatorDashboardDto {
    private BigDecimal totalEarnings;
    private BigDecimal availableBalance;
    private long activeStreams;
    private long totalSubscribers;
    private long contentCount;
}
