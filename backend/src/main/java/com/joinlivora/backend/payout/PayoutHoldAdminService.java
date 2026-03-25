package com.joinlivora.backend.payout;

import com.joinlivora.backend.fraud.model.RiskSubjectType;
import com.joinlivora.backend.payout.dto.PayoutHoldOverrideRequest;
import com.joinlivora.backend.payout.dto.PayoutHoldReleaseRequest;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service("payoutHoldAdminService")
@RequiredArgsConstructor
@Slf4j
public class PayoutHoldAdminService {

    private final PayoutHoldPolicyRepository holdPolicyRepository;
    private final StripePayoutAdapter stripePayoutAdapter;
    private final StripeAccountRepository stripeAccountRepository;
    private final UserRepository userRepository;
    private final CreatorEarningsService creatorEarningsService;
    private final PayoutHoldAuditService holdAuditService;

    @Transactional
    public void overrideHold(PayoutHoldOverrideRequest request, User admin) {
        log.info("ADMIN OVERRIDE: Admin {} setting payout hold for {} {}: level={}, days={}, type={}",
                admin.getEmail(), request.getSubjectType(), request.getSubjectId(),
                request.getHoldLevel(), request.getHoldDays(), request.getReason());

        PayoutHoldPolicy previousPolicy = holdPolicyRepository.findAllBySubjectIdAndSubjectTypeOrderByCreatedAtDesc(
                        request.getSubjectId(), request.getSubjectType())
                .stream().findFirst().orElse(null);

        Instant expiresAt = request.getHoldDays() > 0 
                ? Instant.now().plus(request.getHoldDays(), ChronoUnit.DAYS)
                : null;

        PayoutHoldPolicy policy = PayoutHoldPolicy.builder()
                .subjectType(request.getSubjectType())
                .subjectId(request.getSubjectId())
                .holdLevel(request.getHoldLevel())
                .holdDays(request.getHoldDays())
                .reason("ADMIN_OVERRIDE: " + request.getReason() + " (by " + admin.getEmail() + ")")
                .expiresAt(expiresAt)
                .build();

        holdPolicyRepository.save(policy);

        holdAuditService.logHoldOverridden(request.getSubjectType(), request.getSubjectId(), admin.getId(),
                previousPolicy, request.getHoldLevel(), request.getHoldDays(), expiresAt, request.getReason());

        // Update Stripe if subject is a CREATOR
        if (request.getSubjectType() == RiskSubjectType.CREATOR) {
            updateStripeHold(request.getSubjectId(), Math.max(2, request.getHoldDays()));
        }
    }

    @Transactional
    public void releaseHold(PayoutHoldReleaseRequest request, User admin) {
        log.info("ADMIN RELEASE: Admin {} releasing payout hold for {} {}. Reason: {}",
                admin.getEmail(), request.getSubjectType(), request.getSubjectId(), request.getReason());

        List<PayoutHoldPolicy> activePolicies = holdPolicyRepository.findAllBySubjectIdAndSubjectTypeOrderByCreatedAtDesc(
                request.getSubjectId(), request.getSubjectType());

        PayoutHoldPolicy currentPolicy = activePolicies.stream()
                .filter(p -> p.getExpiresAt() != null && p.getExpiresAt().isAfter(Instant.now()))
                .findFirst().orElse(null);

        Instant now = Instant.now();
        boolean released = false;
        for (PayoutHoldPolicy policy : activePolicies) {
            if (policy.getExpiresAt() != null && policy.getExpiresAt().isAfter(now)) {
                policy.setExpiresAt(now);
                holdPolicyRepository.save(policy);
                released = true;
            }
        }

        if (released) {
            holdAuditService.logHoldReleased(request.getSubjectType(), request.getSubjectId(), admin.getId(), currentPolicy, request.getReason());
            // Update Stripe to default delay (2 days) if subject is a CREATOR
            if (request.getSubjectType() == RiskSubjectType.CREATOR) {
                updateStripeHold(request.getSubjectId(), 2); 
            }
            
            // Immediately trigger earnings unlock
            creatorEarningsService.unlockExpiredEarnings();
        }
    }

    private void updateStripeHold(UUID subjectId, int holdDays) {
        try {
            // subjectId for CREATOR is expected to be UUID(0, creator)
            Long userId = subjectId.getLeastSignificantBits();
            userRepository.findById(userId).ifPresent(user -> {
                stripeAccountRepository.findByUser(user).ifPresent(stripeAccount -> {
                    if (stripeAccount.getStripeAccountId() != null) {
                        try {
                            stripePayoutAdapter.enforceHold(stripeAccount.getStripeAccountId(), holdDays);
                        } catch (Exception e) {
                            log.error("Failed to update Stripe hold for account {}: {}", 
                                    stripeAccount.getStripeAccountId(), e.getMessage());
                        }
                    }
                });
            });
        } catch (Exception e) {
            log.error("Error updating Stripe hold for subjectId {}: {}", subjectId, e.getMessage());
        }
    }
}
