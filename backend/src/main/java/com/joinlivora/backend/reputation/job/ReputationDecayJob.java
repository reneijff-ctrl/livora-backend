package com.joinlivora.backend.reputation.job;

import com.joinlivora.backend.reputation.model.CreatorReputationSnapshot;
import com.joinlivora.backend.reputation.repository.CreatorReputationSnapshotRepository;
import com.joinlivora.backend.reputation.service.ReputationDecayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReputationDecayJob {

    private final CreatorReputationSnapshotRepository snapshotRepository;
    private final ReputationDecayService decayService;

    // Daily at midnight
    @Scheduled(cron = "0 0 0 * * *")
    public void run() {
        log.info("Starting ReputationDecayJob...");
        
        // Only process those above the decay floor
        List<CreatorReputationSnapshot> snapshots = snapshotRepository.findAllByCurrentScoreGreaterThan(10);
        
        log.info("Found {} creators eligible for reputation decay analysis", snapshots.size());
        
        for (CreatorReputationSnapshot snapshot : snapshots) {
            try {
                decayService.processDecay(snapshot);
            } catch (Exception e) {
                log.error("Failed to process reputation decay for creator: {}", snapshot.getCreatorId(), e);
            }
        }
        
        log.info("ReputationDecayJob completed.");
    }
}
