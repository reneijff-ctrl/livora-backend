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
public class CreatorMonetizationResponse {
    private BigDecimal subscriptionPrice;
    private boolean tipEnabled;
    private BigDecimal balance;
    private BigDecimal pendingBalance;
    private BigDecimal lifetimeEarnings;
}
