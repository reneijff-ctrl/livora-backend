package com.joinlivora.backend.payout.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class CreatorPayoutResponseDTO {
    private UUID id;
    private BigDecimal amount;
    private String currency;
    private String status;
    private Instant createdAt;
    private Instant completedAt;
    private String failureReason;
}
