package com.joinlivora.backend.analytics.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record LeaderboardResponseDto(
    int rank,
    UUID creatorId,
    BigDecimal totalEarnings,
    long totalViewers,
    String category
) {}
