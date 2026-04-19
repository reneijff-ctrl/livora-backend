package com.joinlivora.backend.chargeback;

import com.joinlivora.backend.audit.service.AuditService;
import com.joinlivora.backend.chargeback.model.ChargebackCase;
import com.joinlivora.backend.chargeback.model.ChargebackStatus;
import com.joinlivora.backend.chargeback.repository.ChargebackCaseRepository;
import com.joinlivora.backend.fraud.model.FraudDecision;
import com.joinlivora.backend.fraud.repository.FraudDecisionRepository;
import com.joinlivora.backend.fraud.service.FraudDetectionService;
import com.joinlivora.backend.fraud.service.RiskDecisionAuditService;
import com.joinlivora.backend.fraud.model.RiskLevel;
import com.joinlivora.backend.payment.*;
import com.joinlivora.backend.payment.dto.RiskEscalationResult;
import com.joinlivora.backend.payout.PayoutHoldService;
import com.joinlivora.backend.reputation.model.ReputationEventSource;
import com.joinlivora.backend.reputation.model.ReputationEventType;
import com.joinlivora.backend.reputation.service.ReputationEventService;
import com.joinlivora.backend.user.FraudRiskLevel;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import com.joinlivora.backend.user.UserStatus;
import com.stripe.model.Dispute;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * Canonical service for handling all chargeback-related operations.
 * Consolidates PaymentChargebackService, fraud.service.ChargebackService, and InternalChargebackService.
 */
@Service("chargebackService")
@RequiredArgsConstructor
@Slf4j
public class ChargebackService {

    // Repositories
    private final ChargebackCaseRepository chargebackCaseRepository;
    private final com.joinlivora.backend.fraud.repository.ChargebackRepository fraudChargebackRepository;

    // Core dependencies
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final FraudDecisionRepository fraudDecisionRepository;
    private final FraudDetectionService fraudDetectionService;
    private final PayoutHoldService payoutHoldService;
    private final ChargebackClawbackService clawbackService;

    // Side-effect services from PaymentChargebackService
    private final AutoFreezePolicyService autoFreezePolicyService;
    private final ChargebackCorrelationService chargebackCorrelationService;
    private final ChargebackRiskEscalationService riskEscalationService;
    private final ChargebackAlertService chargebackAlertService;
    private final ChargebackAuditService chargebackAuditService;
    private final RiskDecisionAuditService riskDecisionAuditService;
    private final ReputationEventService reputationEventService;
    private final AuditService auditService;

    /**
     * Primary entry point for Stripe chargeback webhooks (dispute created).
     */
    @Transactional
    public void handleDisputeCreated(User user, String paymentIntentId, Dispute dispute) {
        log.warn("SECURITY: Handling chargeback created for creator: {}, PaymentIntent: {}, Dispute: {}",
                user.getEmail(), paymentIntentId, dispute.getId());

        // 1. Look up associated Payment record once — used by multiple steps below
        Optional<Payment> paymentOpt = paymentRepository.findByStripePaymentIntentId(paymentIntentId);
        if (paymentOpt.isEmpty()) {
            log.warn("SECURITY: Chargeback without matching Payment: paymentIntentId={}", paymentIntentId);
        }

        // 2. Persist to canonical tables
        persistFraudChargeback(user, dispute);
        persistChargebackCase(user, paymentIntentId, dispute, paymentOpt);

        // 3. Trigger FIFO clawback if tokens are involved
        paymentOpt.ifPresent(clawbackService::clawbackTokens);

        // 4. Fraud Detection and Enforcements
        fraudDetectionService.recordChargeback(user, paymentIntentId);
        applyFraudRules(user);

        // 5. Reputation and Audit
        recordReputationEvent(user, dispute, paymentOpt);
        auditChargebackReceived(user, paymentIntentId, dispute, paymentOpt);

        // 6. Risk Analysis (Payer & Creator)
        analyzeDisputeRisk(user, paymentIntentId, dispute);
    }

    /**
     * Primary entry point for Stripe chargeback webhooks (dispute closed).
     */
    @Transactional
    public void handleDisputeClosed(String stripeDisputeId, Dispute dispute) {
        log.info("SECURITY: Handling chargeback closed: Dispute={}, Status={}", stripeDisputeId, dispute.getStatus());

        boolean won = "won".equals(dispute.getStatus());
        String stripeChargeId = dispute.getCharge();

        // 1. Update fraud chargeback
        fraudChargebackRepository.findByStripeChargeId(stripeChargeId).ifPresent(cb -> {
            cb.setStatus(won ? com.joinlivora.backend.fraud.ChargebackStatus.WON : com.joinlivora.backend.fraud.ChargebackStatus.LOST);
            cb.setResolvedAt(Instant.now());
            fraudChargebackRepository.save(cb);
            
            User user = cb.getUser();
            updateTrustScore(user, won);
            applyFraudRules(user);
        });

        // 3. Update chargeback case
        chargebackCaseRepository.findByPaymentIntentId(dispute.getPaymentIntent()).ifPresent(cb -> {
            cb.setStatus(won ? ChargebackStatus.WON : ChargebackStatus.LOST);
            chargebackCaseRepository.save(cb);
        });
    }

