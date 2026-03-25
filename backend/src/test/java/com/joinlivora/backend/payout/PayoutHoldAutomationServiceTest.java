package com.joinlivora.backend.payout;

import com.joinlivora.backend.analytics.CreatorStatsRepository;
import com.joinlivora.backend.fraud.model.RiskProfile;
import com.joinlivora.backend.fraud.model.RiskSubjectType;
import com.joinlivora.backend.fraud.service.RiskProfileService;
import com.joinlivora.backend.payment.ChargebackHistoryService;
import com.joinlivora.backend.payout.dto.PayoutHoldDecision;
import com.joinlivora.backend.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PayoutHoldAutomationServiceTest {

    @Mock
    private PayoutHoldDecisionService decisionService;
    @Mock
    private RiskProfileService riskProfileService;
    @Mock
    private ChargebackHistoryService chargebackHistoryService;
    @Mock
    private CreatorStatsRepository creatorStatsRepository;
    @Mock
    private LegacyCreatorProfileRepository creatorProfileRepository;
    @Mock
    private PayoutHoldPolicyRepository holdPolicyRepository;
    @Mock
    private PayoutHoldAuditService holdAuditService;

    @InjectMocks
    private PayoutHoldAutomationService automationService;

    private User user;
    private UUID subjectId;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setEmail("creator@test.com");
        subjectId = new UUID(0L, 1L);
    }

    @Test
    void evaluateAndApplyHold_HighRisk_ShouldApplyHoldAndLog() {
        // Given
        when(riskProfileService.generateRiskProfile(subjectId))
                .thenReturn(RiskProfile.builder().riskScore(70).build());
        when(chargebackHistoryService.getChargebackRiskScore(1L)).thenReturn(0);
        when(creatorProfileRepository.findByUser(user)).thenReturn(Optional.empty());

        PayoutHoldDecision decision = PayoutHoldDecision.builder()
                .holdLevel(HoldLevel.LONG)
                .holdDays(14)
                .reason("High risk")
                .build();
        when(decisionService.decide(eq(70), anyDouble(), anyInt(), any(BigDecimal.class)))
                .thenReturn(decision);

        when(holdPolicyRepository.findAllBySubjectIdAndSubjectTypeOrderByCreatedAtDesc(subjectId, RiskSubjectType.CREATOR))
                .thenReturn(Collections.emptyList());

        // When
        automationService.evaluateAndApplyHold(user);

        // Then
        verify(holdPolicyRepository).save(any(PayoutHoldPolicy.class));
        verify(holdAuditService).logHoldApplied(eq(RiskSubjectType.CREATOR), eq(subjectId), 
                isNull(), eq(HoldLevel.LONG), eq(14), any(Instant.class), eq("High risk"));
    }

    @Test
    void evaluateAndApplyHold_NoRisk_ShouldNotApplyHold() {
        // Given
        when(riskProfileService.generateRiskProfile(subjectId))
                .thenReturn(RiskProfile.builder().riskScore(10).build());
        when(chargebackHistoryService.getChargebackRiskScore(1L)).thenReturn(0);
        when(creatorProfileRepository.findByUser(user)).thenReturn(Optional.empty());

        PayoutHoldDecision decision = PayoutHoldDecision.builder()
                .holdLevel(HoldLevel.NONE)
                .holdDays(0)
                .build();
        when(decisionService.decide(eq(10), anyDouble(), anyInt(), any(BigDecimal.class)))
                .thenReturn(decision);

        // When
        automationService.evaluateAndApplyHold(user);

        // Then
        verify(holdPolicyRepository, never()).save(any());
        verify(holdAuditService, never()).logHoldApplied(any(), any(), any(), any(), anyInt(), any(), any());
    }
}








