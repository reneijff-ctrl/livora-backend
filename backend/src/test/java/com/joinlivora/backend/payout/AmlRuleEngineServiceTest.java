package com.joinlivora.backend.payout;

import com.joinlivora.backend.analytics.AnalyticsEvent;
import com.joinlivora.backend.analytics.AnalyticsEventRepository;
import com.joinlivora.backend.analytics.AnalyticsEventType;
import com.joinlivora.backend.monetization.TipRepository;
import com.joinlivora.backend.monetization.TipStatus;
import com.joinlivora.backend.payout.dto.AmlResult;
import com.joinlivora.backend.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AmlRuleEngineServiceTest {

    @Mock
    private TipRepository tipRepository;
    @Mock
    private CreatorPayoutSettingsRepository payoutSettingsRepository;
    @Mock
    private AnalyticsEventRepository analyticsEventRepository;

    @InjectMocks
    private AmlRuleEngineService amlRuleEngineService;

    private User user;
    private Long userId = 1L;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(userId);
        user.setEmail("test@test.com");
    }

    @Test
    void analyze_NoRulesTriggered_ReturnsZeroScore() {
        when(payoutSettingsRepository.findByCreatorId(any())).thenReturn(Optional.empty());
        when(tipRepository.countByCreatorUserIdAndStatusAndCreatedAtAfter(any(), any(), any())).thenReturn(0L);
        when(analyticsEventRepository.findFirstByUserIdAndEventTypeOrderByCreatedAtDesc(anyLong(), any())).thenReturn(Optional.empty());
        
        AmlResult result = amlRuleEngineService.analyze(user, new BigDecimal("50.00"));
        
        assertEquals(0, result.getRiskScore());
        assertTrue(result.getTriggeredRules().isEmpty());
    }

    @Test
    void analyze_RapidPayout_TriggersRule() {
        when(payoutSettingsRepository.findByCreatorId(any())).thenReturn(Optional.empty());
        when(tipRepository.countByCreatorUserIdAndStatusAndCreatedAtAfter(any(), eq(TipStatus.COMPLETED), any())).thenReturn(5L);
        when(analyticsEventRepository.findFirstByUserIdAndEventTypeOrderByCreatedAtDesc(anyLong(), any())).thenReturn(Optional.empty());

        AmlResult result = amlRuleEngineService.analyze(user, new BigDecimal("50.00"));

        assertEquals(40, result.getRiskScore());
        assertTrue(result.getTriggeredRules().contains("RAPID_PAYOUT_AFTER_TIPS"));
    }

    @Test
    void analyze_SharedBankAccount_TriggersRule() {
        String stripeAcc = "acct_shared";
        CreatorPayoutSettings mySettings = CreatorPayoutSettings.builder()
                .creatorId(new UUID(0L, userId))
                .stripeAccountId(stripeAcc)
                .build();
        CreatorPayoutSettings otherSettings = CreatorPayoutSettings.builder()
                .creatorId(new UUID(0L, 999L))
                .stripeAccountId(stripeAcc)
                .build();

        when(payoutSettingsRepository.findByCreatorId(any())).thenReturn(Optional.of(mySettings));
        when(payoutSettingsRepository.findAllByStripeAccountId(stripeAcc)).thenReturn(List.of(mySettings, otherSettings));
        when(tipRepository.countByCreatorUserIdAndStatusAndCreatedAtAfter(any(), any(), any())).thenReturn(0L);
        when(analyticsEventRepository.findFirstByUserIdAndEventTypeOrderByCreatedAtDesc(anyLong(), any())).thenReturn(Optional.empty());

        AmlResult result = amlRuleEngineService.analyze(user, new BigDecimal("50.00"));

        assertEquals(50, result.getRiskScore());
        assertTrue(result.getTriggeredRules().contains("REPEATED_PAYOUTS_TO_SAME_BANK"));
    }

    @Test
    void analyze_NewAccount_TriggersRule() {
        AnalyticsEvent regEvent = new AnalyticsEvent();
        regEvent.setCreatedAt(Instant.now().minus(2, ChronoUnit.DAYS));

        when(payoutSettingsRepository.findByCreatorId(any())).thenReturn(Optional.empty());
        when(tipRepository.countByCreatorUserIdAndStatusAndCreatedAtAfter(any(), any(), any())).thenReturn(0L);
        when(analyticsEventRepository.findFirstByUserIdAndEventTypeOrderByCreatedAtDesc(userId, AnalyticsEventType.USER_REGISTERED))
                .thenReturn(Optional.of(regEvent));

        AmlResult result = amlRuleEngineService.analyze(user, new BigDecimal("50.00"));

        assertEquals(30, result.getRiskScore());
        assertTrue(result.getTriggeredRules().contains("NEW_ACCOUNT_PAYOUT"));
    }

    @Test
    void analyze_HighPayoutLowActivity_TriggersRule() {
        when(payoutSettingsRepository.findByCreatorId(any())).thenReturn(Optional.empty());
        when(tipRepository.countByCreatorUserIdAndStatusAndCreatedAtAfter(any(), any(), any())).thenReturn(0L);
        when(analyticsEventRepository.findFirstByUserIdAndEventTypeOrderByCreatedAtDesc(anyLong(), any())).thenReturn(Optional.empty());
        when(analyticsEventRepository.countByUserIdAndEventTypeAndCreatedAtAfter(eq(userId), eq(AnalyticsEventType.CHAT_MESSAGE_SENT), any()))
                .thenReturn(2L);

        AmlResult result = amlRuleEngineService.analyze(user, new BigDecimal("200.00"));

        assertEquals(30, result.getRiskScore());
        assertTrue(result.getTriggeredRules().contains("HIGH_PAYOUT_LOW_CHAT_RATIO"));
    }

    @Test
    void analyze_MultipleRules_CappedAt100() {
        // Trigger Rapid Payout (40) and Shared Bank (50) and New Account (30) = 120 -> 100
        when(tipRepository.countByCreatorUserIdAndStatusAndCreatedAtAfter(any(), eq(TipStatus.COMPLETED), any())).thenReturn(1L);
        
        String stripeAcc = "acct_shared";
        CreatorPayoutSettings mySettings = CreatorPayoutSettings.builder()
                .creatorId(new UUID(0L, userId))
                .stripeAccountId(stripeAcc)
                .build();
        CreatorPayoutSettings otherSettings = CreatorPayoutSettings.builder()
                .creatorId(new UUID(0L, 999L))
                .stripeAccountId(stripeAcc)
                .build();
        when(payoutSettingsRepository.findByCreatorId(any())).thenReturn(Optional.of(mySettings));
        when(payoutSettingsRepository.findAllByStripeAccountId(stripeAcc)).thenReturn(List.of(mySettings, otherSettings));

        AnalyticsEvent regEvent = new AnalyticsEvent();
        regEvent.setCreatedAt(Instant.now().minus(1, ChronoUnit.DAYS));
        when(analyticsEventRepository.findFirstByUserIdAndEventTypeOrderByCreatedAtDesc(userId, AnalyticsEventType.USER_REGISTERED))
                .thenReturn(Optional.of(regEvent));

        AmlResult result = amlRuleEngineService.analyze(user, new BigDecimal("50.00"));

        assertEquals(100, result.getRiskScore());
        assertEquals(3, result.getTriggeredRules().size());
    }
}








