package com.joinlivora.backend.payout.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatorEarningsResponseDTO {
    private UUID id;
    private BigDecimal availableBalance;
    private BigDecimal pendingBalance;
    private BigDecimal totalEarned;
    private boolean payoutsDisabled;
}
