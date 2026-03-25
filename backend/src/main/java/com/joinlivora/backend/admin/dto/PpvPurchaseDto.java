package com.joinlivora.backend.admin.dto;

import com.joinlivora.backend.monetization.PpvPurchaseStatus;
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
public class PpvPurchaseDto {
    private UUID id;
    private Long userId;
    private String username;
    private UUID ppvContentId;
    private String ppvContentTitle;
    private BigDecimal amount;
    private PpvPurchaseStatus status;
    private Instant purchasedAt;
}
