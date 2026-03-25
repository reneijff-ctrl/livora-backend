package com.joinlivora.backend.fraud.service;

import com.joinlivora.backend.fraud.model.VelocityActionType;
import com.joinlivora.backend.fraud.repository.VelocityMetricRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service("velocityTrackerService")
@Slf4j
@RequiredArgsConstructor
public class VelocityTrackerService {

    private final VelocityMetricRepository velocityMetricRepository;
    private final VelocityAnomalyDetector velocityAnomalyDetector;

    @Transactional
    public void trackAction(Long userId, VelocityActionType actionType) {
        log.debug("Tracking velocity for creator {} action {}", userId, actionType);
        Instant now = Instant.now();

        // 1 minute window
        trackWindow(userId, actionType, now, 1, ChronoUnit.MINUTES);
        // 10 minutes window
        trackWindow(userId, actionType, now, 10, ChronoUnit.MINUTES);
        // 1 hour window
        trackWindow(userId, actionType, now, 1, ChronoUnit.HOURS);
    }

    private void trackWindow(Long userId, VelocityActionType actionType, Instant now, int amount, ChronoUnit unit) {
        Instant windowStart = truncateToWindow(now, amount, unit);
        Instant windowEnd = windowStart.plus(amount, unit);

        velocityMetricRepository.upsertIncrement(
                UUID.randomUUID(),
                userId,
                actionType.name(),
                windowStart,
                windowEnd
        );

        // Evaluate for anomalies
        velocityMetricRepository.findByUserIdAndActionTypeAndWindowStartAndWindowEnd(userId, actionType, windowStart, windowEnd)
                .ifPresent(velocityAnomalyDetector::evaluateMetric);
    }

    private Instant truncateToWindow(Instant now, int amount, ChronoUnit unit) {
        if (unit == ChronoUnit.MINUTES) {
            long totalMinutes = now.getEpochSecond() / 60;
            long windowStartMinutes = (totalMinutes / amount) * amount;
            return Instant.ofEpochSecond(windowStartMinutes * 60);
        } else if (unit == ChronoUnit.HOURS) {
            long totalHours = now.getEpochSecond() / 3600;
            long windowStartHours = (totalHours / amount) * amount;
            return Instant.ofEpochSecond(windowStartHours * 3600);
        }
        throw new IllegalArgumentException("Unsupported unit: " + unit);
    }
}
