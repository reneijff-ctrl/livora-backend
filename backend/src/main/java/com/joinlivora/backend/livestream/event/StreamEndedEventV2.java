package com.joinlivora.backend.livestream.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.Instant;
import java.util.UUID;

/**
 * Stream-ended event — carries the unified Stream (UUID-based) identity.
 * This is the canonical stream-end event. The legacy {@code StreamEndedEvent} and
 * {@code LivestreamSession} have been fully removed.
 */
@Getter
public class StreamEndedEventV2 extends ApplicationEvent {

    private final UUID streamId;
    private final Long creatorUserId;
    private final boolean isPaid;
    private final String reason;
    private final Instant endedAt;

    public StreamEndedEventV2(Object source, UUID streamId, Long creatorUserId,
                              boolean isPaid, String reason, Instant endedAt) {
        super(source);
        this.streamId = streamId;
        this.creatorUserId = creatorUserId;
        this.isPaid = isPaid;
        this.reason = reason;
        this.endedAt = endedAt;
    }
}
