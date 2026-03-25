package com.joinlivora.backend.monetization.dto;

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
public class TipGraph {
    private UUID tipperUserId;
    private UUID creatorUserId;
    private BigDecimal totalAmount;
    private long tipCount;
    private Instant firstTipAt;
    private Instant lastTipAt;
}
