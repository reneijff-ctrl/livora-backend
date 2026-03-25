package com.joinlivora.backend.analytics.dto;

import java.math.BigDecimal;

public record CreatorEarningsBreakdownDTO(
        BigDecimal subscriptions,
        BigDecimal ppv,
        BigDecimal tips,
        BigDecimal liveStream
) {
}
