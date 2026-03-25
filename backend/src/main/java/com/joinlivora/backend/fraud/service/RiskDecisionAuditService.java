package com.joinlivora.backend.fraud.service;

import com.joinlivora.backend.fraud.model.RiskDecisionAudit;
import com.joinlivora.backend.fraud.model.RiskLevel;
import com.joinlivora.backend.fraud.repository.RiskDecisionAuditRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service("riskDecisionAuditService")
@RequiredArgsConstructor
@Slf4j
public class RiskDecisionAuditService {

    private final RiskDecisionAuditRepository auditRepository;

    @Transactional
    public void logDecision(UUID userId, UUID transactionId, String decisionType, RiskLevel riskLevel, Integer score, String triggeredBy, String actionsTaken, String reason) {
        RiskDecisionAudit audit = RiskDecisionAudit.builder()
                .userId(userId)
                .transactionId(transactionId)
                .decisionType(decisionType)
                .riskLevel(riskLevel)
                .score(score)
                .triggeredBy(triggeredBy)
                .actionsTaken(actionsTaken)
                .reason(reason)
                .build();

        auditRepository.save(audit);
        log.info("Risk decision audit logged: type={}, creator={}, risk={}", decisionType, userId, riskLevel);
    }
}
