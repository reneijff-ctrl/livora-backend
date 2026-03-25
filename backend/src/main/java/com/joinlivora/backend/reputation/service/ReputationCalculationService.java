package com.joinlivora.backend.reputation.service;

import com.joinlivora.backend.reputation.model.CreatorReputationSnapshot;
import com.joinlivora.backend.reputation.model.ReputationEvent;
import com.joinlivora.backend.reputation.model.ReputationStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ReputationCalculationService {

    /**
     * Calculates the new reputation score and status for a creator based on a reputation event.
     * 
     * @param snapshot The current reputation snapshot of the creator.
     * @param event The reputation event to apply.
     * @return The updated snapshot (mutated).
     */
    public CreatorReputationSnapshot applyEvent(CreatorReputationSnapshot snapshot, ReputationEvent event) {
        int oldScore = snapshot.getCurrentScore();
        int newScore = oldScore + event.getDeltaScore();

        // Clamp score between 0 and 100
        newScore = Math.max(0, Math.min(100, newScore));
        snapshot.setCurrentScore(newScore);

        // Update status based on thresholds
        snapshot.setStatus(determineStatus(newScore));

        // Update last positive event timestamp if applicable
        if (event.getDeltaScore() > 0) {
            snapshot.setLastPositiveEventAt(event.getCreatedAt());
        }

        log.debug("Updated reputation for creator {}: Score {} -> {}, Status {}", 
                snapshot.getCreatorId(), oldScore, newScore, snapshot.getStatus());

        return snapshot;
    }

    /**
     * Determines the reputation status based on the current score.
     * Thresholds:
     * - 80+ → TRUSTED
     * - 50–79 → NORMAL
     * - 30–49 → WATCHED
     * - <30 → RESTRICTED
     */
    public ReputationStatus determineStatus(int score) {
        if (score >= 80) {
            return ReputationStatus.TRUSTED;
        } else if (score >= 50) {
            return ReputationStatus.NORMAL;
        } else if (score >= 30) {
            return ReputationStatus.WATCHED;
        } else {
            return ReputationStatus.RESTRICTED;
        }
    }
}
