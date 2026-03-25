package com.joinlivora.backend.fraud.service;

import com.joinlivora.backend.fraud.model.VelocityActionType;
import com.joinlivora.backend.fraud.model.VelocityMetric;
import com.joinlivora.backend.fraud.repository.VelocityMetricRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VelocityTrackerServiceTest {

    @Mock
    private VelocityMetricRepository velocityMetricRepository;

    @Mock
    private VelocityAnomalyDetector velocityAnomalyDetector;

    @InjectMocks
    private VelocityTrackerService velocityTrackerService;

    @Test
    void trackAction_ShouldUpsertThreeWindows() {
        Long userId = 123L;
        VelocityActionType actionType = VelocityActionType.PAYMENT;

        velocityTrackerService.trackAction(userId, actionType);

        // Verify that upsertIncrement was called 3 times
        verify(velocityMetricRepository, times(3)).upsertIncrement(
                any(UUID.class),
                eq(userId),
                eq(actionType.name()),
                any(Instant.class),
                any(Instant.class)
        );
    }

    @Test
    void trackAction_VerifyWindowCalculation() {
        Long userId = 456L;
        VelocityActionType actionType = VelocityActionType.LOGIN;
        
        // Mock current time by overriding or just verifying the captures
        // Since we use Instant.now() internally, we'll capture arguments
        
        ArgumentCaptor<Instant> startCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> endCaptor = ArgumentCaptor.forClass(Instant.class);

        velocityTrackerService.trackAction(userId, actionType);

        verify(velocityMetricRepository, times(3)).upsertIncrement(
                any(UUID.class),
                eq(userId),
                eq(actionType.name()),
                startCaptor.capture(),
                endCaptor.capture()
        );

        List<Instant> starts = startCaptor.getAllValues();
        List<Instant> ends = endCaptor.getAllValues();

        // Check 1 minute window
        Instant start1m = starts.get(0);
        Instant end1m = ends.get(0);
        assertEquals(60, ChronoUnit.SECONDS.between(start1m, end1m));
        assertEquals(0, start1m.getEpochSecond() % 60);

        // Check 10 minute window
        Instant start10m = starts.get(1);
        Instant end10m = ends.get(1);
        assertEquals(10, ChronoUnit.MINUTES.between(start10m, end10m));
        assertEquals(0, start10m.getEpochSecond() % 600);

        // Check 1 hour window
        Instant start1h = starts.get(2);
        Instant end1h = ends.get(2);
        assertEquals(1, ChronoUnit.HOURS.between(start1h, end1h));
        assertEquals(0, start1h.getEpochSecond() % 3600);
    }
}








