package com.joinlivora.backend.reputation.service;

import com.joinlivora.backend.reputation.model.*;
import com.joinlivora.backend.reputation.repository.CreatorReputationSnapshotRepository;
import com.joinlivora.backend.reputation.repository.ReputationEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReputationEventService {

    private final ReputationEventRepository eventRepository;
    private final CreatorReputationSnapshotRepository snapshotRepository;
    private final ReputationCalculationService calculationService;
    private final ReputationAuditService auditService;

    @Transactional
    public void recordEvent(UUID creatorId, ReputationEventType type, int delta, ReputationEventSource source, Map<String, Object> metadata) {
        log.info("Recording reputation event for creator {}: type={}, delta={}, source={}", creatorId, type, delta, source);

        ReputationEvent event = ReputationEvent.builder()
                .creatorId(creatorId)
                .type(type)
                .deltaScore(delta)
                .source(source)
                .metadata(metadata)
                .build();

        eventRepository.save(event);

        CreatorReputationSnapshot snapshot = snapshotRepository.findById(creatorId)
                .orElse(CreatorReputationSnapshot.builder()
                        .creatorId(creatorId)
                        .currentScore(50) // Default starting score
                        .status(ReputationStatus.NORMAL)
                        .build());

        int oldScore = snapshot.getCurrentScore();
        calculationService.applyEvent(snapshot, event);
        int newScore = snapshot.getCurrentScore();

        snapshotRepository.save(snapshot);

        if (oldScore != newScore) {
            auditService.logChange(creatorId, oldScore, newScore, type.name(), source);
        }
    }
}
