package com.joinlivora.backend.analytics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {

    private final AnalyticsEventRepository analyticsEventRepository;

    @Async
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleAnalyticsEvent(DomainAnalyticsEvent event) {
        log.debug("Processing analytics event: {}", event.getEventType());
        try {
            AnalyticsEvent entity = new AnalyticsEvent();
            entity.setEventType(event.getEventType());
            entity.setUser(event.getUser());
            entity.setFunnelId(event.getFunnelId());
            entity.setMetadata(event.getMetadata());
            
            analyticsEventRepository.save(entity);
        } catch (Exception e) {
            log.error("Failed to persist analytics event: {}", event.getEventType(), e);
        }
    }

    @org.springframework.scheduling.annotation.Scheduled(cron = "0 0 2 * * *") // Every day at 2 AM
    @Transactional
    public void cleanupOldAnalytics() {
        java.time.Instant cutoff = java.time.Instant.now().minus(90, java.time.temporal.ChronoUnit.DAYS);
        log.info("Starting analytics cleanup for events older than {}", cutoff);
        analyticsEventRepository.deleteOlderThan(cutoff);
        log.info("Analytics cleanup completed.");
    }
}
