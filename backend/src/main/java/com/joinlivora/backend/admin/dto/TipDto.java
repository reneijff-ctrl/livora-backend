package com.joinlivora.backend.admin.dto;

import com.joinlivora.backend.monetization.TipStatus;
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
public class TipDto {
    private UUID id;
    private Long senderId;
    private String senderUsername;
    private Long creatorId;
    private String creatorUsername;
    private BigDecimal amount;
    private String currency;
    private String message;
    private TipStatus status;
    private Instant createdAt;
}
