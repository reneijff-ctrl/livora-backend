package com.joinlivora.backend.reputation.service;

import com.joinlivora.backend.reputation.model.ReputationChangeLog;
import com.joinlivora.backend.reputation.model.ReputationEventSource;
import com.joinlivora.backend.reputation.repository.ReputationChangeLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReputationAuditServiceTest {

    @Mock
    private ReputationChangeLogRepository repository;

    @InjectMocks
    private ReputationAuditService auditService;

    @Test
    void logChange_ShouldSaveLogEntry() {
        // Given
        UUID creatorId = UUID.randomUUID();
        int oldScore = 50;
        int newScore = 60;
        String reason = "TIP";
        ReputationEventSource source = ReputationEventSource.SYSTEM;

        // When
        auditService.logChange(creatorId, oldScore, newScore, reason, source);

        // Then
        ArgumentCaptor<ReputationChangeLog> captor = ArgumentCaptor.forClass(ReputationChangeLog.class);
        verify(repository).save(captor.capture());
        
        ReputationChangeLog saved = captor.getValue();
        assertEquals(creatorId, saved.getCreatorId());
        assertEquals(oldScore, saved.getOldScore());
        assertEquals(newScore, saved.getNewScore());
        assertEquals(reason, saved.getReason());
        assertEquals(source, saved.getSource());
    }
}








