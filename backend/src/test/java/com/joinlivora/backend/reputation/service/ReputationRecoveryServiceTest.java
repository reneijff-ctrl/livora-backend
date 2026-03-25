package com.joinlivora.backend.reputation.service;

import com.joinlivora.backend.reputation.model.*;
import com.joinlivora.backend.reputation.repository.CreatorReputationSnapshotRepository;
import com.joinlivora.backend.reputation.repository.ReputationEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReputationRecoveryServiceTest {

    @Mock
    private ReputationEventRepository eventRepository;
    @Mock
    private CreatorReputationSnapshotRepository snapshotRepository;
    @Mock
    private ReputationCalculationService calculationService;
    @Mock
    private ReputationAuditService auditService;

    @InjectMocks
    private ReputationRecoveryService recoveryService;

    private UUID creatorId;
    private CreatorReputationSnapshot snapshot;

    @BeforeEach
    void setUp() {
        creatorId = UUID.randomUUID();
        snapshot = CreatorReputationSnapshot.builder()
                .creatorId(creatorId)
                .currentScore(50)
                .status(ReputationStatus.NORMAL)
                .build();
    }

    @Test
    void processRecovery_Success() {
        // Given
        when(eventRepository.findFirstByCreatorIdAndTypeOrderByCreatedAtDesc(eq(creatorId), eq(ReputationEventType.RECOVERY)))
                .thenReturn(Optional.empty());
        when(eventRepository.existsByCreatorIdAndTypeInAndCreatedAtAfter(eq(creatorId), any(), any()))
                .thenReturn(false);
        when(eventRepository.countByCreatorIdAndTypeAndCreatedAtAfter(eq(creatorId), eq(ReputationEventType.TIP), any()))
                .thenReturn(5L);

        // When
        recoveryService.processRecovery(snapshot);

        // Then
        verify(eventRepository).save(any(ReputationEvent.class));
        verify(calculationService).applyEvent(eq(snapshot), any(ReputationEvent.class));
        verify(snapshotRepository).save(snapshot);
        verify(auditService).logChange(eq(creatorId), eq(50), anyInt(), eq("RECOVERY"), eq(ReputationEventSource.SYSTEM));
    }

    @Test
    void processRecovery_AlreadyAtMax_ShouldDoNothing() {
        // Given
        snapshot.setCurrentScore(100);

        // When
        recoveryService.processRecovery(snapshot);

        // Then
        verifyNoInteractions(eventRepository);
        verifyNoInteractions(calculationService);
        verifyNoInteractions(snapshotRepository);
    }

    @Test
    void processRecovery_RecentlyRecovered_ShouldDoNothing() {
        // Given
        ReputationEvent lastRecovery = ReputationEvent.builder()
                .createdAt(Instant.now().minus(3, ChronoUnit.DAYS))
                .build();
        when(eventRepository.findFirstByCreatorIdAndTypeOrderByCreatedAtDesc(eq(creatorId), eq(ReputationEventType.RECOVERY)))
                .thenReturn(Optional.of(lastRecovery));

        // When
        recoveryService.processRecovery(snapshot);

        // Then
        verify(eventRepository, never()).save(any());
        verifyNoInteractions(calculationService);
        verifyNoInteractions(snapshotRepository);
    }

    @Test
    void processRecovery_NegativeEvents_ShouldDoNothing() {
        // Given
        when(eventRepository.findFirstByCreatorIdAndTypeOrderByCreatedAtDesc(eq(creatorId), eq(ReputationEventType.RECOVERY)))
                .thenReturn(Optional.empty());
        when(eventRepository.existsByCreatorIdAndTypeInAndCreatedAtAfter(eq(creatorId), any(), any()))
                .thenReturn(true);

        // When
        recoveryService.processRecovery(snapshot);

        // Then
        verify(eventRepository, never()).save(any());
        verifyNoInteractions(calculationService);
        verifyNoInteractions(snapshotRepository);
    }

    @Test
    void processRecovery_InsufficientActivity_ShouldDoNothing() {
        // Given
        when(eventRepository.findFirstByCreatorIdAndTypeOrderByCreatedAtDesc(eq(creatorId), eq(ReputationEventType.RECOVERY)))
                .thenReturn(Optional.empty());
        when(eventRepository.existsByCreatorIdAndTypeInAndCreatedAtAfter(eq(creatorId), any(), any()))
                .thenReturn(false);
        when(eventRepository.countByCreatorIdAndTypeAndCreatedAtAfter(eq(creatorId), eq(ReputationEventType.TIP), any()))
                .thenReturn(4L);

        // When
        recoveryService.processRecovery(snapshot);

        // Then
        verify(eventRepository, never()).save(any());
        verifyNoInteractions(calculationService);
        verifyNoInteractions(snapshotRepository);
    }
}








