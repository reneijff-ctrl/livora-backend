package com.joinlivora.backend.payout.dto;

import com.joinlivora.backend.payout.EarningSource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatorEarningsReportDTO {
    private PeriodStats daily;
    private PeriodStats weekly;
    private PeriodStats monthly;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PeriodStats {
        private BigDecimal totalEarnings; // Non-token revenue (EUR)
        private long totalTokens;
        private Map<EarningSource, BigDecimal> revenueBySource;
        private Map<EarningSource, Long> tokensBySource;
    }
}
