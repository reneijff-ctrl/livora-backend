package com.joinlivora.backend.payment;

import com.joinlivora.backend.audit.service.AuditService;
import com.joinlivora.backend.fraud.service.FraudDetectionService;
import com.joinlivora.backend.payment.dto.RiskEscalationResult;
import com.joinlivora.backend.reputation.model.ReputationEventSource;
import com.joinlivora.backend.reputation.model.ReputationEventType;
import com.joinlivora.backend.reputation.service.ReputationEventService;
import com.joinlivora.backend.user.User;
import com.stripe.model.Dispute;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @deprecated Use com.joinlivora.backend.chargeback.ChargebackService instead.
 * TODO(livora-security): Remove in the next refactor stage.
 */
@Deprecated
@Service("paymentChargebackService")
@RequiredArgsConstructor
@Slf4j
public class PaymentChargebackService {

    private final com.joinlivora.backend.chargeback.ChargebackService canonicalChargebackService;
    private final ChargebackRepository chargebackRepository;
    private final ChargebackCorrelationService chargebackCorrelationService;

    @Transactional
    public void processChargeback(User user, String paymentIntentId, Dispute dispute) {
        log.warn("DEPRECATED: PaymentChargebackService.processChargeback called. Redirecting to canonical service.");
        canonicalChargebackService.handleDisputeCreated(user, paymentIntentId, dispute);
    }

    private ChargebackStatus mapStripeStatus(String stripeStatus) {
        if (stripeStatus == null) return ChargebackStatus.RECEIVED;
        return switch (stripeStatus) {
            case "won" -> ChargebackStatus.WON;
            case "lost" -> ChargebackStatus.LOST;
            case "under_review", "warning_under_review" -> ChargebackStatus.UNDER_REVIEW;
            default -> ChargebackStatus.RECEIVED;
        };
    }

    private boolean isResolved(ChargebackStatus status) {
        return status == ChargebackStatus.WON || status == ChargebackStatus.LOST;
    }

    @Transactional(readOnly = true)
    public List<Chargeback> findCorrelatedChargebacksForUser(Long userId) {
        List<Chargeback> userChargebacks = chargebackRepository.findAllByUserId(new UUID(0L, userId));
        if (userChargebacks.isEmpty()) {
            return List.of();
        }

        Set<UUID> allCorrelatedIds = new HashSet<>();
        for (Chargeback cb : userChargebacks) {
            allCorrelatedIds.addAll(chargebackCorrelationService.findCorrelatedChargebacks(cb).stream()
                    .map(Chargeback::getId)
                    .collect(Collectors.toSet()));
        }

        // Remove the creator's own chargebacks from the results
        Set<UUID> userChargebackIds = userChargebacks.stream().map(Chargeback::getId).collect(Collectors.toSet());
        allCorrelatedIds.removeAll(userChargebackIds);

        return chargebackRepository.findAllById(allCorrelatedIds);
    }
}
