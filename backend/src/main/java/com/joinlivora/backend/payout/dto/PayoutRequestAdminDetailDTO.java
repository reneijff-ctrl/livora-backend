package com.joinlivora.backend.payout.dto;

import com.joinlivora.backend.payout.PayoutRequestStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayoutRequestAdminDetailDTO {
    private UUID id;
    private String creatorEmail;
    private BigDecimal amount;
    private String currency;
    private PayoutRequestStatus status;
    private Instant requestedAt;
    
    // Risk metrics
    private int fraudScore;
    private int trustScore;
    
    // Status details
    private List<PayoutHoldStatusDTO> payoutHolds;
    private boolean stripeReady;
    
    private String rejectionReason;
}
