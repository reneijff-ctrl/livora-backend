package com.joinlivora.backend.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreatorAnalyticsResponseDTO(
    LocalDate date,
    BigDecimal earnings,
    long viewers,
    long subscriptions,
    long returningViewers,
    long avgSessionDuration,
    double messagesPerViewer
) {}
