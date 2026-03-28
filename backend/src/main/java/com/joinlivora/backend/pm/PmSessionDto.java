package com.joinlivora.backend.pm;

import java.time.Instant;

public record PmSessionDto(
        Long roomId,
        Long creatorId,
        String creatorUsername,
        Long viewerId,
        String viewerUsername,
        Instant createdAt,
        Integer unreadCount,
        String lastMessage,
        Instant lastMessageTime
) {}
