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

@Service
@RequiredArgsConstructor
@Slf4j
public class ReputationDecayService {

    private final CreatorReputationSnapshotRepository snapshotRepository;
    private final ReputationEventRepository eventRepository;
    private final ReputationCalculationService calculationService;
    private final ReputationAuditService auditService;

    @Transactional
    public void processDecay(CreatorReputationSnapshot snapshot) {
        Instant now = Instant.now();
        
        // Safety check to avoid double decay if job runs multiple times
        if (snapshot.getLastDecayAt() != null && 
            snapshot.getLastDecayAt().isAfter(now.minus(23, ChronoUnit.HOURS))) {
            return;
        }

        Instant lastPositive = snapshot.getLastPositiveEventAt();
        if (lastPositive == null) {
            // If no positive event ever, we don't decay as we don't have a referenceId point
            // for the "last 7 days" rule that is fair to new creators.
            return;
        }

        long daysSincePositive = ChronoUnit.DAYS.between(lastPositive, now);
        int decayAmount = 0;

        if (daysSincePositive >= 30) {
            decayAmount = -2;
        } else if (daysSincePositive >= 7) {
            decayAmount = -1;
        }

        if (decayAmount < 0 && snapshot.getCurrentScore() > 10) {
            // "Never decay below 10 automatically"
            int actualDecay = Math.max(decayAmount, 10 - snapshot.getCurrentScore());
            
            if (actualDecay == 0) return;

            ReputationEvent decayEvent = ReputationEvent.builder()
                    .creatorId(snapshot.getCreatorId())
                    .type(ReputationEventType.DECAY)
                    .deltaScore(actualDecay)
                    .source(ReputationEventSource.SYSTEM)
                    .createdAt(now)
                    .build();

            eventRepository.save(decayEvent);
            
            int oldScore = snapshot.getCurrentScore();
            snapshot.setCurrentScore(oldScore + actualDecay);
            snapshot.setStatus(calculationService.determineStatus(snapshot.getCurrentScore()));
            snapshot.setLastDecayAt(now);
            
            snapshotRepository.save(snapshot);
            auditService.logChange(snapshot.getCreatorId(), oldScore, snapshot.getCurrentScore(), "DECAY", ReputationEventSource.SYSTEM);
            
            log.info("Decayed reputation for creator {}: delta={}, newScore={}, status={}", 
                    snapshot.getCreatorId(), actualDecay, snapshot.getCurrentScore(), snapshot.getStatus());
        }
    }
}
