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
public class PayoutLimit {
    private BigDecimal maxPayoutAmount;
    private PayoutFrequency payoutFrequency;
    private String reason;
}
