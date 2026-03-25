package com.joinlivora.backend.audit.scheduler;

import com.joinlivora.backend.audit.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditLogCleanupSchedulerTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditLogCleanupScheduler cleanupScheduler;

    @Test
    void cleanupOldAuditLogs_ShouldCallDeleteWithSevenYearsThreshold() {
        // Given
        Instant now = Instant.now();
        Instant expectedThresholdMin = now.minus(7 * 365 + 1, ChronoUnit.DAYS);
        Instant expectedThresholdMax = now.minus(7 * 365 - 1, ChronoUnit.DAYS);

        // When
        cleanupScheduler.cleanupOldAuditLogs();

        // Then
        ArgumentCaptor<Instant> thresholdCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(auditLogRepository).deleteAllByCreatedAtBefore(thresholdCaptor.capture());
        
        Instant actualThreshold = thresholdCaptor.getValue();
        assertTrue(actualThreshold.isAfter(expectedThresholdMin) && actualThreshold.isBefore(expectedThresholdMax),
                "Threshold should be approximately 7 years ago");
    }
}








