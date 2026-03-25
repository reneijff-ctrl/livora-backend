package com.joinlivora.backend.payout.dto;

import com.joinlivora.backend.payout.PayoutStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class AdminPayoutDetailDTO {
    private UUID id;
    private UUID creatorId;
    private String creatorEmail;
    private BigDecimal amount;
    private String currency;
    private PayoutStatus status;
    private String stripeTransferId;
    private Instant completedAt;
    private String failureReason;
    private Instant createdAt;
    
    private List<EarningDetailDTO> earnings;
    private List<PayoutHoldDetailDTO> holds;
    private List<PayoutAuditLogDTO> auditLogs;

    @Data
    @Builder
    public static class EarningDetailDTO {
        private UUID id;
        private BigDecimal netAmount;
        private String currency;
        private String sourceType;
        private Instant createdAt;
    }

    @Data
    @Builder
    public static class PayoutHoldDetailDTO {
        private UUID id;
        private String reason;
        private String status;
        private Instant createdAt;
    }

    @Data
    @Builder
    public static class PayoutAuditLogDTO {
        private UUID id;
        private String actorType;
        private UUID actorId;
        private String action;
        private String previousStatus;
        private String newStatus;
        private String message;
        private Instant createdAt;
    }
}
