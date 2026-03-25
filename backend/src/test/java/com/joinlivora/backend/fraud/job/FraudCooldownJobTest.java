package com.joinlivora.backend.fraud.job;

import com.joinlivora.backend.fraud.model.UserRiskState;
import com.joinlivora.backend.fraud.repository.UserRiskStateRepository;
import com.joinlivora.backend.fraud.service.FraudDetectionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FraudCooldownJobTest {

    @Mock
    private UserRiskStateRepository userRiskStateRepository;

    @Mock
    private FraudDetectionService fraudDetectionService;

    @InjectMocks
    private FraudCooldownJob fraudCooldownJob;

    @Test
    void run_ShouldProcessExpiredBlocks() {
        UserRiskState state1 = UserRiskState.builder().userId(1L).build();
        UserRiskState state2 = UserRiskState.builder().userId(2L).build();
        
        when(userRiskStateRepository.findAllByBlockedUntilBefore(any(Instant.class)))
                .thenReturn(List.of(state1, state2));

        fraudCooldownJob.run();

        verify(fraudDetectionService).processCooldown(state1);
        verify(fraudDetectionService).processCooldown(state2);
    }

    @Test
    void run_WhenServiceThrows_ShouldContinueProcessing() {
        UserRiskState state1 = UserRiskState.builder().userId(3L).build();
        UserRiskState state2 = UserRiskState.builder().userId(4L).build();
        
        when(userRiskStateRepository.findAllByBlockedUntilBefore(any(Instant.class)))
                .thenReturn(List.of(state1, state2));
        
        doThrow(new RuntimeException("Test error")).when(fraudDetectionService).processCooldown(state1);

        fraudCooldownJob.run();

        verify(fraudDetectionService).processCooldown(state1);
        verify(fraudDetectionService).processCooldown(state2);
    }
}








