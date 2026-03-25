package com.joinlivora.backend.payout.dto;

import com.joinlivora.backend.payout.HoldLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayoutHoldStatusDTO {
    private HoldLevel holdLevel;
    private Instant unlockDate;
    private String reason;
}
