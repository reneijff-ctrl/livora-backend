package com.joinlivora.backend.payout;

import com.joinlivora.backend.fraud.model.RiskSubjectType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service("payoutHoldAuditService")
@RequiredArgsConstructor
@Slf4j
public class PayoutHoldAuditService {

    private final PayoutHoldAuditRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logHoldApplied(RiskSubjectType subjectType, UUID subjectId, PayoutHoldPolicy previousPolicy, HoldLevel level, int days, Instant expiresAt, String reason) {
        HoldLevel prevLevel = previousPolicy != null ? previousPolicy.getHoldLevel() : HoldLevel.NONE;
        Integer prevDays = previousPolicy != null ? previousPolicy.getHoldDays() : 0;
        Instant prevExpiresAt = previousPolicy != null ? previousPolicy.getExpiresAt() : null;

        logHoldEvent(subjectType, subjectId, null, "APPLIED", prevLevel, prevDays, prevExpiresAt, level, days, expiresAt, reason);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logHoldOverridden(RiskSubjectType subjectType, UUID subjectId, Long adminId, PayoutHoldPolicy previousPolicy, HoldLevel newLevel, int newDays, Instant newExpiresAt, String reason) {
        HoldLevel prevLevel = previousPolicy != null ? previousPolicy.getHoldLevel() : HoldLevel.NONE;
        Integer prevDays = previousPolicy != null ? previousPolicy.getHoldDays() : 0;
        Instant prevExpiresAt = previousPolicy != null ? previousPolicy.getExpiresAt() : null;

        logHoldEvent(subjectType, subjectId, adminId, "OVERRIDDEN", prevLevel, prevDays, prevExpiresAt, newLevel, newDays, newExpiresAt, reason);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logHoldReleased(RiskSubjectType subjectType, UUID subjectId, Long adminId, PayoutHoldPolicy previousPolicy, String reason) {
        HoldLevel prevLevel = previousPolicy != null ? previousPolicy.getHoldLevel() : HoldLevel.NONE;
        Integer prevDays = previousPolicy != null ? previousPolicy.getHoldDays() : 0;
        Instant prevExpiresAt = previousPolicy != null ? previousPolicy.getExpiresAt() : null;

        logHoldEvent(subjectType, subjectId, adminId, "RELEASED", prevLevel, prevDays, prevExpiresAt, HoldLevel.NONE, 0, Instant.now(), reason);
    }

    private void logHoldEvent(RiskSubjectType subjectType, UUID subjectId, Long adminId, String action,
                              HoldLevel prevLevel, Integer prevDays, Instant prevExpiresAt,
                              HoldLevel newLevel, Integer newDays, Instant newExpiresAt, String reason) {
        
        log.info("PAYOUT HOLD AUDIT: {} for {} {}. Level: {} -> {}. Reason: {}", 
                action, subjectType, subjectId, prevLevel, newLevel, reason);

        PayoutActorType type = adminId != null ? PayoutActorType.ADMIN : PayoutActorType.SYSTEM;

        PayoutHoldAudit audit = PayoutHoldAudit.builder()
                .subjectType(subjectType)
                .subjectId(subjectId)
                .adminId(adminId)
                .type(type)
                .action(action)
                .prevHoldLevel(prevLevel)
                .prevHoldDays(prevDays)
                .prevExpiresAt(prevExpiresAt)
                .newHoldLevel(newLevel)
                .newHoldDays(newDays)
                .newExpiresAt(newExpiresAt)
                .reason(reason)
                .build();

        repository.save(audit);
    }
}
