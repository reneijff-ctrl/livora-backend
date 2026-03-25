package com.joinlivora.backend.payout.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PayoutLimitRequest {
    private BigDecimal maxPayoutAmount;
    private PayoutFrequency frequency;
}
