package com.joinlivora.backend.tip.dto;

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
public class CreatorTipDto {
    private UUID id;
    private BigDecimal amount;
    private String fromUserId; // Masked
    private Instant createdAt;
}
