package com.joinlivora.backend.payment;

import com.joinlivora.backend.exception.ResourceNotFoundException;
import com.joinlivora.backend.fraud.model.FraudDecisionLevel;
import com.joinlivora.backend.fraud.model.FraudSignalType;
import com.joinlivora.backend.fraud.model.FraudSource;
import com.joinlivora.backend.fraud.model.RiskLevel;
import com.joinlivora.backend.fraud.service.FraudDetectionService;
import com.joinlivora.backend.payment.dto.RiskEscalationResult;
import com.joinlivora.backend.payout.PayoutHoldService;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import com.joinlivora.backend.user.UserStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service("autoFreezePolicyService")
@RequiredArgsConstructor
@Slf4j
public class AutoFreezePolicyService {

    private final UserRepository userRepository;
    private final FraudDetectionService fraudDetectionService;
    private final ChargebackCorrelationService chargebackCorrelationService;
    private final ChargebackRiskEscalationService riskEscalationService;
    private final PayoutHoldService payoutHoldService;
    private final UserSubscriptionRepository userSubscriptionRepository;

    @Transactional
    public void applyPayerPolicy(User user, int clusterSize) {
        log.info("Applying payer policy for creator: {} with cluster size: {}", user.getEmail(), clusterSize);
        if (clusterSize >= 3) {
            suspendAccount(user, "3 or more correlated chargebacks (Cluster size: " + clusterSize + ")");
        } else if (clusterSize >= 2) {
            freezePayouts(user, "2 correlated chargebacks (Cluster size: " + clusterSize + ")");
        } else {
            flagUser(user, "Chargeback received");
        }
    }

    @Transactional
    public void applyCreatorEscalation(Chargeback chargeback, RiskEscalationResult result) {
        if (chargeback.getCreatorId() == null || result.getRiskLevel() != RiskLevel.HIGH) {
            return;
        }

        User creator = userRepository.findById(chargeback.getCreatorId())
                .orElseThrow(() -> new ResourceNotFoundException("Creator not found: " + chargeback.getCreatorId()));

        log.warn("HIGH risk escalation for creator: {}. Applying payout hold and manual review status.", creator.getEmail());

        // Apply payout hold
        payoutHoldService.createHold(creator, RiskLevel.HIGH, chargeback.getTransactionId(),
                "Chargeback escalation: HIGH risk (2+ chargebacks in 30 days)");

        // Set status to MANUAL_REVIEW
        if (creator.getStatus() != UserStatus.SUSPENDED) {
            creator.setStatus(UserStatus.MANUAL_REVIEW);
            userRepository.save(creator);
        }
    }

    public void validateUserStatus(User user) {
        validateUserStatus(user, false);
    }

    public void validateUserStatus(User user, boolean skipPayoutsDisabledCheck) {
        if (user.getStatus() == UserStatus.SUSPENDED) {
            log.warn("SECURITY: Action blocked for SUSPENDED creator: {}", user.getEmail());
            throw new org.springframework.security.access.AccessDeniedException("Account is suspended.");
        }
        if (user.getStatus() == UserStatus.MANUAL_REVIEW) {
            log.warn("SECURITY: Action blocked for creator under MANUAL_REVIEW: {}", user.getEmail());
            throw new org.springframework.security.access.AccessDeniedException("Account is under manual review. Payments and payouts are restricted.");
        }
        if (user.getStatus() == UserStatus.PAYOUTS_FROZEN) {
            log.warn("SECURITY: Action blocked for creator with FROZEN payouts: {}", user.getEmail());
            throw new org.springframework.security.access.AccessDeniedException("Account is restricted. Payments and payouts are frozen.");
        }
        if (!skipPayoutsDisabledCheck && user.getRole() == Role.CREATOR && !user.isPayoutsEnabled()) {
            log.warn("SECURITY: Action blocked for creator with disabled payouts: {}", user.getEmail());
            throw new org.springframework.security.access.AccessDeniedException("Payouts are disabled for your account.");
        }
    }

    private void flagUser(User user, String reason) {
        if (user.getStatus() == UserStatus.ACTIVE) {
            log.warn("FLAGGING creator {}: {}", user.getEmail(), reason);
            user.setStatus(UserStatus.FLAGGED);
            userRepository.save(user);
            emitFraudSignal(user, FraudDecisionLevel.MEDIUM, reason, FraudSignalType.CHARGEBACK_CORRELATION);
        }
    }

    @Transactional
    public void freezePayouts(User user, String reason) {
        if (user.getStatus() == UserStatus.ACTIVE || user.getStatus() == UserStatus.FLAGGED) {
            log.warn("FREEZING payouts for creator {}: {}", user.getEmail(), reason);
            user.setStatus(UserStatus.PAYOUTS_FROZEN);
            user.setPayoutsEnabled(false);
            userRepository.save(user);
            emitFraudSignal(user, FraudDecisionLevel.HIGH, reason, FraudSignalType.CHARGEBACK_CORRELATION);
        }
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void suspendAccount(User user, String reason) {
        suspendAccount(user, reason, FraudSignalType.CHARGEBACK_CORRELATION);
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void suspendAccount(User user, String reason, FraudSignalType type) {
        if (user.getStatus() != UserStatus.SUSPENDED) {
            log.warn("SUSPENDING account for creator {}: {}", user.getEmail(), reason);
            user.setStatus(UserStatus.SUSPENDED);
            userRepository.save(user);
            emitFraudSignal(user, FraudDecisionLevel.HIGH, reason, type);
        }
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void terminateAccount(User user, String reason) {
        log.warn("TERMINATING account for creator {}: {}", user.getEmail(), reason);
        
        User entity = userRepository.findById(user.getId())
                .orElseThrow(() -> new RuntimeException("User not found: " + user.getId()));
        
        if (entity.getStatus() != UserStatus.TERMINATED) {
            entity.setStatus(UserStatus.TERMINATED);
            entity.setPayoutsEnabled(false);
            userRepository.saveAndFlush(entity);
        }
        
        // Cancel all active subscriptions
        userSubscriptionRepository.findAllByUser(entity).stream()
                .filter(sub -> sub.getStatus() == SubscriptionStatus.ACTIVE)
                .forEach(sub -> {
                    log.info("SECURITY: Marking subscription {} as CANCELED for terminated creator {}", sub.getId(), entity.getEmail());
                    sub.setStatus(SubscriptionStatus.CANCELED);
                    userSubscriptionRepository.saveAndFlush(sub);
                });
        
        fraudDetectionService.logFraudSignal(
                entity.getId(),
                FraudDecisionLevel.HIGH,
                FraudSource.SYSTEM,
                FraudSignalType.CHARGEBACK_CORRELATION,
                "TERMINATED: " + reason
        );
    }

    private void emitFraudSignal(User user, FraudDecisionLevel riskLevel, String reason, FraudSignalType type) {
        fraudDetectionService.logFraudSignal(
                user.getId(),
                riskLevel,
                FraudSource.SYSTEM,
                type,
                reason
        );
    }
}
