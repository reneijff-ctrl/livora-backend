package com.joinlivora.backend.livestream.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Stream-started event — carries the unified Stream (UUID-based) identity.
 * This is the canonical stream-start event. The legacy {@code StreamStartedEvent} and
 * {@code LivestreamSession} have been fully removed.
 */
@Getter
public class StreamStartedEventV2 extends ApplicationEvent {

    private final UUID streamId;
    private final Long creatorUserId;
    private final boolean isPaid;
    private final BigDecimal admissionPrice;
    private final Instant startedAt;

    public StreamStartedEventV2(Object source, UUID streamId, Long creatorUserId,
                                boolean isPaid, BigDecimal admissionPrice, Instant startedAt) {
        super(source);
        this.streamId = streamId;
        this.creatorUserId = creatorUserId;
        this.isPaid = isPaid;
        this.admissionPrice = admissionPrice;
        this.startedAt = startedAt;
    }
}
