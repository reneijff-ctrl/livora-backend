package com.joinlivora.backend.payouts.service;

import com.joinlivora.backend.audit.service.AuditService;
import com.joinlivora.backend.payouts.model.CreatorAccount;
import com.joinlivora.backend.payouts.model.PayoutFreezeHistory;
import com.joinlivora.backend.payouts.repository.CreatorAccountRepository;
import com.joinlivora.backend.payouts.repository.PayoutFreezeHistoryRepository;
import com.joinlivora.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayoutFreezeService {

    private final CreatorAccountRepository creatorAccountRepository;
    private final PayoutFreezeHistoryRepository historyRepository;
    private final com.joinlivora.backend.user.UserService userService;
    private final AuditService auditService;

    @Transactional
    public void freezeCreator(UUID creatorId, String reason, String triggeredBy) {
        log.info("Freezing payouts for creator: {}. Reason: {}, Triggered by: {}", creatorId, reason, triggeredBy);

        CreatorAccount account = creatorAccountRepository.findByCreatorId(creatorId)
                .orElse(CreatorAccount.builder()
                        .creatorId(creatorId)
                        .build());

        account.setPayoutFrozen(true);
        account.setFreezeReason(reason);
        account.setFrozenAt(Instant.now());
        creatorAccountRepository.save(account);

        PayoutFreezeHistory history = PayoutFreezeHistory.builder()
                .creatorId(creatorId)
                .reason(reason)
                .triggeredBy(triggeredBy)
                .createdAt(Instant.now())
                .build();
        historyRepository.save(history);

        UUID actorId = userService.resolveUserFromSubject(triggeredBy)
                .map(u -> new UUID(0L, u.getId()))
                .orElse(null);

        auditService.logEvent(
                actorId,
                AuditService.PAYOUT_FROZEN,
                "USER",
                creatorId,
                Map.of("type", reason, "triggeredBy", triggeredBy),
                null,
                null
        );
    }

    @Transactional
    public void unfreezeCreator(UUID creatorId, String triggeredBy) {
        log.info("Unfreezing payouts for creator: {} by {}", creatorId, triggeredBy);

        creatorAccountRepository.findByCreatorId(creatorId).ifPresent(account -> {
            account.setPayoutFrozen(false);
            account.setFreezeReason(null);
            account.setFrozenAt(null);
            creatorAccountRepository.save(account);

            UUID actorId = userService.resolveUserFromSubject(triggeredBy)
                    .map(u -> new UUID(0L, u.getId()))
                    .orElse(null);

            auditService.logEvent(
                    actorId,
                    AuditService.PAYOUT_UNFROZEN,
                    "USER",
                    creatorId,
                    Map.of("action", "unfreeze", "triggeredBy", triggeredBy),
                    null,
                    null
            );
        });
    }
}
