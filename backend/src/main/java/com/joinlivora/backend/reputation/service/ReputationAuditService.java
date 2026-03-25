package com.joinlivora.backend.reputation.service;

import com.joinlivora.backend.reputation.model.ReputationChangeLog;
import com.joinlivora.backend.reputation.model.ReputationEventSource;
import com.joinlivora.backend.reputation.repository.ReputationChangeLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReputationAuditService {

    private final ReputationChangeLogRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logChange(UUID creatorId, int oldScore, int newScore, String reason, ReputationEventSource source) {
        log.info("REPUTATION AUDIT: Logging change for creator {}: {} -> {} (Source: {}, Reason: {})",
                creatorId, oldScore, newScore, source, reason);

        ReputationChangeLog logEntry = ReputationChangeLog.builder()
                .creatorId(creatorId)
                .oldScore(oldScore)
                .newScore(newScore)
                .reason(reason)
                .source(source)
                .build();
        
        repository.save(logEntry);
    }
}
