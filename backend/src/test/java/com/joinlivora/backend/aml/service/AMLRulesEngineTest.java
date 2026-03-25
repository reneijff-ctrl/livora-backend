package com.joinlivora.backend.aml.service;

import com.joinlivora.backend.aml.dto.AmlResult;
import com.joinlivora.backend.aml.model.AMLRule;
import com.joinlivora.backend.aml.model.RiskScore;
import com.joinlivora.backend.aml.repository.AMLRuleRepository;
import com.joinlivora.backend.aml.repository.RiskScoreRepository;
import com.joinlivora.backend.analytics.AnalyticsEvent;
import com.joinlivora.backend.analytics.AnalyticsEventRepository;
import com.joinlivora.backend.monetization.TipRepository;
import com.joinlivora.backend.monetization.TipStatus;
import com.joinlivora.backend.payment.PaymentRepository;
import com.joinlivora.backend.payout.CreatorPayoutSettings;
import com.joinlivora.backend.payout.CreatorPayoutSettingsRepository;
import com.joinlivora.backend.payouts.service.PayoutFreezeService;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AMLRulesEngineTest {

    @Mock
    private AMLRuleRepository amlRuleRepository;
    @Mock
    private RiskScoreRepository riskScoreRepository;
    @Mock
    private PayoutFreezeService payoutFreezeService;
    @Mock
    private TipRepository tipRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private CreatorPayoutSettingsRepository payoutSettingsRepository;
    @Mock
    private AnalyticsEventRepository analyticsEventRepository;
    @Mock
    private com.joinlivora.backend.user.UserRepository userRepository;
    @Mock
    private com.joinlivora.backend.audit.service.AuditService auditService;

    @InjectMocks
    private AMLRulesEngine amlRulesEngine;

    private User user;
    private UUID userUuid;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(123L);
        user.setEmail("test@example.com");
        userUuid = new UUID(0L, 123L);
        lenient().when(riskScoreRepository.findTopByUserIdOrderByLastEvaluatedAtDesc(any())).thenReturn(Optional.empty());
    }

    @Test
    void calculateRiskScore_ShouldWork() {
        when(userRepository.findById(123L)).thenReturn(Optional.of(user));
        when(amlRuleRepository.findAll()).thenReturn(List.of());

        AmlResult result = amlRulesEngine.calculateRiskScore(userUuid);

        assertThat(result.getRiskScore()).isEqualTo(0);
        assertThat(result.getRiskLevel()).isEqualTo("LOW");
    }

    @Test
    void calculateRiskLevel_CheckBoundaries() {
        AMLRule rule = AMLRule.builder().code("RAPID_PAYOUT_AFTER_TIPS").enabled(true).build();
        when(amlRuleRepository.findAll()).thenReturn(List.of(rule));
        when(tipRepository.countByCreatorUserIdAndStatusAndCreatedAtAfter(any(), any(), any())).thenReturn(1L);

        // 20 -> LOW
        rule.setThreshold(20);
        assertThat(amlRulesEngine.evaluateRules(user, BigDecimal.ZERO).getRiskLevel()).isEqualTo("LOW");

        // 21 -> MEDIUM
        rule.setThreshold(21);
        assertThat(amlRulesEngine.evaluateRules(user, BigDecimal.ZERO).getRiskLevel()).isEqualTo("MEDIUM");

        // 50 -> MEDIUM
        rule.setThreshold(50);
        assertThat(amlRulesEngine.evaluateRules(user, BigDecimal.ZERO).getRiskLevel()).isEqualTo("MEDIUM");

        // 51 -> HIGH
        rule.setThreshold(51);
        assertThat(amlRulesEngine.evaluateRules(user, BigDecimal.ZERO).getRiskLevel()).isEqualTo("HIGH");
        verify(auditService).logEvent(isNull(), eq("AML_FLAGGED"), eq("USER"), eq(userUuid), any(), isNull(), isNull());

        // 80 -> HIGH
        rule.setThreshold(80);
        AmlResult result = amlRulesEngine.evaluateRules(user, BigDecimal.ZERO);
        assertThat(result.getRiskLevel()).isEqualTo("HIGH");
        verify(payoutFreezeService, never()).freezeCreator(any(), any(), any());
        verify(auditService, times(2)).logEvent(isNull(), eq("AML_FLAGGED"), eq("USER"), eq(userUuid), any(), isNull(), isNull());

        // 81 -> CRITICAL
        rule.setThreshold(81);
        AmlResult result81 = amlRulesEngine.evaluateRules(user, BigDecimal.ZERO);
        assertThat(result81.getRiskLevel()).isEqualTo("CRITICAL");
        verify(payoutFreezeService).freezeCreator(eq(userUuid), any(), any());
        verify(auditService).logEvent(isNull(), eq("AML_PAYOUT_FROZEN"), eq("USER"), eq(userUuid), any(), isNull(), isNull());
    }

    @Test
    void evaluateRules_ShouldUpdateExistingScore() {
        AMLRule rule = AMLRule.builder()
                .code("RAPID_PAYOUT_AFTER_TIPS")
                .threshold(40)
                .enabled(true)
                .build();

        RiskScore existingScore = RiskScore.builder()
                .id(UUID.randomUUID())
                .userId(userUuid)
                .score(10)
                .level("LOW")
                .lastEvaluatedAt(Instant.now().minus(1, java.time.temporal.ChronoUnit.DAYS))
                .build();

        when(amlRuleRepository.findAll()).thenReturn(List.of(rule));
        when(tipRepository.countByCreatorUserIdAndStatusAndCreatedAtAfter(any(), any(), any())).thenReturn(1L);
        when(riskScoreRepository.findTopByUserIdOrderByLastEvaluatedAtDesc(userUuid)).thenReturn(Optional.of(existingScore));

        AmlResult result = amlRulesEngine.evaluateRules(user, BigDecimal.ZERO);

        assertThat(result.getRiskScore()).isEqualTo(40);
        
        // Verify we saved the SAME object (same ID)
        verify(riskScoreRepository).save(existingScore);
        assertThat(existingScore.getScore()).isEqualTo(40);
        assertThat(existingScore.getLevel()).isEqualTo("MEDIUM");
    }

    @Test
    void evaluateRules_ShouldTriggerRuleAndSaveScore() {
        AMLRule rule = AMLRule.builder()
                .code("RAPID_PAYOUT_AFTER_TIPS")
                .threshold(40)
                .enabled(true)
                .build();

        when(amlRuleRepository.findAll()).thenReturn(List.of(rule));
        when(tipRepository.countByCreatorUserIdAndStatusAndCreatedAtAfter(any(), any(), any())).thenReturn(1L);
        when(riskScoreRepository.findTopByUserIdOrderByLastEvaluatedAtDesc(userUuid)).thenReturn(Optional.empty());

        AmlResult result = amlRulesEngine.evaluateRules(user, new BigDecimal("50.00"));

        assertThat(result.getRiskScore()).isEqualTo(40);
        assertThat(result.getRiskLevel()).isEqualTo("MEDIUM");
        assertThat(result.getTriggeredRules()).contains("RAPID_PAYOUT_AFTER_TIPS");

        verify(riskScoreRepository).save(argThat(rs -> rs.getScore() == 40 && rs.getUserId().equals(userUuid)));
        verify(payoutFreezeService, never()).freezeCreator(any(), any(), any());
    }

    @Test
    void evaluateRules_ShouldFreezeWhenThresholdExceeded() {
        AMLRule rule1 = AMLRule.builder().code("RAPID_PAYOUT_AFTER_TIPS").threshold(50).enabled(true).build();
        AMLRule rule2 = AMLRule.builder().code("NEW_ACCOUNT_PAYOUT").threshold(40).enabled(true).build();

        when(amlRuleRepository.findAll()).thenReturn(List.of(rule1, rule2));
        when(tipRepository.countByCreatorUserIdAndStatusAndCreatedAtAfter(any(), any(), any())).thenReturn(1L);
        AnalyticsEvent event = new AnalyticsEvent();
        event.setCreatedAt(Instant.now());
        when(analyticsEventRepository.findFirstByUserIdAndEventTypeOrderByCreatedAtDesc(anyLong(), any()))
                .thenReturn(Optional.of(event));

        AmlResult result = amlRulesEngine.evaluateRules(user, new BigDecimal("100.00"));

        assertThat(result.getRiskScore()).isEqualTo(90);
        assertThat(result.getRiskLevel()).isEqualTo("CRITICAL");
        
        verify(payoutFreezeService).freezeCreator(eq(userUuid), contains("AML Critical Risk"), eq("SYSTEM_AML"));
    }

    @Test
    void evaluateRules_HighRisk_ShouldSetManualReviewStatus() {
        AMLRule rule = AMLRule.builder()
                .code("RAPID_PAYOUT_AFTER_TIPS")
                .threshold(60)
                .enabled(true)
                .build();

        when(amlRuleRepository.findAll()).thenReturn(List.of(rule));
        when(tipRepository.countByCreatorUserIdAndStatusAndCreatedAtAfter(any(), any(), any())).thenReturn(1L);
        
        user.setStatus(UserStatus.ACTIVE);

        AmlResult result = amlRulesEngine.evaluateRules(user, BigDecimal.ZERO);

        assertThat(result.getRiskLevel()).isEqualTo("HIGH");
        assertThat(user.getStatus()).isEqualTo(UserStatus.MANUAL_REVIEW);
        verify(userRepository).save(user);
    }

    @Test
    void isSharedStripeAccount_ShouldReturnTrueWhenStripeIdIsShared() {
        AMLRule rule = AMLRule.builder().code("REPEATED_PAYOUTS_TO_SAME_BANK").threshold(50).enabled(true).build();
        when(amlRuleRepository.findAll()).thenReturn(List.of(rule));

        CreatorPayoutSettings settings = CreatorPayoutSettings.builder()
                .creatorId(userUuid)
                .stripeAccountId("acct_123")
                .build();
        when(payoutSettingsRepository.findByCreatorId(userUuid)).thenReturn(Optional.of(settings));

        CreatorPayoutSettings otherSettings = CreatorPayoutSettings.builder()
                .creatorId(new UUID(0L, 456L))
                .stripeAccountId("acct_123")
                .build();
        when(payoutSettingsRepository.findAllByStripeAccountId("acct_123")).thenReturn(List.of(settings, otherSettings));

        AmlResult result = amlRulesEngine.evaluateRules(user, new BigDecimal("50.00"));
        
        assertThat(result.getTriggeredRules()).contains("REPEATED_PAYOUTS_TO_SAME_BANK");
    }

    @Test
    void evaluateRules_ShouldTriggerHighTipVelocity() {
        AMLRule rule = AMLRule.builder().code("HIGH_TIP_VELOCITY").threshold(30).enabled(true).build();
        when(amlRuleRepository.findAll()).thenReturn(List.of(rule));
        when(tipRepository.countByCreatorUserIdAndStatusAndCreatedAtAfter(any(), eq(TipStatus.COMPLETED), any())).thenReturn(21L);

        AmlResult result = amlRulesEngine.evaluateRules(user, new BigDecimal("50.00"));

        assertThat(result.getTriggeredRules()).contains("HIGH_TIP_VELOCITY");
        assertThat(result.getRiskScore()).isEqualTo(30);
    }

    @Test
    void evaluateRules_ShouldTriggerLargeSingleTip() {
        AMLRule rule = AMLRule.builder().code("LARGE_SINGLE_TIP").threshold(40).enabled(true).build();
        when(amlRuleRepository.findAll()).thenReturn(List.of(rule));
        when(tipRepository.existsByCreatorUserIdAndStatusAndAmountGreaterThan(any(), eq(TipStatus.COMPLETED), any())).thenReturn(true);

        AmlResult result = amlRulesEngine.evaluateRules(user, new BigDecimal("50.00"));

        assertThat(result.getTriggeredRules()).contains("LARGE_SINGLE_TIP");
    }

    @Test
    void evaluateRules_ShouldTriggerRapidBalanceGrowth() {
        AMLRule rule = AMLRule.builder().code("RAPID_BALANCE_GROWTH").threshold(50).enabled(true).build();
        when(amlRuleRepository.findAll()).thenReturn(List.of(rule));
        when(tipRepository.sumAmountByCreatorUserIdAndStatusAndCreatedAtAfter(any(), eq(TipStatus.COMPLETED), any()))
                .thenReturn(new BigDecimal("2500"));

        AmlResult result = amlRulesEngine.evaluateRules(user, new BigDecimal("50.00"));

        assertThat(result.getTriggeredRules()).contains("RAPID_BALANCE_GROWTH");
    }

    @Test
    void evaluateRules_ShouldTriggerRepeatedFailedPayments() {
        AMLRule rule = AMLRule.builder().code("REPEATED_FAILED_PAYMENTS").threshold(45).enabled(true).build();
        when(amlRuleRepository.findAll()).thenReturn(List.of(rule));
        when(paymentRepository.countByUserIdAndSuccessAndCreatedAtAfter(anyLong(), eq(false), any())).thenReturn(6L);

        AmlResult result = amlRulesEngine.evaluateRules(user, new BigDecimal("50.00"));

        assertThat(result.getTriggeredRules()).contains("REPEATED_FAILED_PAYMENTS");
    }
}








