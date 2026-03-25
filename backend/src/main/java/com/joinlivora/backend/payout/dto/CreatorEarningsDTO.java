package com.joinlivora.backend.payout.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatorEarningsDTO {
    private long todayTokens;
    private BigDecimal todayRevenue;
    private long totalTokens;
    private BigDecimal totalRevenue;
    private BigDecimal pendingPayout;
    private Instant lastUpdated;
}
