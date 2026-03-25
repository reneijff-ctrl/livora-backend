package com.joinlivora.backend.monetization.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class TipResult {
    private UUID tipId;
    private String senderEmail;
    private String creatorEmail;
    private BigDecimal amount;
    private String currency;
    private String message;
    private Instant timestamp;
    private String status;
    private boolean isDuplicate;
    private Long viewerBalance;
    private Long creatorBalance;
}
