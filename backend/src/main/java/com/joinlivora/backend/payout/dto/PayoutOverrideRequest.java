package com.joinlivora.backend.payout.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayoutOverrideRequest {
    private boolean releaseHold;
    private boolean forcePayout;
    private boolean relockEarnings;
    private String adminNote;
}
