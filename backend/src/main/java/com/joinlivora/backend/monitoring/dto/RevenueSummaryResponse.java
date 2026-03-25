package com.joinlivora.backend.monitoring.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class RevenueSummaryResponse {
    private BigDecimal totalRevenue;
    private long totalTipsCount;
    private BigDecimal totalTipsAmount;
    private long activeStreamsCount;
    private long fraudBlocksCount;
    private long activeSubscriptionsCount;
}
