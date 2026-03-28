package com.joinlivora.backend.privateshow;

import java.time.Instant;
import java.util.UUID;

public record PrivateSpySessionDto(
        UUID id,
        UUID privateSessionId,
        Long spyViewerId,
        long spyPricePerMinute,
        SpySessionStatus status,
        Instant startedAt,
        Instant endedAt
) {}
