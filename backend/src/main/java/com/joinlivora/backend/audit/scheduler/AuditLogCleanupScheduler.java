package com.joinlivora.backend.audit.scheduler;

import com.joinlivora.backend.audit.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuditLogCleanupScheduler {

    private final AuditLogRepository auditLogRepository;

    /**
     * Retain audit logs for 7 years.
     * Runs daily at 04:00.
     */
    @Scheduled(cron = "0 0 4 * * *")
    @Transactional
    public void cleanupOldAuditLogs() {
        log.info("AUDIT_CLEANUP: Starting audit log retention job (7 years retention)");
        
        Instant threshold = Instant.now().minus(7 * 365, ChronoUnit.DAYS);
        
        try {
            auditLogRepository.deleteAllByCreatedAtBefore(threshold);
            log.info("AUDIT_CLEANUP: Finished audit log cleanup job. Logs older than {} have been removed.", threshold);
        } catch (Exception e) {
            log.error("AUDIT_CLEANUP: Failed to cleanup old audit logs", e);
        }
    }
}
