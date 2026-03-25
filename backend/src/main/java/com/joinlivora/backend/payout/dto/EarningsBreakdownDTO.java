package com.joinlivora.backend.payout.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EarningsBreakdownDTO {
    private SummaryDTO totalEarnings;
    private SummaryDTO availableEarnings;
    private SummaryDTO lockedEarnings;
    private LockedByDTO lockedBy;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SummaryDTO {
        private long count;
        private BigDecimal sum;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LockedByDTO {
        private SummaryDTO payoutHold;
        private SummaryDTO fraudHold;
        private SummaryDTO payoutRequested;
        private SummaryDTO manualAdminLock;
    }
}