    // --- Persistence Helpers ---

    private void persistFraudChargeback(User user, Dispute dispute) {
        if (fraudChargebackRepository.findByStripeChargeId(dispute.getCharge()).isPresent()) {
            return;
        }

        com.joinlivora.backend.fraud.Chargeback cb = com.joinlivora.backend.fraud.Chargeback.builder()
                .stripeChargeId(dispute.getCharge())
                .user(user)
                .amount(BigDecimal.valueOf(dispute.getAmount()).divide(BigDecimal.valueOf(100)))
                .currency(dispute.getCurrency())
                .reason(dispute.getReason())
                .status(com.joinlivora.backend.fraud.ChargebackStatus.OPEN)
                .build();
        fraudChargebackRepository.save(cb);
        
        // From fraud.service.ChargebackService: create payout hold
        RiskLevel holdRisk = user.getFraudRiskLevel() == FraudRiskLevel.HIGH ? RiskLevel.HIGH : RiskLevel.MEDIUM;
        payoutHoldService.createHold(user, holdRisk, null, "Chargeback opened: " + dispute.getCharge());
    }

    private void persistChargebackCase(User user, String paymentIntentId, Dispute dispute, Optional<Payment> paymentOpt) {
        if (chargebackCaseRepository.findByPaymentIntentId(paymentIntentId).isPresent()) {
            return;
        }

        int fraudScoreAtTime = fraudDecisionRepository.findFirstByUserIdOrderByCreatedAtDesc(new UUID(0L, user.getId()))
                .map(FraudDecision::getScore)
                .orElse(0);

        ChargebackCase.ChargebackCaseBuilder builder = ChargebackCase.builder()
                .userId(new UUID(0L, user.getId()))
                .paymentIntentId(paymentIntentId)
                .amount(BigDecimal.valueOf(dispute.getAmount()).divide(BigDecimal.valueOf(100)))
                .currency(dispute.getCurrency())
                .status(ChargebackStatus.OPEN)
                .reason(dispute.getReason())
                .fraudScoreAtTime(fraudScoreAtTime)
                .stripeDisputeId(dispute.getId())
                .stripeChargeId(dispute.getCharge());

        paymentOpt.ifPresent(p -> {
            builder.transactionId(p.getId());
            builder.deviceFingerprint(p.getDeviceFingerprint());
            builder.ipAddress(p.getIpAddress());
            builder.paymentMethodFingerprint(p.getPaymentMethodFingerprint());
            builder.paymentMethodBrand(p.getPaymentMethodBrand());
            builder.paymentMethodLast4(p.getPaymentMethodLast4());
            if (p.getCreator() != null) {
                builder.creatorId(p.getCreator().getId());
            }
        });

        chargebackCaseRepository.save(builder.build());
    }

    // --- Business Logic ---

    public void applyFraudRules(User user) {
        List<com.joinlivora.backend.fraud.Chargeback> cbs = fraudChargebackRepository.findByUser_Id(user.getId());
        long openCount = cbs.stream().filter(c -> c.getStatus() == com.joinlivora.backend.fraud.ChargebackStatus.OPEN).count();
        long lostCount = cbs.stream().filter(c -> c.getStatus() == com.joinlivora.backend.fraud.ChargebackStatus.LOST).count();

        if (lostCount >= 3) {
            user.setPayoutsEnabled(false);
            user.setStatus(UserStatus.PAYOUTS_FROZEN);
            user.setFraudRiskLevel(FraudRiskLevel.HIGH);
        } else if (lostCount >= 2) {
            user.setFraudRiskLevel(FraudRiskLevel.HIGH);
            user.setPayoutsEnabled(false);
        } else if (openCount >= 1) {
            if (user.getFraudRiskLevel() != FraudRiskLevel.HIGH) {
                user.setFraudRiskLevel(FraudRiskLevel.MEDIUM);
            }
            user.setPayoutsEnabled(false);
        }
        userRepository.save(user);
    }

