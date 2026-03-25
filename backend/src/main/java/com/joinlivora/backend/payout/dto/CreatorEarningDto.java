package com.joinlivora.backend.payout.dto;

import com.joinlivora.backend.payout.EarningSource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatorEarningDto {
    private UUID id;
    private BigDecimal grossAmount;
    private BigDecimal platformFee;
    private BigDecimal netAmount;
    private String currency;
    private EarningSource sourceType;
    private String stripeChargeId;
    private boolean locked;
    private Instant createdAt;
    private String status; // Combined from locked and other fields if needed
    private String supporterName;
}
