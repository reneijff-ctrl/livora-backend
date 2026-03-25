package com.joinlivora.backend.reputation.job;

import com.joinlivora.backend.reputation.model.CreatorReputationSnapshot;
import com.joinlivora.backend.reputation.repository.CreatorReputationSnapshotRepository;
import com.joinlivora.backend.reputation.service.ReputationRecoveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReputationRecoveryJob {

    private final CreatorReputationSnapshotRepository snapshotRepository;
    private final ReputationRecoveryService recoveryService;

    // Daily at 01:00 AM (to not collide with DecayJob at 00:00)
    @Scheduled(cron = "0 0 1 * * *")
    public void run() {
        log.info("Starting ReputationRecoveryJob...");

        // Only process those who can still recover
        List<CreatorReputationSnapshot> snapshots = snapshotRepository.findAllByCurrentScoreLessThan(100);

        log.info("Found {} creators eligible for reputation recovery analysis", snapshots.size());

        for (CreatorReputationSnapshot snapshot : snapshots) {
            try {
                recoveryService.processRecovery(snapshot);
            } catch (Exception e) {
                log.error("Failed to process reputation recovery for creator: {}", snapshot.getCreatorId(), e);
            }
        }

        log.info("ReputationRecoveryJob completed.");
    }
}
