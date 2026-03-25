package com.joinlivora.backend.aml.service;

import com.joinlivora.backend.aml.dto.AmlResult;
import com.joinlivora.backend.aml.model.AMLRule;
import com.joinlivora.backend.aml.model.RiskScore;
import com.joinlivora.backend.aml.repository.AMLRuleRepository;
import com.joinlivora.backend.aml.repository.RiskScoreRepository;
import com.joinlivora.backend.analytics.AnalyticsEventRepository;
import com.joinlivora.backend.analytics.AnalyticsEventType;
import com.joinlivora.backend.audit.service.AuditService;
import com.joinlivora.backend.monetization.TipRepository;
import com.joinlivora.backend.monetization.TipStatus;
import com.joinlivora.backend.payment.PaymentRepository;
import com.joinlivora.backend.payout.CreatorPayoutSettings;
import com.joinlivora.backend.payout.CreatorPayoutSettingsRepository;
import com.joinlivora.backend.payouts.service.PayoutFreezeService;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service("amlRulesEngine")
@RequiredArgsConstructor
@Slf4j
public class AMLRulesEngine {

    private final AMLRuleRepository amlRuleRepository;
    private final RiskScoreRepository riskScoreRepository;
    private final PayoutFreezeService payoutFreezeService;
    private final TipRepository tipRepository;
    private final PaymentRepository paymentRepository;
    private final CreatorPayoutSettingsRepository payoutSettingsRepository;
    private final AnalyticsEventRepository analyticsEventRepository;
    private final com.joinlivora.backend.user.UserRepository userRepository;
    private final AuditService auditService;

    private static final int CRITICAL_THRESHOLD = 81;

    @Transactional
    public AmlResult calculateRiskScore(UUID userId) {
        User user = userRepository.findById(userId.getLeastSignificantBits())
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        return evaluateRules(user, BigDecimal.ZERO);
    }

    @Transactional
    public AmlResult evaluateRules(User user, BigDecimal amount) {
        log.info("Evaluating AML rules for creator: {} (ID: {}) with amount: {}", user.getEmail(), user.getId(), amount);

        UUID userUuid = new UUID(0L, user.getId());
        List<AMLRule> activeRules = amlRuleRepository.findAll().stream()
                .filter(AMLRule::isEnabled)
                .toList();

        int totalScore = 0;
        List<String> triggeredRules = new ArrayList<>();

        for (AMLRule rule : activeRules) {
            if (isRuleTriggered(rule, user, amount)) {
                totalScore += rule.getThreshold();
                triggeredRules.add(rule.getCode());
                log.debug("AML Rule triggered: {} (+{} score)", rule.getCode(), rule.getThreshold());
            }
        }

        int finalScore = Math.min(totalScore, 100);
        String level = calculateRiskLevel(finalScore);

        // Save or update risk score
        RiskScore riskScore = riskScoreRepository.findTopByUserIdOrderByLastEvaluatedAtDesc(userUuid)
                .orElseGet(() -> RiskScore.builder().userId(userUuid).build());

        riskScore.setScore(finalScore);
        riskScore.setLevel(level);
        riskScore.setLastEvaluatedAt(Instant.now());
        riskScoreRepository.save(riskScore);

        // Trigger actions
        if ("HIGH".equals(level)) {
            log.warn("HIGH AML RISK for creator {}. Flagging for review.", user.getId());
            auditService.logEvent(
                    null,
                    AuditService.AML_FLAGGED,
                    "USER",
                    userUuid,
                    Map.of("score", finalScore, "triggeredRules", triggeredRules),
                    null,
                    null
            );
            if (user.getStatus() == UserStatus.ACTIVE || user.getStatus() == UserStatus.FLAGGED) {
                user.setStatus(UserStatus.MANUAL_REVIEW);
                userRepository.save(user);
            }
        } else if ("CRITICAL".equals(level)) {
            String reason = "AML Critical Risk: " + String.join(", ", triggeredRules);
            log.warn("SECURITY [aml_incident]: CRITICAL AML RISK for creator {}: {}. Freezing payouts.", user.getId(), reason);
            auditService.logEvent(
                    null,
                    AuditService.AML_PAYOUT_FROZEN,
                    "USER",
                    userUuid,
                    Map.of("score", finalScore, "triggeredRules", triggeredRules, "type", reason),
                    null,
                    null
            );
            payoutFreezeService.freezeCreator(userUuid, reason, "SYSTEM_AML");
        }

        return AmlResult.builder()
                .riskScore(finalScore)
                .riskLevel(level)
                .triggeredRules(triggeredRules)
                .build();
    }

