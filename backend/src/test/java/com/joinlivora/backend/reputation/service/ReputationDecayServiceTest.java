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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReputationDecayServiceTest {

    @Mock
    private CreatorReputationSnapshotRepository snapshotRepository;
    @Mock
    private ReputationEventRepository eventRepository;
    @Mock
    private ReputationCalculationService calculationService;
    @Mock
    private ReputationAuditService auditService;

    @InjectMocks
    private ReputationDecayService decayService;

    private CreatorReputationSnapshot snapshot;
    private UUID creatorId;

    @BeforeEach
    void setUp() {
        creatorId = UUID.randomUUID();
        snapshot = CreatorReputationSnapshot.builder()
                .creatorId(creatorId)
                .currentScore(50)
                .status(ReputationStatus.NORMAL)
                .lastPositiveEventAt(Instant.now().minus(8, ChronoUnit.DAYS))
                .build();
    }

    @Test
    void processDecay_NoPositiveEvent_ShouldNotDecay() {
        snapshot.setLastPositiveEventAt(null);
        decayService.processDecay(snapshot);
        verify(snapshotRepository, never()).save(any());
        verify(eventRepository, never()).save(any());
    }

    @Test
    void processDecay_RecentPositiveEvent_ShouldNotDecay() {
        snapshot.setLastPositiveEventAt(Instant.now().minus(6, ChronoUnit.DAYS));
        decayService.processDecay(snapshot);
        verify(snapshotRepository, never()).save(any());
    }

    @Test
    void processDecay_Over7Days_ShouldDecayBy1() {
        snapshot.setLastPositiveEventAt(Instant.now().minus(8, ChronoUnit.DAYS));
        when(calculationService.determineStatus(49)).thenReturn(ReputationStatus.WATCHED);

        decayService.processDecay(snapshot);

        assertEquals(49, snapshot.getCurrentScore());
        assertEquals(ReputationStatus.WATCHED, snapshot.getStatus());
        assertNotNull(snapshot.getLastDecayAt());
        verify(eventRepository).save(argThat(event -> 
            event.getDeltaScore() == -1 && event.getType() == ReputationEventType.DECAY));
        verify(snapshotRepository).save(snapshot);
        verify(auditService).logChange(creatorId, 50, 49, "DECAY", ReputationEventSource.SYSTEM);
    }

    @Test
    void processDecay_Over30Days_ShouldDecayBy2() {
        snapshot.setLastPositiveEventAt(Instant.now().minus(31, ChronoUnit.DAYS));
        when(calculationService.determineStatus(48)).thenReturn(ReputationStatus.WATCHED);

        decayService.processDecay(snapshot);

        assertEquals(48, snapshot.getCurrentScore());
        verify(eventRepository).save(argThat(event -> event.getDeltaScore() == -2));
    }

    @Test
    void processDecay_AtFloor_ShouldNotDecay() {
        snapshot.setCurrentScore(10);
        snapshot.setLastPositiveEventAt(Instant.now().minus(31, ChronoUnit.DAYS));

        decayService.processDecay(snapshot);

        assertEquals(10, snapshot.getCurrentScore());
        verify(snapshotRepository, never()).save(any());
    }

    @Test
    void processDecay_NearFloor_ShouldDecayOnlyToFloor() {
        snapshot.setCurrentScore(11);
        snapshot.setLastPositiveEventAt(Instant.now().minus(31, ChronoUnit.DAYS));
        when(calculationService.determineStatus(10)).thenReturn(ReputationStatus.RESTRICTED);

        decayService.processDecay(snapshot);

        assertEquals(10, snapshot.getCurrentScore());
        verify(eventRepository).save(argThat(event -> event.getDeltaScore() == -1));
    }

    @Test
    void processDecay_AlreadyDecayedToday_ShouldNotDecayAgain() {
        snapshot.setLastDecayAt(Instant.now().minus(1, ChronoUnit.HOURS));
        decayService.processDecay(snapshot);
        verify(snapshotRepository, never()).save(any());
    }
}








