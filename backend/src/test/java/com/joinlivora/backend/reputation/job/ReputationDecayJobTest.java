package com.joinlivora.backend.reputation.job;

import com.joinlivora.backend.reputation.model.CreatorReputationSnapshot;
import com.joinlivora.backend.reputation.repository.CreatorReputationSnapshotRepository;
import com.joinlivora.backend.reputation.service.ReputationDecayService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReputationDecayJobTest {

    @Mock
    private CreatorReputationSnapshotRepository snapshotRepository;
    @Mock
    private ReputationDecayService decayService;

    @InjectMocks
    private ReputationDecayJob decayJob;

    @Test
    void run_ShouldProcessSnapshotsAboveFloor() {
        CreatorReputationSnapshot s1 = CreatorReputationSnapshot.builder().creatorId(UUID.randomUUID()).build();
        CreatorReputationSnapshot s2 = CreatorReputationSnapshot.builder().creatorId(UUID.randomUUID()).build();
        
        when(snapshotRepository.findAllByCurrentScoreGreaterThan(10)).thenReturn(List.of(s1, s2));

        decayJob.run();

        verify(decayService).processDecay(s1);
        verify(decayService).processDecay(s2);
    }

    @Test
    void run_ShouldHandleExceptionsAndContinue() {
        CreatorReputationSnapshot s1 = CreatorReputationSnapshot.builder().creatorId(UUID.randomUUID()).build();
        CreatorReputationSnapshot s2 = CreatorReputationSnapshot.builder().creatorId(UUID.randomUUID()).build();
        
        when(snapshotRepository.findAllByCurrentScoreGreaterThan(10)).thenReturn(List.of(s1, s2));
        doThrow(new RuntimeException("Error")).when(decayService).processDecay(s1);

        decayJob.run();

        verify(decayService).processDecay(s1);
        verify(decayService).processDecay(s2); // Should still process s2
    }
}








