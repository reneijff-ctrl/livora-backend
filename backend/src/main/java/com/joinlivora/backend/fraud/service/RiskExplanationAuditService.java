package com.joinlivora.backend.fraud.service;

import com.joinlivora.backend.fraud.model.RiskExplanationLog;
import com.joinlivora.backend.fraud.repository.RiskExplanationLogRepository;
import com.joinlivora.backend.user.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service("riskExplanationAuditService")
@RequiredArgsConstructor
@Slf4j
public class RiskExplanationAuditService {

    private final RiskExplanationLogRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logRequest(UUID requesterId, Role role, UUID explanationId) {
        log.info("SECURITY [explanation_access]: Requester {} (Role: {}) accessed explanation {}", 
                requesterId, role, explanationId);
        
        RiskExplanationLog entry = RiskExplanationLog.builder()
                .requesterId(requesterId)
                .role(role)
                .explanationId(explanationId)
                .timestamp(Instant.now())
                .build();
        
        repository.save(entry);
    }
}
