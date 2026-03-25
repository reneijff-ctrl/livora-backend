package com.joinlivora.backend.monetization.dto;

import com.joinlivora.backend.monetization.HighlightLevel;
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
public class SuperTipResponse {
    private boolean success;
    private UUID id;
    private Long senderId;
    private String senderUsername;
    private String senderEmail;
    private String creatorEmail;
    private UUID roomId;
    private BigDecimal amount;
    private String message;
    private HighlightLevel highlightLevel;
    private int durationSeconds;
    private Instant createdAt;
    private SuperTipErrorCode errorCode;
}