    private void updateTrustScore(User user, boolean won) {
        if (!won) {
            user.setTrustScore(Math.max(0, user.getTrustScore() - 50));
        } else {
            user.setTrustScore(Math.min(100, user.getTrustScore() + 20));
        }
        userRepository.save(user);
    }

    private void analyzeDisputeRisk(User user, String paymentIntentId, Dispute dispute) {
        chargebackCaseRepository.findByStripeDisputeId(dispute.getId()).ifPresent(cb -> {
            int clusterSize = chargebackCorrelationService.findCorrelatedChargebacks(cb).size() + 1;
            autoFreezePolicyService.applyPayerPolicy(user, clusterSize);

            RiskEscalationResult escalation = RiskEscalationResult.builder()
                    .riskLevel(RiskLevel.LOW).actions(List.of()).build();

            if (cb.getCreatorId() != null) {
                escalation = riskEscalationService.evaluateEscalation(cb.getCreatorId());
                autoFreezePolicyService.applyCreatorEscalation(cb, escalation);
            }

            chargebackAuditService.audit(cb, clusterSize, escalation);
            riskDecisionAuditService.logDecision(cb.getUserId(), cb.getTransactionId(), "CHARGEBACK_ENFORCEMENT",
                    escalation.getRiskLevel(), null, "STRIPE_WEBHOOK", "CLUSTER_SIZE=" + clusterSize, "Processed");
            chargebackAlertService.alert(cb, clusterSize);
        });
    }

    private void recordReputationEvent(User user, Dispute dispute, Optional<Payment> paymentOpt) {
        paymentOpt.ifPresent(p -> {
            if (p.getCreator() != null) {
                reputationEventService.recordEvent(
                        new UUID(0L, p.getCreator().getId()),
                        ReputationEventType.CHARGEBACK,
                        -20,
                        ReputationEventSource.SYSTEM,
                        Map.of("chargebackId", dispute.getCharge(), "amount", dispute.getAmount())
                );
            }
        });
    }

    private void auditChargebackReceived(User user, String paymentIntentId, Dispute dispute, Optional<Payment> paymentOpt) {
        auditService.logEvent(
                new UUID(0L, user.getId()),
                AuditService.CHARGEBACK_RECEIVED,
                "CHARGEBACK",
                null,
                Map.of("amount", dispute.getAmount(), "reason", dispute.getReason(), "disputeId", dispute.getId()),
                paymentOpt.map(Payment::getIpAddress).orElse(null),
                null
        );
    }

    // --- Read Methods ---

    @Transactional(readOnly = true)
    public long getChargebackCount(UUID userId) {
        return chargebackCaseRepository.countByUserId(userId);
    }

    @Transactional(readOnly = true)
    public List<ChargebackCase> getAllChargebackCases() {
        return chargebackCaseRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Page<ChargebackCase> getAllChargebackCasesPaged(Pageable pageable) {
        return chargebackCaseRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public List<ChargebackCase> findCorrelatedCasesByUserId(Long userId) {
        return chargebackCaseRepository.findAllByUserId(new UUID(0L, userId));
    }

    @Transactional(readOnly = true)
    public Page<com.joinlivora.backend.fraud.dto.ChargebackAdminResponseDTO> getFraudChargebacks(Pageable pageable) {
        return fraudChargebackRepository.findAll(pageable)
                .map(this::mapToFraudAdminDTO);
    }

    private com.joinlivora.backend.fraud.dto.ChargebackAdminResponseDTO mapToFraudAdminDTO(com.joinlivora.backend.fraud.Chargeback cb) {
        return com.joinlivora.backend.fraud.dto.ChargebackAdminResponseDTO.builder()
                .userEmail(cb.getUser().getEmail())
                .amount(cb.getAmount())
                .currency(cb.getCurrency())
                .reason(cb.getReason())
                .status(cb.getStatus())
                .createdAt(cb.getCreatedAt())
                .fraudRisk(cb.getUser().getFraudRiskLevel())
                .build();
    }

    @Transactional(readOnly = true)
    public Optional<ChargebackCase> getChargebackById(UUID id) {
        return chargebackCaseRepository.findById(id);
    }

    @Transactional
    public ChargebackCase updateStatus(UUID chargebackId, ChargebackStatus newStatus) {
        log.info("Updating status for chargeback case {} to {}", chargebackId, newStatus);
        ChargebackCase chargebackCase = chargebackCaseRepository.findById(chargebackId)
                .orElseThrow(() -> new IllegalArgumentException("Chargeback case not found: " + chargebackId));

        chargebackCase.setStatus(newStatus);
        return chargebackCaseRepository.save(chargebackCase);
    }

}
