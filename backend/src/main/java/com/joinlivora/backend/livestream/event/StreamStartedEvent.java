package com.joinlivora.backend.livestream.event;

import com.joinlivora.backend.livestream.domain.LivestreamSession;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class StreamStartedEvent extends ApplicationEvent {
    private final LivestreamSession session;

    public StreamStartedEvent(Object source, LivestreamSession session) {
        super(source);
        this.session = session;
    }
}
