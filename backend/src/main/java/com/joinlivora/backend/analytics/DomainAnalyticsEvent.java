package com.joinlivora.backend.analytics;

import com.joinlivora.backend.user.User;
import org.springframework.context.ApplicationEvent;

import java.util.Map;

public class DomainAnalyticsEvent extends ApplicationEvent {
    private final AnalyticsEventType eventType;
    private final User user;
    private final String funnelId;
    private final Map<String, Object> metadata;

    public DomainAnalyticsEvent(Object source, AnalyticsEventType eventType, User user, String funnelId, Map<String, Object> metadata) {
        super(source);
        this.eventType = eventType;
        this.user = user;
        this.funnelId = funnelId;
        this.metadata = metadata;
    }

    public AnalyticsEventType getEventType() {
        return eventType;
    }

    public User getUser() {
        return user;
    }

    public String getFunnelId() {
        return funnelId;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }
}
