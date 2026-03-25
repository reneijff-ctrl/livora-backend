package com.joinlivora.backend.payout.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AmlOverrideRequest {
    private int riskScore;
    private String reason;
}
