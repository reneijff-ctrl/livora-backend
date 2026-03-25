package com.joinlivora.backend.payout.freeze.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PayoutFreezeRequest {
    private Long creatorId;
    private String reason;
}
