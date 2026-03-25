package com.joinlivora.backend.payout.dto;

import com.joinlivora.backend.payout.HoldLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayoutHoldDecision {
    private HoldLevel holdLevel;
    private int holdDays;
    private String reason;
}
