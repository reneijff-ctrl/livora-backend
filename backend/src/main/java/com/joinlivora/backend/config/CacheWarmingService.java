package com.joinlivora.backend.config;

import com.joinlivora.backend.creator.service.CreatorSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CacheWarmingService {

    private final CreatorSearchService creatorSearchService;

    @EventListener(ApplicationReadyEvent.class)
    public void warmCaches() {
        log.info("LIVORA: Starting cache warming...");
        try {
            creatorSearchService.getPublicCreatorsForHomepage();
            log.info("LIVORA: Homepage cache warmed.");
        } catch (Exception e) {
            log.warn("LIVORA: Failed to warm homepage cache: {}", e.getMessage());
        }
    }
}
