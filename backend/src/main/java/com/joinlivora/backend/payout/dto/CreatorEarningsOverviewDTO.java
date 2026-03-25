package com.joinlivora.backend.payout.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatorEarningsOverviewDTO {
    private BigDecimal totalEarnings;
    private BigDecimal availableBalance;
    private BigDecimal pendingBalance;
    private List<CreatorEarningDto> lastEarnings;
}
