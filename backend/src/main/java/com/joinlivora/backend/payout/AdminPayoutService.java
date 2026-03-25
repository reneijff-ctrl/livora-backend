package com.joinlivora.backend.payout;

import com.joinlivora.backend.audit.service.AuditService;
import com.joinlivora.backend.exception.ResourceNotFoundException;
import com.joinlivora.backend.payout.dto.AdminPayoutDetailDTO;
import com.joinlivora.backend.payout.dto.PayoutOverrideRequest;
import com.joinlivora.backend.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminPayoutService {

    private final CreatorPayoutRepository payoutRepository;
    private final CreatorEarningRepository earningRepository;
    private final PayoutHoldRepository holdRepository;
    private final StripePayoutService stripePayoutService;
    private final AuditService auditService;
    private final PayoutAuditService payoutAuditService;
    private final PayoutAuditLogRepository auditLogRepository;
    private final LegacyCreatorProfileRepository creatorProfileRepository;

    @Transactional(readOnly = true)
    public AdminPayoutDetailDTO getPayoutDetails(UUID payoutId) {
        CreatorPayout payout = payoutRepository.findById(payoutId)
                .orElseThrow(() -> new ResourceNotFoundException("Payout not found: " + payoutId));

        LegacyCreatorProfile profile = creatorProfileRepository.findById(payout.getCreatorId())
                .orElseThrow(() -> new ResourceNotFoundException("Creator profile not found for ID: " + payout.getCreatorId()));

        List<CreatorEarning> earnings = earningRepository.findAllByPayout(payout);
        
        List<PayoutAuditLog> auditLogs = auditLogRepository.findAllByPayoutIdOrderByCreatedAtDesc(payoutId);

        List<AdminPayoutDetailDTO.EarningDetailDTO> earningDTOs = earnings.stream()
                .map(e -> AdminPayoutDetailDTO.EarningDetailDTO.builder()
                        .id(e.getId())
                        .netAmount(e.getNetAmount())
                        .currency(e.getCurrency())
                        .sourceType(e.getSourceType().name())
                        .createdAt(e.getCreatedAt())
                        .build())
                .toList();

        List<AdminPayoutDetailDTO.PayoutHoldDetailDTO> holdDTOs = earnings.stream()
                .map(CreatorEarning::getPayoutHold)
                .filter(Objects::nonNull)
                .distinct()
                .map(h -> AdminPayoutDetailDTO.PayoutHoldDetailDTO.builder()
                        .id(h.getId())
                        .reason(h.getReason())
                        .status(h.getStatus().name())
                        .createdAt(h.getCreatedAt())
                        .build())
                .toList();

        List<AdminPayoutDetailDTO.PayoutAuditLogDTO> auditLogDTOs = auditLogs.stream()
                .map(log -> AdminPayoutDetailDTO.PayoutAuditLogDTO.builder()
                        .id(log.getId())
                        .actorType(log.getActorType().name())
                        .actorId(log.getActorId())
                        .action(log.getAction())
                        .previousStatus(log.getPreviousStatus() != null ? log.getPreviousStatus().name() : null)
                        .newStatus(log.getNewStatus() != null ? log.getNewStatus().name() : null)
                        .message(log.getMessage())
                        .createdAt(log.getCreatedAt())
                        .build())
                .toList();

        return AdminPayoutDetailDTO.builder()
                .id(payout.getId())
                .creatorId(payout.getCreatorId())
                .creatorEmail(profile.getUser().getEmail())
                .amount(payout.getAmount())
                .currency(payout.getCurrency())
                .status(payout.getStatus())
                .stripeTransferId(payout.getStripeTransferId())
                .completedAt(payout.getCompletedAt())
                .failureReason(payout.getFailureReason())
                .createdAt(payout.getCreatedAt())
                .earnings(earningDTOs)
                .holds(holdDTOs)
                .auditLogs(auditLogDTOs)
                .build();
    }

    @Transactional
    public void overridePayout(UUID payoutId, PayoutOverrideRequest request, User admin, String ip, String userAgent) {
        log.info("ADMIN_OVERRIDE: Payout {} being overridden by admin {}", payoutId, admin.getEmail());

        CreatorPayout payout = payoutRepository.findById(payoutId)
                .orElseThrow(() -> new ResourceNotFoundException("Payout not found: " + payoutId));

        UUID creatorId = payout.getCreatorId();

        if (request.isReleaseHold()) {
            log.info("ADMIN_OVERRIDE: Releasing holds for creator {}", creatorId);
            List<PayoutHold> activeHolds = holdRepository.findAllByUserIdOrderByCreatedAtDesc(creatorId).stream()
                    .filter(h -> h.getStatus() == PayoutHoldStatus.ACTIVE)
                    .toList();
            
            for (PayoutHold hold : activeHolds) {
                hold.setStatus(PayoutHoldStatus.RELEASED);
                holdRepository.save(hold);
            }
            payoutAuditService.logAction(payoutId, "RELEASE_HOLD", PayoutActorType.ADMIN, new UUID(0L, admin.getId()), "Admin released all active payout holds");
        }

        if (request.isRelockEarnings()) {
            log.info("ADMIN_OVERRIDE: Re-locking earnings for payout {}", payoutId);
            List<CreatorEarning> earnings = earningRepository.findAllByPayout(payout);
            for (CreatorEarning earning : earnings) {
                earning.setLocked(true);
            }
            earningRepository.saveAll(earnings);
            payoutAuditService.logAction(payoutId, "RELOCK_EARNINGS", PayoutActorType.ADMIN, new UUID(0L, admin.getId()), "Admin re-locked associated earnings");
        }

        if (request.isForcePayout()) {
            log.info("ADMIN_OVERRIDE: Forcing payout {}", payoutId);
            PayoutStatus oldStatus = payout.getStatus();
            payout.setStatus(PayoutStatus.PENDING);
            payoutRepository.save(payout);
            payoutAuditService.logStatusChange(payoutId, oldStatus, PayoutStatus.PENDING, PayoutActorType.ADMIN, new UUID(0L, admin.getId()), "Admin forced payout: status reset to PENDING. Note: " + request.getAdminNote());
            
            // Ensure earnings are locked if forcing payout
            List<CreatorEarning> earnings = earningRepository.findAllByPayout(payout);
            for (CreatorEarning earning : earnings) {
                earning.setLocked(true);
            }
            earningRepository.saveAll(earnings);

            stripePayoutService.triggerPayout(payoutId);
        }

        auditService.logEvent(
                new UUID(0L, admin.getId()),
                AuditService.PAYOUT_OVERRIDE,
                "PAYOUT",
                payoutId,
                Map.of(
                        "releaseHold", request.isReleaseHold(),
                        "relockEarnings", request.isRelockEarnings(),
                        "forcePayout", request.isForcePayout(),
                        "adminNote", request.getAdminNote() != null ? request.getAdminNote() : ""
                ),
                ip,
                userAgent
        );
    }
}
