package com.joinlivora.backend.payout.dto;

import com.joinlivora.backend.payout.PayoutRequestStatus;
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
public class PayoutRequestResponseDTO {
    private UUID id;
    private BigDecimal amount;
    private String currency;
    private PayoutRequestStatus status;
    private Instant requestedAt;
    private Instant processedAt;
    private String rejectionReason;
}
