package com.joinlivora.backend.payout.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
public class CreatorEarningsSummary {
    @JsonProperty("totalEarned")
    private BigDecimal totalEarned;
    
    @JsonProperty("availableBalance")
    private BigDecimal availableBalance;
    
    @JsonProperty("pendingBalance")
    private BigDecimal pendingBalance;
    
    private BigDecimal monthEarnings;
    private Instant lastPayoutDate;

    // Frontend compatibility aliases
    @JsonProperty("totalEarnings")
    public BigDecimal getTotalEarnings() {
        return totalEarned;
    }

    @JsonProperty("availableEarnings")
    public BigDecimal getAvailableEarnings() {
        return availableBalance;
    }

    @JsonProperty("pendingEarnings")
    public BigDecimal getPendingEarnings() {
        return pendingBalance;
    }
}
