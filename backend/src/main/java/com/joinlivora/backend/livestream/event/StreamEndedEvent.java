package com.joinlivora.backend.livestream.event;

import com.joinlivora.backend.livestream.domain.LivestreamSession;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

@Getter
public class StreamEndedEvent extends ApplicationEvent {
    private final LivestreamSession session;
    private final String reason;
    private final UUID streamId;

    public StreamEndedEvent(Object source, LivestreamSession session) {
        this(source, session, "unknown", null);
    }

    public StreamEndedEvent(Object source, LivestreamSession session, String reason, UUID streamId) {
        super(source);
        this.session = session;
        this.reason = reason;
        this.streamId = streamId;
    }
}
