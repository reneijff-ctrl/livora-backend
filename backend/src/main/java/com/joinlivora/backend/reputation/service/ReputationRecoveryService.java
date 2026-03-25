package com.joinlivora.backend.reputation.service;

import com.joinlivora.backend.reputation.model.CreatorReputationSnapshot;
import com.joinlivora.backend.reputation.model.ReputationEvent;
import com.joinlivora.backend.reputation.model.ReputationEventSource;
import com.joinlivora.backend.reputation.model.ReputationEventType;
import com.joinlivora.backend.reputation.repository.CreatorReputationSnapshotRepository;
import com.joinlivora.backend.reputation.repository.ReputationEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReputationRecoveryService {

    private final ReputationEventRepository eventRepository;
    private final CreatorReputationSnapshotRepository snapshotRepository;
    private final ReputationCalculationService calculationService;
    private final ReputationAuditService auditService;

    private static final int RECOVERY_DELTA = 5;
    private static final int ACTIVITY_THRESHOLD = 5;

    /**
     * Processes reputation recovery for a creator.
     * Rules:
     * - 7 consecutive days without fraud/chargebacks/reports
     * - Minimum activity threshold met (at least 5 tips in the last 7 days)
     * - Max once per week
     * - Only if score < 100
     */
    @Transactional
    public void processRecovery(CreatorReputationSnapshot snapshot) {
        if (snapshot.getCurrentScore() >= 100) {
            return;
        }

        Instant now = Instant.now();
        Instant sevenDaysAgo = now.minus(7, ChronoUnit.DAYS);

        // 1. Once per week max check
        Optional<ReputationEvent> lastRecovery = eventRepository.findFirstByCreatorIdAndTypeOrderByCreatedAtDesc(
                snapshot.getCreatorId(), ReputationEventType.RECOVERY);
        if (lastRecovery.isPresent() && lastRecovery.get().getCreatedAt().isAfter(sevenDaysAgo)) {
            log.debug("Skipping recovery for creator {}: Last recovery was less than 7 days ago", snapshot.getCreatorId());
            return;
        }

        // 2. No negative events (CHARGEBACK, FRAUD_FLAG, REPORT) in the last 7 days
        boolean hasNegativeEvents = eventRepository.existsByCreatorIdAndTypeInAndCreatedAtAfter(
                snapshot.getCreatorId(),
                List.of(ReputationEventType.CHARGEBACK, ReputationEventType.FRAUD_FLAG, ReputationEventType.REPORT),
                sevenDaysAgo
        );
        if (hasNegativeEvents) {
            log.debug("Skipping recovery for creator {}: Negative events found in the last 7 days", snapshot.getCreatorId());
            return;
        }

        // 3. Minimum activity threshold met
        long tipCount = eventRepository.countByCreatorIdAndTypeAndCreatedAtAfter(
                snapshot.getCreatorId(), ReputationEventType.TIP, sevenDaysAgo);
        if (tipCount < ACTIVITY_THRESHOLD) {
            log.debug("Skipping recovery for creator {}: Activity threshold not met (Tips: {}/{})", 
                    snapshot.getCreatorId(), tipCount, ACTIVITY_THRESHOLD);
            return;
        }

        // All conditions met, apply recovery
        ReputationEvent recoveryEvent = ReputationEvent.builder()
                .creatorId(snapshot.getCreatorId())
                .type(ReputationEventType.RECOVERY)
                .deltaScore(RECOVERY_DELTA)
                .source(ReputationEventSource.SYSTEM)
                .createdAt(now)
                .build();

        eventRepository.save(recoveryEvent);
        int oldScore = snapshot.getCurrentScore();
        calculationService.applyEvent(snapshot, recoveryEvent);
        int newScore = snapshot.getCurrentScore();

        snapshotRepository.save(snapshot);
        auditService.logChange(snapshot.getCreatorId(), oldScore, newScore, "RECOVERY", ReputationEventSource.SYSTEM);

        log.info("Recovered reputation for creator {}: +{} points, newScore={}, status={}", 
                snapshot.getCreatorId(), RECOVERY_DELTA, snapshot.getCurrentScore(), snapshot.getStatus());
    }
}
