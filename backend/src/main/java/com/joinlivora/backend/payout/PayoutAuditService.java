package com.joinlivora.backend.payout;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PayoutAuditService {

    private final PayoutAuditLogRepository auditLogRepository;

    @Transactional(propagation = Propagation.REQUIRED)
    public void logStatusChange(UUID payoutId, PayoutStatus oldStatus, PayoutStatus newStatus, PayoutActorType actorType, UUID actorId, String message) {
        PayoutAuditLog log = PayoutAuditLog.builder()
                .payoutId(payoutId)
                .previousStatus(oldStatus)
                .newStatus(newStatus)
                .actorType(actorType)
                .actorId(actorId)
                .action("STATUS_CHANGE")
                .message(message)
                .createdAt(Instant.now())
                .build();
        auditLogRepository.save(log);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void logAction(UUID payoutId, String action, PayoutActorType actorType, UUID actorId, String message) {
        PayoutAuditLog log = PayoutAuditLog.builder()
                .payoutId(payoutId)
                .actorType(actorType)
                .actorId(actorId)
                .action(action)
                .message(message)
                .createdAt(Instant.now())
                .build();
        auditLogRepository.save(log);
    }
}
