package com.joinlivora.backend.privateshow;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PrivateShowScheduler {

    private final PrivateSessionService sessionService;

    @Scheduled(fixedRate = 60000) // Every 1 minute
    public void processBilling() {
        log.debug("Scheduler: Processing private show billing...");
        sessionService.processBilling();
    }

    @Scheduled(fixedRate = 60000) // Every 1 minute
    public void processSpyBilling() {
        log.debug("Scheduler: Processing spy session billing...");
        sessionService.processSpyBilling();
    }

    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void expireStaleAcceptedSessions() {
        log.debug("Scheduler: Checking for stale ACCEPTED sessions...");
        sessionService.expireStaleAcceptedSessions();
    }

    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void cleanupDuplicateActiveSessions() {
        log.debug("Scheduler: Cleaning up duplicate ACTIVE sessions...");
        sessionService.cleanupDuplicateActiveSessions();
    }
}
