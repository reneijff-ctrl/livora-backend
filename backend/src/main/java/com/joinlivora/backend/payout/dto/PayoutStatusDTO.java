package com.joinlivora.backend.payout.dto;

import com.joinlivora.backend.user.FraudRiskLevel;
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
public class PayoutStatusDTO {
    private boolean payoutsEnabled;
    private boolean hasActivePayoutHold;
    private FraudRiskLevel fraudRiskLevel;
    private BigDecimal availableBalance;
    private Instant nextPayoutDate;
}
