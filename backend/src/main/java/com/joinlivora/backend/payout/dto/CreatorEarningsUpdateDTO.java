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
public class CreatorEarningsUpdateDTO {
    private String type; // TIP, PPV, SUBSCRIPTION, HIGHLIGHTED_CHAT
    private BigDecimal amount;
    private String currency;
    private CreatorEarningsDTO currentAggregatedEarnings;
}
