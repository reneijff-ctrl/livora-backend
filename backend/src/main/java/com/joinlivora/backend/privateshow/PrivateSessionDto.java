package com.joinlivora.backend.privateshow;

import java.time.Instant;
import java.util.UUID;

public record PrivateSessionDto(
        UUID id,
        Long viewerId,
        String viewerUsername,
        Long creatorId,
        String creatorUsername,
        PrivateSessionStatus status,
        long pricePerMinute,
        Instant startedAt,
        Instant endedAt
) {}
