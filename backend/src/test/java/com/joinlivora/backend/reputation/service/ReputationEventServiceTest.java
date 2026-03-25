package com.joinlivora.backend.reputation.service;

import com.joinlivora.backend.reputation.model.*;
import com.joinlivora.backend.reputation.repository.CreatorReputationSnapshotRepository;
import com.joinlivora.backend.reputation.repository.ReputationEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReputationEventServiceTest {

    @Mock
    private ReputationEventRepository eventRepository;
    @Mock
    private CreatorReputationSnapshotRepository snapshotRepository;
    @Mock
    private ReputationCalculationService calculationService;
    @Mock
    private ReputationAuditService auditService;

    @InjectMocks
    private ReputationEventService eventService;

    @Test
    void recordEvent_ShouldLogChangeWhenScoreChanges() {
        // Given
        UUID creatorId = UUID.randomUUID();
        CreatorReputationSnapshot snapshot = CreatorReputationSnapshot.builder()
                .creatorId(creatorId)
                .currentScore(50)
                .status(ReputationStatus.NORMAL)
                .build();

        when(snapshotRepository.findById(creatorId)).thenReturn(Optional.of(snapshot));
        
        // Simulate score change from 50 to 60
        doAnswer(invocation -> {
            CreatorReputationSnapshot s = invocation.getArgument(0);
            s.setCurrentScore(60);
            return s;
        }).when(calculationService).applyEvent(eq(snapshot), any(ReputationEvent.class));

        // When
        eventService.recordEvent(creatorId, ReputationEventType.TIP, 10, ReputationEventSource.SYSTEM, Map.of());

        // Then
        verify(eventRepository).save(any(ReputationEvent.class));
        verify(snapshotRepository).save(snapshot);
        verify(auditService).logChange(eq(creatorId), eq(50), eq(60), eq("TIP"), eq(ReputationEventSource.SYSTEM));
    }

    @Test
    void recordEvent_ShouldNotLogChangeWhenScoreIsSame() {
        // Given
        UUID creatorId = UUID.randomUUID();
        CreatorReputationSnapshot snapshot = CreatorReputationSnapshot.builder()
                .creatorId(creatorId)
                .currentScore(100)
                .status(ReputationStatus.TRUSTED)
                .build();

        when(snapshotRepository.findById(creatorId)).thenReturn(Optional.of(snapshot));
        
        // Simulate no score change (already at max)
        doAnswer(invocation -> invocation.getArgument(0))
                .when(calculationService).applyEvent(eq(snapshot), any(ReputationEvent.class));

        // When
        eventService.recordEvent(creatorId, ReputationEventType.TIP, 10, ReputationEventSource.SYSTEM, Map.of());

        // Then
        verify(auditService, never()).logChange(any(), anyInt(), anyInt(), any(), any());
    }
}








