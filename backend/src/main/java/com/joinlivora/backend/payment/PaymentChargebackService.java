package com.joinlivora.backend.payment;

import com.joinlivora.backend.chargeback.model.ChargebackCase;
import com.joinlivora.backend.user.User;
import com.stripe.model.Dispute;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * @deprecated Use com.joinlivora.backend.chargeback.ChargebackService instead.
 * TODO(livora-security): Remove in the next refactor stage.
 */
@Deprecated
@RequiredArgsConstructor
@Slf4j
public class PaymentChargebackService {

    private final com.joinlivora.backend.chargeback.ChargebackService canonicalChargebackService;

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

    /**
     * @deprecated AdminChargebackController now reads correlated cases directly from chargeback_cases
     * via {@link com.joinlivora.backend.chargeback.ChargebackService#findCorrelatedCasesByUserId(Long)}.
     * This method is retained for compatibility only and delegates to the canonical service.
     */
    @Deprecated
    @Transactional(readOnly = true)
    public List<ChargebackCase> findCorrelatedChargebacksForUser(Long userId) {
        return canonicalChargebackService.findCorrelatedCasesByUserId(userId);
    }
}
