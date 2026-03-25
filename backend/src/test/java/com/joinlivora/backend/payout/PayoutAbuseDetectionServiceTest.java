package com.joinlivora.backend.payout;

import com.joinlivora.backend.fraud.dto.RiskDecisionResult;
import com.joinlivora.backend.fraud.model.FraudSignalType;
import com.joinlivora.backend.fraud.model.RiskDecision;
import com.joinlivora.backend.fraud.service.RiskDecisionEngine;
import com.joinlivora.backend.payment.AutoFreezePolicyService;
import com.joinlivora.backend.payout.dto.AmlResult;
import com.joinlivora.backend.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PayoutAbuseDetectionServiceTest {

    @Mock
    private AmlRuleEngineService amlRuleEngineService;
    @Mock
    private PayoutRiskRepository payoutRiskRepository;
    @Mock
    private AutoFreezePolicyService autoFreezePolicyService;
    @Mock
    private AmlAuditService amlAuditService;
    @Mock
    private com.joinlivora.backend.monetization.CreatorTrustService creatorTrustService;
    @Mock
    private RiskDecisionEngine riskDecisionEngine;
    @Mock
    private org.springframework.beans.factory.ObjectProvider<PayoutAbuseDetectionService> selfProvider;

    @InjectMocks
    private PayoutAbuseDetectionService payoutAbuseDetectionService;

    private User user;
    private BigDecimal amount;

    @BeforeEach
    void setUp() {
        lenient().when(selfProvider.getIfAvailable()).thenReturn(payoutAbuseDetectionService);
        user = new User();
        user.setId(1L);
        user.setEmail("test@test.com");
        amount = new BigDecimal("100.00");
    }

    @Test
    void detect_LowRisk_ShouldJustPersist() {
        AmlResult lowRisk = new AmlResult(30, List.of("NEW_ACCOUNT_PAYOUT"));
        when(amlRuleEngineService.analyze(user, amount)).thenReturn(lowRisk);
        when(riskDecisionEngine.evaluate(any(), any(), eq(30), any()))
                .thenReturn(RiskDecisionResult.builder().decision(RiskDecision.LIMIT).build());

        payoutAbuseDetectionService.detect(user, amount);

        verify(creatorTrustService).evaluateTrust(user);
        verify(payoutRiskRepository).save(any(PayoutRisk.class));
        verify(amlAuditService).audit(user, amount, lowRisk, false);
        verify(autoFreezePolicyService, never()).suspendAccount(any(), any(), any());
    }

    @Test
    void detect_HighRisk_ShouldBlockPayout() {
        AmlResult highRisk = new AmlResult(75, List.of("RAPID_PAYOUT_AFTER_TIPS", "HIGH_PAYOUT_LOW_CHAT_RATIO"));
        when(amlRuleEngineService.analyze(user, amount)).thenReturn(highRisk);
        when(riskDecisionEngine.evaluate(any(), any(), eq(75), any()))
                .thenReturn(RiskDecisionResult.builder().decision(RiskDecision.BLOCK).explanationId(UUID.randomUUID()).build());

        assertThrows(AccessDeniedException.class, () -> 
                payoutAbuseDetectionService.detect(user, amount));

        verify(payoutRiskRepository).save(any(PayoutRisk.class));
        verify(amlAuditService).audit(user, amount, highRisk, true);
        verify(autoFreezePolicyService, never()).suspendAccount(any(), any(), any());
    }

    @Test
    void detect_ExtremeRisk_ShouldBlockAndSuspend() {
        AmlResult extremeRisk = new AmlResult(95, List.of("RAPID_PAYOUT_AFTER_TIPS", "REPEATED_PAYOUTS_TO_SAME_BANK", "NEW_ACCOUNT_PAYOUT"));
        when(amlRuleEngineService.analyze(user, amount)).thenReturn(extremeRisk);
        when(riskDecisionEngine.evaluate(any(), any(), eq(95), any()))
                .thenReturn(RiskDecisionResult.builder().decision(RiskDecision.BLOCK).explanationId(UUID.randomUUID()).build());

        assertThrows(AccessDeniedException.class, () -> 
                payoutAbuseDetectionService.detect(user, amount));

        ArgumentCaptor<PayoutRisk> riskCaptor = ArgumentCaptor.forClass(PayoutRisk.class);
        verify(payoutRiskRepository).save(riskCaptor.capture());
        assertThat(riskCaptor.getValue().getRiskScore()).isEqualTo(95);

        verify(amlAuditService).audit(user, amount, extremeRisk, true);
        verify(autoFreezePolicyService).suspendAccount(eq(user), contains("Extreme AML Risk"), eq(FraudSignalType.AML_HIGH_RISK));
    }
}








