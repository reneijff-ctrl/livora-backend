package com.joinlivora.backend.reputation.job;

import com.joinlivora.backend.reputation.model.CreatorReputationSnapshot;
import com.joinlivora.backend.reputation.repository.CreatorReputationSnapshotRepository;
import com.joinlivora.backend.reputation.service.ReputationRecoveryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReputationRecoveryJobTest {

    @Mock
    private CreatorReputationSnapshotRepository snapshotRepository;
    @Mock
    private ReputationRecoveryService recoveryService;

    @InjectMocks
    private ReputationRecoveryJob recoveryJob;

    @Test
    void run_ShouldProcessEligibleCreators() {
        // Given
        CreatorReputationSnapshot s1 = CreatorReputationSnapshot.builder().creatorId(UUID.randomUUID()).build();
        CreatorReputationSnapshot s2 = CreatorReputationSnapshot.builder().creatorId(UUID.randomUUID()).build();
        when(snapshotRepository.findAllByCurrentScoreLessThan(100)).thenReturn(List.of(s1, s2));

        // When
        recoveryJob.run();

        // Then
        verify(recoveryService).processRecovery(s1);
        verify(recoveryService).processRecovery(s2);
    }

    @Test
    void run_WithError_ShouldContinue() {
        // Given
        CreatorReputationSnapshot s1 = CreatorReputationSnapshot.builder().creatorId(UUID.randomUUID()).build();
        CreatorReputationSnapshot s2 = CreatorReputationSnapshot.builder().creatorId(UUID.randomUUID()).build();
        when(snapshotRepository.findAllByCurrentScoreLessThan(100)).thenReturn(List.of(s1, s2));
        doThrow(new RuntimeException("Error")).when(recoveryService).processRecovery(s1);

        // When
        recoveryJob.run();

        // Then
        verify(recoveryService).processRecovery(s1);
        verify(recoveryService).processRecovery(s2);
    }
}








