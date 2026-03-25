package com.joinlivora.backend.payout.freeze;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service("payoutFreezeServiceNew")
@RequiredArgsConstructor
@Slf4j
public class PayoutFreezeService {
    private final PayoutFreezeRepository payoutFreezeRepository;
    private final PayoutFreezeAuditRepository auditRepository;

    @Transactional
    public void freezeCreator(Long creatorId, String reason, Long adminId) {
        log.info("Freezing payouts for creator: {}. Reason: {}, Admin: {}", creatorId, reason, adminId);
        
        payoutFreezeRepository.findByCreatorIdAndActiveTrue(creatorId).ifPresent(freeze -> {
            freeze.setActive(false);
            payoutFreezeRepository.save(freeze);
        });

        PayoutFreeze payoutFreeze = PayoutFreeze.builder()
                .creatorId(creatorId)
                .reason(reason)
                .createdByAdminId(adminId)
                .active(true)
                .build();

        payoutFreezeRepository.save(payoutFreeze);
        
        auditRepository.save(PayoutFreezeAuditLog.builder()
                .creatorId(creatorId)
                .action("FREEZE")
                .reason(reason)
                .adminId(adminId)
                .createdAt(Instant.now())
                .build());
    }

    @Transactional
    public void unfreezeCreator(Long creatorId) {
        log.info("Unfreezing payouts for creator: {}", creatorId);
        payoutFreezeRepository.findByCreatorIdAndActiveTrue(creatorId).ifPresent(freeze -> {
            freeze.setActive(false);
            payoutFreezeRepository.save(freeze);

            auditRepository.save(PayoutFreezeAuditLog.builder()
                    .creatorId(creatorId)
                    .action("UNFREEZE")
                    .reason("Manual unfreeze")
                    .adminId(1L) // temporary until auth integration
                    .createdAt(Instant.now())
                    .build());
        });
    }

    public Optional<PayoutFreeze> findActiveFreeze(Long creatorId) {
        return payoutFreezeRepository.findByCreatorIdAndActiveTrue(creatorId);
    }
}