    private boolean isRuleTriggered(AMLRule rule, User user, BigDecimal amount) {
        return switch (rule.getCode()) {
            case "RAPID_PAYOUT_AFTER_TIPS" -> hasRecentTips(user);
            case "REPEATED_PAYOUTS_TO_SAME_BANK" -> isSharedStripeAccount(user);
            case "NEW_ACCOUNT_PAYOUT" -> isNewAccount(user);
            case "HIGH_PAYOUT_LOW_CHAT_RATIO" -> isHighPayoutLowChatActivity(user, amount);
            case "HIGH_TIP_VELOCITY" -> hasHighTipVelocity(user);
            case "LARGE_SINGLE_TIP" -> hasLargeSingleTip(user);
            case "RAPID_BALANCE_GROWTH" -> hasRapidBalanceGrowth(user);
            case "REPEATED_FAILED_PAYMENTS" -> hasRepeatedFailedPayments(user);
            default -> {
                log.warn("Unknown AML rule code: {}", rule.getCode());
                yield false;
            }
        };
    }

    private String calculateRiskLevel(int score) {
        if (score <= 20) return "LOW";
        if (score <= 50) return "MEDIUM";
        if (score <= 80) return "HIGH";
        return "CRITICAL";
    }

    private boolean hasRecentTips(User user) {
        Instant since = Instant.now().minus(24, ChronoUnit.HOURS);
        return tipRepository.countByCreatorUserIdAndStatusAndCreatedAtAfter(user, TipStatus.COMPLETED, since) > 0;
    }

    private boolean isSharedStripeAccount(User user) {
        return payoutSettingsRepository.findByCreatorId(new UUID(0L, user.getId()))
                .map(settings -> {
                    String stripeId = settings.getStripeAccountId();
                    if (stripeId == null || stripeId.isBlank()) return false;
                    List<CreatorPayoutSettings> shared = payoutSettingsRepository.findAllByStripeAccountId(stripeId);
                    return shared.stream().anyMatch(s -> s.getCreatorId().getLeastSignificantBits() != user.getId());
                }).orElse(false);
    }

    private boolean isNewAccount(User user) {
        return analyticsEventRepository.findFirstByUserIdAndEventTypeOrderByCreatedAtDesc(user.getId(), AnalyticsEventType.USER_REGISTERED)
                .map(event -> event.getCreatedAt().isAfter(Instant.now().minus(7, ChronoUnit.DAYS)))
                .orElse(false);
    }

    private boolean isHighPayoutLowChatActivity(User user, BigDecimal amount) {
        if (amount == null || amount.compareTo(new BigDecimal("100")) < 0) return false;
        Instant since = Instant.now().minus(30, ChronoUnit.DAYS);
        long chatCount = analyticsEventRepository.countByUserIdAndEventTypeAndCreatedAtAfter(user.getId(), AnalyticsEventType.CHAT_MESSAGE_SENT, since);
        return chatCount < 5;
    }

    private boolean hasHighTipVelocity(User user) {
        Instant since = Instant.now().minus(1, ChronoUnit.HOURS);
        return tipRepository.countByCreatorUserIdAndStatusAndCreatedAtAfter(user, TipStatus.COMPLETED, since) > 20;
    }

    private boolean hasLargeSingleTip(User user) {
        return tipRepository.existsByCreatorUserIdAndStatusAndAmountGreaterThan(user, TipStatus.COMPLETED, new BigDecimal("500"));
    }

    private boolean hasRapidBalanceGrowth(User user) {
        Instant since = Instant.now().minus(24, ChronoUnit.HOURS);
        BigDecimal sum = tipRepository.sumAmountByCreatorUserIdAndStatusAndCreatedAtAfter(user, TipStatus.COMPLETED, since);
        return sum != null && sum.compareTo(new BigDecimal("2000")) > 0;
    }

    private boolean hasRepeatedFailedPayments(User user) {
        Instant since = Instant.now().minus(24, ChronoUnit.HOURS);
        return paymentRepository.countByUserIdAndSuccessAndCreatedAtAfter(user.getId(), false, since) > 5;
    }
}
