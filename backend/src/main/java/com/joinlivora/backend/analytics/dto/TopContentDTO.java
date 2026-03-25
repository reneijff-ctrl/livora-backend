package com.joinlivora.backend.analytics.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record TopContentDTO(
        List<ContentRevenueDTO> topPpvContent,
        List<ContentRevenueDTO> topLiveStreams
) {
    public record ContentRevenueDTO(
            UUID id,
            String title,
            BigDecimal revenue
    ) {}
}
