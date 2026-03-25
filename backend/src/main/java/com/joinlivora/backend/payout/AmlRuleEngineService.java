package com.joinlivora.backend.payout;

import com.joinlivora.backend.analytics.AnalyticsEventRepository;
import com.joinlivora.backend.analytics.AnalyticsEventType;
import com.joinlivora.backend.monetization.TipRepository;
import com.joinlivora.backend.monetization.TipStatus;
import com.joinlivora.backend.payout.dto.AmlResult;
import com.joinlivora.backend.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service("payoutAmlRuleEngineService")
@RequiredArgsConstructor
@Slf4j
public class AmlRuleEngineService {

    private final TipRepository tipRepository;
    private final CreatorPayoutSettingsRepository payoutSettingsRepository;
    private final AnalyticsEventRepository analyticsEventRepository;

    public AmlResult analyze(User user, BigDecimal amount) {
        log.info("Performing AML analysis for creator: {} with payout amount: {}", user.getEmail(), amount);
        
        int score = 0;
        List<String> triggeredRules = new ArrayList<>();

        // 1. Rapid payout after tips
        if (hasRecentTips(user)) {
            score += 40;
            triggeredRules.add("RAPID_PAYOUT_AFTER_TIPS");
        }

        // 2. Repeated payouts to same bank (Shared bank account)
        CreatorPayoutSettings settings = payoutSettingsRepository.findByCreatorId(new UUID(0L, user.getId())).orElse(null);
        String stripeAccountId = settings != null ? settings.getStripeAccountId() : null;
        if (isSharedStripeAccount(user, stripeAccountId)) {
            score += 50;
            triggeredRules.add("REPEATED_PAYOUTS_TO_SAME_BANK");
        }

        // 3. Payouts shortly after account creation
        if (isNewAccount(user)) {
            score += 30;
            triggeredRules.add("NEW_ACCOUNT_PAYOUT");
        }

        // 4. High payout / low chat activity ratio
        if (isHighPayoutLowChatActivity(user, amount)) {
            score += 30;
            triggeredRules.add("HIGH_PAYOUT_LOW_CHAT_RATIO");
        }

        int finalScore = Math.min(score, 100);
        log.info("AML analysis completed for creator: {}. Risk score: {}. Triggered rules: {}",
                user.getEmail(), finalScore, triggeredRules);

        return new AmlResult(finalScore, triggeredRules);
    }

    private boolean hasRecentTips(User user) {
        Instant since = Instant.now().minus(24, ChronoUnit.HOURS);
        long tipCount = tipRepository.countByCreatorUserIdAndStatusAndCreatedAtAfter(user, TipStatus.COMPLETED, since);
        return tipCount > 0;
    }

    private boolean isSharedStripeAccount(User user, String stripeAccountId) {
        if (stripeAccountId == null || stripeAccountId.isBlank()) return false;
        List<CreatorPayoutSettings> settingsList = payoutSettingsRepository.findAllByStripeAccountId(stripeAccountId);
        return settingsList.stream().anyMatch(s -> s.getCreatorId().getLeastSignificantBits() != user.getId());
    }

    private boolean isNewAccount(User user) {
        return analyticsEventRepository.findFirstByUserIdAndEventTypeOrderByCreatedAtDesc(user.getId(), AnalyticsEventType.USER_REGISTERED)
                .map(event -> event.getCreatedAt().isAfter(Instant.now().minus(7, ChronoUnit.DAYS)))
                .orElse(false);
    }

    private boolean isHighPayoutLowChatActivity(User user, BigDecimal amount) {
        // High payout: > 100 EUR
        if (amount == null || amount.compareTo(new BigDecimal("100")) < 0) return false;
        
        // Low chat activity: < 5 messages in last 30 days
        Instant since = Instant.now().minus(30, ChronoUnit.DAYS);
        long chatCount = analyticsEventRepository.countByUserIdAndEventTypeAndCreatedAtAfter(user.getId(), AnalyticsEventType.CHAT_MESSAGE_SENT, since);
        return chatCount < 5;
    }
}
