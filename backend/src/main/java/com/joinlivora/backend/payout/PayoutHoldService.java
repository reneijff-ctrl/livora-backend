package com.joinlivora.backend.payout;

import com.joinlivora.backend.fraud.model.FraudRiskAssessment;
import com.joinlivora.backend.fraud.model.RiskLevel;
import com.joinlivora.backend.fraud.model.RiskSubjectType;
import com.joinlivora.backend.payout.dto.PayoutHoldStatusDTO;
import com.joinlivora.backend.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service("payoutHoldService")
@RequiredArgsConstructor
@Slf4j
public class PayoutHoldService {

    private final PayoutHoldPolicyRepository holdPolicyRepository;
    private final PayoutHoldRepository payoutHoldRepository;
    private final CreatorEarningRepository earningRepository;

    @Transactional
    public PayoutHold createHold(FraudRiskAssessment assessment, UUID transactionId) {
        return createHold(assessment.getUserId(), assessment.getRiskLevel(), transactionId, 
                "Fraud assessment score: " + assessment.getScore());
    }

    @Transactional
    public PayoutHold createHold(User user, RiskLevel riskLevel, UUID transactionId, String reason) {
        return createHold(new UUID(0L, user.getId()), riskLevel, transactionId, reason);
    }

    private PayoutHold createHold(UUID userId, RiskLevel riskLevel, UUID transactionId, String reason) {
        if (riskLevel == RiskLevel.LOW || riskLevel == null) {
            return null;
        }

        Instant holdUntil = PayoutHoldRules.calculateHoldUntil(riskLevel);

        PayoutHold hold = PayoutHold.builder()
                .userId(userId)
                .transactionId(transactionId)
                .riskLevel(riskLevel)
                .holdUntil(holdUntil)
                .status(PayoutHoldStatus.ACTIVE)
                .reason(reason)
                .build();

        log.info("Created payout hold for creator {} until {} (Reason: {})", userId, holdUntil, reason);
        return payoutHoldRepository.save(hold);
    }

    @Transactional
    public void linkEarningToHold(CreatorEarning earning, PayoutHold hold) {
        if (hold == null) return;
        earning.setPayoutHold(hold);
        earning.setLocked(true);
        earningRepository.save(earning);
    }

    @Transactional
    public void linkToActiveHolds(CreatorEarning earning) {
        UUID userId = new UUID(0L, earning.getCreator().getId());
        Instant now = Instant.now();

        // Find the latest active hold for this creator
        Optional<PayoutHold> latestHold = payoutHoldRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(h -> h.getStatus() == PayoutHoldStatus.ACTIVE && h.getHoldUntil().isAfter(now))
                .findFirst();

        if (latestHold.isPresent()) {
            earning.setPayoutHold(latestHold.get());
            earning.setLocked(true);
        }

        // Also check for legacy PayoutHoldPolicy
        Optional<PayoutHoldPolicy> latestPolicy = holdPolicyRepository.findAllBySubjectIdAndSubjectTypeOrderByCreatedAtDesc(userId, RiskSubjectType.CREATOR).stream()
                .filter(p -> p.getExpiresAt() != null && p.getExpiresAt().isAfter(now))
                .findFirst();

        if (latestPolicy.isPresent()) {
            earning.setHoldPolicy(latestPolicy.get());
            earning.setLocked(true);
        }

        if (earning.isLocked()) {
            earningRepository.save(earning);
        }
    }

    public boolean isPayable(CreatorEarning earning) {
        if (!earning.isLocked()) return true;

        // Check PayoutHold
        if (earning.getPayoutHold() != null) {
            PayoutHold hold = earning.getPayoutHold();
            if (hold.getStatus() == PayoutHoldStatus.CANCELLED) {
                return false;
            }
            if (hold.getStatus() == PayoutHoldStatus.ACTIVE && hold.getHoldUntil().isAfter(Instant.now())) {
                return false;
            }
        }

        // Check legacy PayoutHoldPolicy
        if (earning.getHoldPolicy() != null) {
            PayoutHoldPolicy policy = earning.getHoldPolicy();
            if (policy.getExpiresAt() != null && policy.getExpiresAt().isAfter(Instant.now())) {
                return false;
            }
        }

        return true;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public int releaseExpiredHolds() {
        Instant now = Instant.now();
        List<PayoutHold> expired = payoutHoldRepository.findAllByStatusAndHoldUntilBefore(PayoutHoldStatus.ACTIVE, now);

        for (PayoutHold hold : expired) {
            log.info("Releasing expired hold {}", hold.getId());
            hold.setStatus(PayoutHoldStatus.RELEASED);
            payoutHoldRepository.save(hold);
        }
        return expired.size();
    }

    public boolean hasActiveHold(User user) {
        return getPayoutHoldStatus(user).getHoldLevel() != HoldLevel.NONE;
    }

    public PayoutHoldStatusDTO getPayoutHoldStatus(User user) {
        return getPayoutHoldStatus(new UUID(0L, user.getId()));
    }

    public PayoutHoldStatusDTO getPayoutHoldStatus(UUID subjectId) {
        Instant now = Instant.now();

        // New holds
        Optional<PayoutHold> latestHold = payoutHoldRepository.findAllByUserIdOrderByCreatedAtDesc(subjectId).stream()
                .filter(h -> h.getStatus() == PayoutHoldStatus.ACTIVE && h.getHoldUntil().isAfter(now))
                .max(Comparator.comparing(PayoutHold::getHoldUntil));

        // Legacy policies
        Optional<PayoutHoldPolicy> latestPolicy = holdPolicyRepository.findAllBySubjectIdAndSubjectTypeOrderByCreatedAtDesc(subjectId, RiskSubjectType.CREATOR).stream()
                .filter(p -> p.getExpiresAt() != null && p.getExpiresAt().isAfter(now))
                .max(Comparator.comparing(PayoutHoldPolicy::getExpiresAt));

        if (latestHold.isEmpty() && latestPolicy.isEmpty()) {
            return PayoutHoldStatusDTO.builder()
                    .holdLevel(HoldLevel.NONE)
                    .build();
        }

        // If both exist, take the one that expires later
        if (latestHold.isPresent() && (latestPolicy.isEmpty() || latestHold.get().getHoldUntil().isAfter(latestPolicy.get().getExpiresAt()))) {
            PayoutHold h = latestHold.get();
            return PayoutHoldStatusDTO.builder()
                    .holdLevel(mapRiskToHoldLevel(h.getRiskLevel()))
                    .unlockDate(h.getHoldUntil())
                    .reason(sanitizeReason(h.getReason()))
                    .build();
        } else {
            PayoutHoldPolicy p = latestPolicy.get();
            return PayoutHoldStatusDTO.builder()
                    .holdLevel(p.getHoldLevel())
                    .unlockDate(p.getExpiresAt())
                    .reason(sanitizeReason(p.getReason()))
                    .build();
        }
    }

    private HoldLevel mapRiskToHoldLevel(RiskLevel riskLevel) {
        return switch (riskLevel) {
            case LOW -> HoldLevel.NONE;
            case MEDIUM -> HoldLevel.MEDIUM;
            case HIGH, CRITICAL -> HoldLevel.LONG;
        };
    }

    private String sanitizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "Account under routine payout review";
        }
        
        if (reason.contains("ADMIN_OVERRIDE")) {
            return "Manual review by administration";
        }

        return reason;
    }
}
