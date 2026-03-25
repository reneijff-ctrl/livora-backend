package com.joinlivora.backend.fraud.service;

import com.joinlivora.backend.fraud.Chargeback;
import com.joinlivora.backend.fraud.ChargebackStatus;
import com.joinlivora.backend.fraud.model.*;
import com.joinlivora.backend.fraud.repository.ChargebackRepository;
import com.joinlivora.backend.fraud.repository.FraudScoreRepository;
import com.joinlivora.backend.payment.PaymentRepository;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service("fraudEvaluationService")
@RequiredArgsConstructor
@Slf4j
public class FraudEvaluationService {

    @Qualifier("fraudChargebackRepository")
    private final ChargebackRepository chargebackRepository;
    private final FraudScoreRepository fraudScoreRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final EnforcementService enforcementService;
    private final RiskScoringService riskScoringService;

    @Transactional
    public void evaluateUser(UUID userId) {
        evaluateUser(userId, null, null, false, null);
    }

    @Transactional
    public void evaluateUser(UUID userId, String stripeEventId) {
        evaluateUser(userId, stripeEventId, null, false, null);
    }

    @Transactional
    public void evaluateUser(UUID userId, String stripeEventId, String reason, boolean isRegistration, String ipAddress) {
        log.info("Evaluating fraud score for creator: {}", userId);

        List<Chargeback> chargebacks = chargebackRepository.findByUser_Id(userId.getLeastSignificantBits());
        int chargebackCount = chargebacks.size();

        // Calculate rate based on successful payments
        long numericUserId = userId.getLeastSignificantBits();
        long totalPayments = paymentRepository.countByUserIdAndSuccessAndCreatedAtAfter(numericUserId, true, Instant.MIN);
        
        double chargebackRate = totalPayments > 0 ? (double) chargebackCount / totalPayments : 0.0;
        Instant lastChargebackAt = chargebacks.stream()
                .map(Chargeback::getCreatedAt)
                .max(Instant::compareTo)
                .orElse(null);

        String source = stripeEventId != null ? "STRIPE" : "INTERNAL";

        if (isRegistration) {
            enforcementService.recordChargeback(userId, reason, chargebackCount, chargebackRate, stripeEventId, "SYSTEM", source, ipAddress);
        }

        Map<RiskFactor, Integer> factors = new EnumMap<>(RiskFactor.class);
        if (chargebackCount > 0) {
            factors.put(RiskFactor.CHARGEBACK_COUNT, chargebackCount);
        }
        if (chargebackRate > 0.1) {
            factors.put(RiskFactor.CHARGEBACK_RATE, 1);
        }

        RiskScore riskScore = riskScoringService.calculateAndPersist(userId, factors);

        FraudScore fraudScore = fraudScoreRepository.findByUserId(numericUserId)
                .orElse(FraudScore.builder().userId(numericUserId).build());

        int score = riskScore.getScore();
        fraudScore.setScore(score);
        fraudScore.setRiskLevel(FraudRiskLevel.fromScore(score).name());
        fraudScore.setCalculatedAt(Instant.now());
        fraudScoreRepository.save(fraudScore);

        RiskAction riskAction = riskScoringService.evaluateAction(riskScore.getScore());

        EnforcementAction action = mapToEnforcementAction(riskAction);
        triggerEnforcement(userId, action, chargebackCount, chargebackRate, stripeEventId, source, ipAddress, riskScore.getScore());
    }

    @Transactional
    public void processSuccessfulPayment(UUID userId, String paymentIntentId, Long amount, String currency, String stripeEventId, String ipAddress) {
        log.info("Processing successful payment for creator: {}. PI: {}", userId, paymentIntentId);
        enforcementService.recordPaymentSuccess(userId, paymentIntentId, amount, currency, stripeEventId, ipAddress);

        // Re-evaluate creator to update chargeback rate and other factors
        evaluateUser(userId, stripeEventId, "Successful payment: " + paymentIntentId, false, ipAddress);
    }

    private EnforcementAction mapToEnforcementAction(RiskAction riskAction) {
        return switch (riskAction) {
            case ACCOUNT_TERMINATED -> EnforcementAction.TERMINATE_ACCOUNT;
            case ACCOUNT_SUSPENDED -> EnforcementAction.SUSPEND_ACCOUNT;
            case PAYOUT_FROZEN -> EnforcementAction.FREEZE_PAYOUTS;
            case NO_ACTION -> EnforcementAction.NONE;
        };
    }

    private int getThreshold(EnforcementAction action) {
        return switch (action) {
            case TERMINATE_ACCOUNT -> 80;
            case SUSPEND_ACCOUNT -> 60;
            case FREEZE_PAYOUTS -> 40;
            default -> 0;
        };
    }

    private void triggerEnforcement(UUID userId, EnforcementAction action, int count, double rate, String stripeEventId, String source, String ipAddress, int riskScore) {
        int thresholdReached = getThreshold(action);
        switch (action) {
            case FREEZE_PAYOUTS:
                enforcementService.freezePayouts(userId, "Fraud evaluation: FREEZE_PAYOUTS (Chargeback count: " + count + ")", count, rate, stripeEventId, "SYSTEM", source, ipAddress, riskScore, thresholdReached);
                break;
            case SUSPEND_ACCOUNT:
                String reason = count >= 2 ? "Chargeback count: " + count : "Chargeback rate: " + String.format("%.2f", rate);
                enforcementService.suspendAccount(userId, "Fraud evaluation: SUSPEND_ACCOUNT (" + reason + ")", count, rate, stripeEventId, "SYSTEM", source, ipAddress, riskScore, thresholdReached);
                break;
            case TERMINATE_ACCOUNT:
                enforcementService.terminateAccount(userId, "Fraud evaluation: TERMINATE_ACCOUNT (Chargeback count: " + count + ")", count, rate, stripeEventId, "SYSTEM", source, ipAddress, riskScore, thresholdReached);
                break;
            default:
                break;
        }
    }
}
