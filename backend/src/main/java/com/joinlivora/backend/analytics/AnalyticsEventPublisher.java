package com.joinlivora.backend.analytics;

import com.joinlivora.backend.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class AnalyticsEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    public void publishEvent(AnalyticsEventType type, User user, String funnelId, Map<String, Object> metadata) {
        applicationEventPublisher.publishEvent(new DomainAnalyticsEvent(this, type, user, funnelId, metadata));
    }

    public void publishEvent(AnalyticsEventType type, User user, Map<String, Object> metadata) {
        publishEvent(type, user, null, metadata);
    }
}
