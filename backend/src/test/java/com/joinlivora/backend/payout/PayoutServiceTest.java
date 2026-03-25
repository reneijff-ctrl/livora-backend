package com.joinlivora.backend.payout;

import com.joinlivora.backend.common.exception.KycNotApprovedException;
import com.joinlivora.backend.creator.verification.KycAccessService;
import com.joinlivora.backend.exception.PayoutRestrictedException;
import com.joinlivora.backend.fraud.dto.RiskDecisionResult;
import com.joinlivora.backend.fraud.model.RiskDecision;
import com.joinlivora.backend.fraud.model.RiskProfile;
import com.joinlivora.backend.fraud.service.RiskDecisionEngine;
import com.joinlivora.backend.fraud.service.RiskProfileService;
import com.joinlivora.backend.payout.dto.PayoutFrequency;
import com.joinlivora.backend.payout.dto.PayoutLimit;
import com.joinlivora.backend.token.CreatorEarnings;
import com.joinlivora.backend.token.CreatorEarningsRepository;
import com.joinlivora.backend.token.TokenService;
import com.joinlivora.backend.user.User;
import com.stripe.StripeClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PayoutServiceTest {

    @Mock
    private PayoutRepository payoutRepository;
    @Mock
    private CreatorPayoutRepository creatorPayoutRepository;
    @Mock
    private CreatorPayoutSettingsRepository creatorPayoutSettingsRepository;
    @Mock
    private StripeAccountRepository stripeAccountRepository;
    @Mock
    private TokenService tokenService;
    @Mock
    private StripeClient stripeClient;
    @Mock
    private CreatorEarningRepository creatorEarningRepository;
    @Mock
    private CreatorEarningsRepository creatorEarningsRepository;
    @Mock
    private com.joinlivora.backend.user.UserRepository userRepository;
    @Mock
    private com.joinlivora.backend.payment.AutoFreezePolicyService autoFreezePolicyService;
    @Mock
    private PayoutAbuseDetectionService payoutAbuseDetectionService;
    @Mock
    private RiskProfileService riskProfileService;
    @Mock
    private RiskDecisionEngine riskDecisionEngine;
    @Mock
    private PayoutLimitPolicy payoutLimitPolicy;
    @Mock
    private CreatorPayoutStateRepository creatorPayoutStateRepository;
    @Mock
    private PayoutPolicyAuditService payoutPolicyAuditService;
    @Mock
    private StripePayoutAdapter stripePayoutAdapter;
    @Mock
    private PayoutHoldAutomationService payoutHoldAutomationService;
    @Mock
    private com.joinlivora.backend.fraud.service.FraudScoreService fraudScoreService;
    @Mock
    private com.joinlivora.backend.fraud.repository.FraudFlagRepository fraudFlagRepository;
    @Mock
    private com.joinlivora.backend.audit.service.AuditService auditService;
    @Mock
    private PayoutAuditService payoutAuditService;
    @Mock
    private KycAccessService kycAccessService;

    @InjectMocks
    private PayoutService payoutService;

    private User user;
    private UUID creatorId;
    private CreatorEarnings earnings;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setEmail("creator@test.com");

        creatorId = new UUID(0L, user.getId());
        earnings = CreatorEarnings.builder()
                .id(creatorId)
                .user(user)
                .build();
        
        lenient().when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        lenient().when(creatorEarningsRepository.findByUserWithLock(user)).thenReturn(Optional.of(earnings));
        lenient().when(creatorEarningRepository.sumTotalNetTokensByCreator(user)).thenReturn(new BigDecimal("1000000"));
        lenient().when(creatorEarningRepository.sumTotalNetRevenueByCreator(user)).thenReturn(new BigDecimal("1000000"));
    }

    @Test
    void calculateAvailablePayout_ShouldFollowRules() {
        // Given
        // Sum net earnings: 1000 tokens + 50.00 EUR
        // 1000 tokens * 0.01 = 10.00 EUR
        // Total earnings = 60.00 EUR
        when(creatorEarningRepository.sumTotalNetTokensByCreator(user)).thenReturn(new BigDecimal("1000"));
        when(creatorEarningRepository.sumTotalNetRevenueByCreator(user)).thenReturn(new BigDecimal("50.00"));

        // Subtract already paid payouts: 20.00 EUR
        when(creatorPayoutRepository.sumAmountByCreatorIdAndStatus(creatorId, PayoutStatus.COMPLETED)).thenReturn(new BigDecimal("20.00"));

        // Exclude pending transactions: 10.00 EUR
        when(creatorPayoutRepository.sumAmountByCreatorIdAndStatus(creatorId, PayoutStatus.PENDING)).thenReturn(new BigDecimal("10.00"));

        // Calculation: 60.00 - 20.00 - 10.00 = 30.00
        
        // When
        BigDecimal available = payoutService.calculateAvailablePayout(creatorId);

        // Then
        assertEquals(0, new BigDecimal("30.00").compareTo(available));
    }

    @Test
    void calculateAvailablePayout_NullHandling() {
        // Given
        when(creatorEarningRepository.sumTotalNetTokensByCreator(user)).thenReturn(null);
        when(creatorEarningRepository.sumTotalNetRevenueByCreator(user)).thenReturn(null);
        when(creatorPayoutRepository.sumAmountByCreatorIdAndStatus(creatorId, PayoutStatus.COMPLETED)).thenReturn(BigDecimal.ZERO);
        when(creatorPayoutRepository.sumAmountByCreatorIdAndStatus(creatorId, PayoutStatus.PENDING)).thenReturn(BigDecimal.ZERO);

        // When
        BigDecimal available = payoutService.calculateAvailablePayout(creatorId);

        // Then
        assertEquals(0, BigDecimal.ZERO.compareTo(available));
    }

    @Test
    void executePayout_Success() throws Exception {
        // Given
        mockNoLimit();
        BigDecimal amount = new BigDecimal("100.00");
        String currency = "EUR";
        CreatorPayoutSettings settings = CreatorPayoutSettings.builder()
                .creatorId(creatorId)
                .enabled(true)
                .minimumPayoutAmount(new BigDecimal("50.00"))
                .stripeAccountId("acct_123")
                .build();

        when(creatorPayoutSettingsRepository.findByCreatorId(creatorId)).thenReturn(Optional.of(settings));
        when(creatorPayoutRepository.save(any(CreatorPayout.class))).thenAnswer(i -> {
            CreatorPayout p = i.getArgument(0);
            if (p.getId() == null) p.setId(UUID.randomUUID());
            return p;
        });

        com.stripe.service.TransferService transferService = mock(com.stripe.service.TransferService.class);
        when(stripeClient.transfers()).thenReturn(transferService);
        com.stripe.model.Transfer transfer = new com.stripe.model.Transfer();
        transfer.setId("tr_123");
        when(transferService.create(any(com.stripe.param.TransferCreateParams.class))).thenReturn(transfer);

        // When
        CreatorPayout result = payoutService.executePayout(creatorId, amount, currency);

        // Then
        assertNotNull(result);
        assertEquals(PayoutStatus.COMPLETED, result.getStatus());
        assertEquals("tr_123", result.getStripeTransferId());
        assertNotNull(result.getCompletedAt());
        verify(payoutAbuseDetectionService).detect(user, amount);
        verify(creatorPayoutRepository, times(2)).save(any(CreatorPayout.class));
        verify(payoutPolicyAuditService).logAutoDecision(eq(creatorId), eq(10), any(), any());
    }

    @Test
    void executePayout_BelowMinimum_ShouldThrowException() {
        // Given
        mockNoLimit();
        BigDecimal amount = new BigDecimal("40.00");
        CreatorPayoutSettings settings = CreatorPayoutSettings.builder()
                .creatorId(creatorId)
                .enabled(true)
                .minimumPayoutAmount(new BigDecimal("50.00"))
                .build();

        when(creatorPayoutSettingsRepository.findByCreatorId(creatorId)).thenReturn(Optional.of(settings));

        // When & Then
        Exception ex = assertThrows(RuntimeException.class, () ->
                payoutService.executePayout(creatorId, amount, "EUR"));
        assertTrue(ex.getMessage().contains("below minimum"));
    }

    @Test
    void executePayout_Disabled_ShouldThrowException() {
        // Given
        mockNoLimit();
        CreatorPayoutSettings settings = CreatorPayoutSettings.builder()
                .creatorId(creatorId)
                .enabled(false)
                .build();

        when(creatorPayoutSettingsRepository.findByCreatorId(creatorId)).thenReturn(Optional.of(settings));

        // When & Then
        Exception ex = assertThrows(RuntimeException.class, () ->
                payoutService.executePayout(creatorId, new BigDecimal("100.00"), "EUR"));
        assertTrue(ex.getMessage().contains("disabled"));
    }

    @Test
    void executePayout_StripeFailure_ShouldSetStatusFailed() throws Exception {
        // Given
        mockNoLimit();
        BigDecimal amount = new BigDecimal("100.00");
        String currency = "EUR";
        CreatorPayoutSettings settings = CreatorPayoutSettings.builder()
                .creatorId(creatorId)
                .enabled(true)
                .minimumPayoutAmount(new BigDecimal("50.00"))
                .stripeAccountId("acct_123")
                .build();

        when(creatorPayoutSettingsRepository.findByCreatorId(creatorId)).thenReturn(Optional.of(settings));
        when(creatorPayoutRepository.save(any(CreatorPayout.class))).thenAnswer(i -> {
            CreatorPayout p = i.getArgument(0);
            if (p.getId() == null) p.setId(UUID.randomUUID());
            return p;
        });

        com.stripe.service.TransferService transferService = mock(com.stripe.service.TransferService.class);
        when(stripeClient.transfers()).thenReturn(transferService);
        when(transferService.create(any(com.stripe.param.TransferCreateParams.class)))
                .thenThrow(new RuntimeException("Stripe connection error"));

        // When & Then
        assertThrows(RuntimeException.class, () ->
                payoutService.executePayout(creatorId, amount, currency));

        verify(creatorPayoutRepository, atLeastOnce()).save(argThat(p -> 
                p.getStatus() == PayoutStatus.FAILED && 
                "Stripe connection error".equals(p.getFailureReason())));
    }

    @Test
    void executePayout_WhenAbuseDetected_ShouldThrowException() {
        // Given
        BigDecimal amount = new BigDecimal("100.00");
        CreatorPayoutSettings settings = CreatorPayoutSettings.builder()
                .creatorId(creatorId)
                .enabled(true)
                .build();

        when(creatorPayoutSettingsRepository.findByCreatorId(creatorId)).thenReturn(Optional.of(settings));
        
        doThrow(new org.springframework.security.access.AccessDeniedException("Abuse detected"))
                .when(payoutAbuseDetectionService).detect(user, amount);

        // When & Then
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () ->
                payoutService.executePayout(creatorId, amount, "EUR"));
    }

    @Test
    void executePayout_WhenFrozen_ShouldThrowException() {
        // Given
        CreatorPayoutSettings settings = CreatorPayoutSettings.builder()
                .creatorId(creatorId)
                .enabled(true)
                .build();

        when(creatorPayoutSettingsRepository.findByCreatorId(creatorId)).thenReturn(Optional.of(settings));
        
        doThrow(new org.springframework.security.access.AccessDeniedException("Account is restricted."))
                .when(autoFreezePolicyService).validateUserStatus(user);

        // When & Then
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () ->
                payoutService.executePayout(creatorId, new BigDecimal("100.00"), "EUR"));
    }

    @Test
    void executePayout_HighRisk_ShouldPausePayouts() {
        // Given
        RiskProfile riskProfile = RiskProfile.builder()
                .userId(creatorId)
                .riskScore(85)
                .build();
        when(riskProfileService.generateRiskProfile(creatorId)).thenReturn(riskProfile);
        when(riskDecisionEngine.evaluate(any(), any()))
                .thenReturn(RiskDecisionResult.builder().decision(RiskDecision.BLOCK).explanationId(UUID.randomUUID()).build());

        PayoutLimit limit = PayoutLimit.builder()
                .payoutFrequency(PayoutFrequency.PAUSED)
                .maxPayoutAmount(BigDecimal.ZERO)
                .reason("High risk score >= 80")
                .build();
        when(payoutLimitPolicy.getLimit(85)).thenReturn(limit);
        when(creatorPayoutStateRepository.findByCreatorId(creatorId)).thenReturn(Optional.empty());

        CreatorPayoutSettings settings = CreatorPayoutSettings.builder()
                .creatorId(creatorId)
                .enabled(true)
                .build();
        when(creatorPayoutSettingsRepository.findByCreatorId(creatorId)).thenReturn(Optional.of(settings));

        // When & Then
        PayoutRestrictedException ex = assertThrows(PayoutRestrictedException.class, () ->
                payoutService.executePayout(creatorId, new BigDecimal("100.00"), "EUR"));
        assertTrue(ex.getMessage().contains("paused"));
    }

    @Test
    void executePayout_MediumRisk_DailyLimitExceeded_ShouldThrowException() {
        // Given
        RiskProfile riskProfile = RiskProfile.builder()
                .userId(creatorId)
                .riskScore(45)
                .build();
        when(riskProfileService.generateRiskProfile(creatorId)).thenReturn(riskProfile);
        when(riskDecisionEngine.evaluate(any(), any()))
                .thenReturn(RiskDecisionResult.builder().decision(RiskDecision.LIMIT).explanationId(UUID.randomUUID()).build());

        PayoutLimit limit = PayoutLimit.builder()
                .payoutFrequency(PayoutFrequency.DAILY)
                .maxPayoutAmount(new BigDecimal("100.00"))
                .reason("Risk score 30-59")
                .build();
        when(payoutLimitPolicy.getLimit(45)).thenReturn(limit);
        when(creatorPayoutStateRepository.findByCreatorId(creatorId)).thenReturn(Optional.empty());

        // Already paid 60 today
        when(creatorPayoutRepository.sumPaidAmountByCreatorIdAndCreatedAtAfter(eq(creatorId), any(java.time.Instant.class)))
                .thenReturn(new BigDecimal("60.00"));

        CreatorPayoutSettings settings = CreatorPayoutSettings.builder()
                .creatorId(creatorId)
                .enabled(true)
                .build();
        when(creatorPayoutSettingsRepository.findByCreatorId(creatorId)).thenReturn(Optional.of(settings));

        // When & Then
        // Requesting 50, but 60 + 50 = 110 > 100
        PayoutRestrictedException ex = assertThrows(PayoutRestrictedException.class, () ->
                payoutService.executePayout(creatorId, new BigDecimal("50.00"), "EUR"));
        assertTrue(ex.getMessage().contains("limit exceeded"));
    }

    @Test
    void executePayout_MediumRisk_WithinLimit_ShouldSucceed() throws Exception {
        // Given
        RiskProfile riskProfile = RiskProfile.builder()
                .userId(creatorId)
                .riskScore(45)
                .build();
        when(riskProfileService.generateRiskProfile(creatorId)).thenReturn(riskProfile);
        when(riskDecisionEngine.evaluate(any(), any()))
                .thenReturn(RiskDecisionResult.builder().decision(RiskDecision.LIMIT).explanationId(UUID.randomUUID()).build());

        PayoutLimit limit = PayoutLimit.builder()
                .payoutFrequency(PayoutFrequency.DAILY)
                .maxPayoutAmount(new BigDecimal("100.00"))
                .reason("Risk score 30-59")
                .build();
        when(payoutLimitPolicy.getLimit(45)).thenReturn(limit);
        when(creatorPayoutStateRepository.findByCreatorId(creatorId)).thenReturn(Optional.empty());

        // Already paid 30 today
        when(creatorPayoutRepository.sumPaidAmountByCreatorIdAndCreatedAtAfter(eq(creatorId), any(java.time.Instant.class)))
                .thenReturn(new BigDecimal("30.00"));

        CreatorPayoutSettings settings = CreatorPayoutSettings.builder()
                .creatorId(creatorId)
                .enabled(true)
                .stripeAccountId("acct_123")
                .build();
        when(creatorPayoutSettingsRepository.findByCreatorId(creatorId)).thenReturn(Optional.of(settings));
        when(creatorPayoutRepository.save(any(CreatorPayout.class))).thenAnswer(i -> {
            CreatorPayout p = i.getArgument(0);
            if (p.getId() == null) p.setId(UUID.randomUUID());
            return p;
        });

        com.stripe.service.TransferService transferService = mock(com.stripe.service.TransferService.class);
        when(stripeClient.transfers()).thenReturn(transferService);
        com.stripe.model.Transfer transfer = new com.stripe.model.Transfer();
        transfer.setId("tr_456");
        when(transferService.create(any(com.stripe.param.TransferCreateParams.class))).thenReturn(transfer);

        // When
        // Requesting 50, 30 + 50 = 80 <= 100. Should succeed.
        CreatorPayout payout = payoutService.executePayout(creatorId, new BigDecimal("50.00"), "EUR");

        // Then
        assertNotNull(payout);
        assertEquals(PayoutStatus.COMPLETED, payout.getStatus());
    }

    @Test
    void validatePayoutLimit_ManualOverride_ShouldSkipAutoUpdate() throws Exception {
        // Given
        BigDecimal amount = new BigDecimal("100.00");
        CreatorPayoutSettings settings = CreatorPayoutSettings.builder()
                .creatorId(creatorId)
                .enabled(true)
                .stripeAccountId("acct_123")
                .build();

        CreatorPayoutState state = CreatorPayoutState.builder()
                .creatorId(creatorId)
                .status(PayoutStateStatus.ACTIVE)
                .frequency(PayoutFrequency.NO_LIMIT)
                .manualOverride(true)
                .build();

        RiskProfile riskProfile = RiskProfile.builder()
                .userId(creatorId)
                .riskScore(90) // High risk
                .build();

        PayoutLimit highRiskLimit = PayoutLimit.builder()
                .payoutFrequency(PayoutFrequency.PAUSED)
                .maxPayoutAmount(BigDecimal.ZERO)
                .build();

        when(creatorPayoutSettingsRepository.findByCreatorId(creatorId)).thenReturn(Optional.of(settings));
        when(riskProfileService.generateRiskProfile(creatorId)).thenReturn(riskProfile);
        when(riskDecisionEngine.evaluate(any(), any()))
                .thenReturn(RiskDecisionResult.builder().decision(RiskDecision.BLOCK).explanationId(UUID.randomUUID()).build());
        when(payoutLimitPolicy.getLimit(90)).thenReturn(highRiskLimit);
        when(creatorPayoutStateRepository.findByCreatorId(creatorId)).thenReturn(Optional.of(state));
        
        // Mock success for the rest of the flow
        com.stripe.service.TransferService transferService = mock(com.stripe.service.TransferService.class);
        when(stripeClient.transfers()).thenReturn(transferService);
        com.stripe.model.Transfer transfer = new com.stripe.model.Transfer();
        transfer.setId("tr_123");
        when(transferService.create(any())).thenReturn(transfer);
        when(creatorPayoutRepository.save(any())).thenAnswer(i -> {
            CreatorPayout p = i.getArgument(0);
            if (p.getId() == null) p.setId(UUID.randomUUID());
            return p;
        });

        // When
        payoutService.executePayout(creatorId, amount, "EUR");

        // Then
        // Should NOT have updated state to PAUSED because of manual override
        verify(creatorPayoutStateRepository, never()).save(argThat(s -> s.getStatus() == PayoutStateStatus.PAUSED));
        assertEquals(PayoutStateStatus.ACTIVE, state.getStatus());
    }
    
    @Test
    void executePayout_WhenHoldActive_ShouldThrowException() throws Exception {
        // Given
        mockNoLimit();
        BigDecimal amount = new BigDecimal("100.00");
        CreatorPayoutSettings settings = CreatorPayoutSettings.builder()
                .creatorId(creatorId)
                .enabled(true)
                .stripeAccountId("acct_123")
                .build();
        when(creatorPayoutSettingsRepository.findByCreatorId(creatorId)).thenReturn(Optional.of(settings));

        doThrow(new PayoutRestrictedException("Payout is held"))
                .when(stripePayoutAdapter).validateNoActiveHold(any(), any());

        // When & Then
        assertThrows(PayoutRestrictedException.class, () -> 
                payoutService.executePayout(creatorId, amount, "EUR"));
        
        verify(stripePayoutAdapter).validateNoActiveHold(eq(new UUID(0L, user.getId())), eq(com.joinlivora.backend.fraud.model.RiskSubjectType.CREATOR));
        verify(stripeClient, never()).transfers();
    }

    @Test
    void executePayout_WhenHighFraudScore_ShouldBlockPayoutAndStoreFlag() throws Exception {
        // Given
        mockNoLimit();
        BigDecimal amount = new BigDecimal("100.00");
        String currency = "EUR";

        // Mocking FraudScoreService to return 75 (>= 70)
        when(fraudScoreService.calculateFraudScore(any())).thenReturn(75);

        // We need these for the earlier checks in executePayout
        CreatorPayoutSettings settings = CreatorPayoutSettings.builder()
                .creatorId(creatorId)
                .enabled(true)
                .stripeAccountId("acct_123")
                .build();
        when(creatorPayoutSettingsRepository.findByCreatorId(creatorId)).thenReturn(Optional.of(settings));
        when(creatorPayoutRepository.save(any(CreatorPayout.class))).thenAnswer(i -> {
            CreatorPayout p = i.getArgument(0);
            if (p.getId() == null) p.setId(UUID.randomUUID());
            return p;
        });

        // When
        CreatorPayout result = payoutService.executePayout(creatorId, amount, currency);

        // Then
        assertNotNull(result);
        assertEquals(PayoutStatus.FAILED, result.getStatus());

        verify(fraudFlagRepository).save(argThat(flag ->
                flag.getSource() == com.joinlivora.backend.fraud.FraudFlagSource.SYSTEM &&
                flag.getScore() == 75 &&
                flag.getReason().contains("high fraud score")
        ));

        // Verify that Stripe was NOT called
        verify(stripeClient, never()).transfers();

        // Verify that the payout was saved with FAILED status
        verify(creatorPayoutRepository).save(argThat(p -> p.getStatus() == PayoutStatus.FAILED));
    }

    @Test
    void requestPayout_ShouldCreatePayoutAndLockEarnings() {
        // Given
        BigDecimal amount = new BigDecimal("100.00");
        String currency = "EUR";

        when(creatorPayoutRepository.save(any(CreatorPayout.class))).thenAnswer(i -> {
            CreatorPayout p = i.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        CreatorEarning earning1 = CreatorEarning.builder().id(UUID.randomUUID()).netAmount(new BigDecimal("60.00")).locked(false).build();
        CreatorEarning earning2 = CreatorEarning.builder().id(UUID.randomUUID()).netAmount(new BigDecimal("40.00")).locked(false).build();
        java.util.List<CreatorEarning> availableEarnings = java.util.Arrays.asList(earning1, earning2);
        
        when(creatorEarningRepository.findAvailableEarningsByCreator(user)).thenReturn(availableEarnings);

        // When
        CreatorPayout result = payoutService.requestPayout(creatorId, amount, currency);

        // Then
        assertNotNull(result);
        assertEquals(PayoutStatus.PENDING, result.getStatus());
        assertEquals(amount, result.getAmount());
        
        assertTrue(earning1.isLocked());
        assertEquals(result, earning1.getPayout());
        assertTrue(earning2.isLocked());
        assertEquals(result, earning2.getPayout());

        verify(creatorEarningRepository).saveAll(availableEarnings);
        verify(auditService).logEvent(any(), eq(com.joinlivora.backend.audit.service.AuditService.PAYOUT_REQUESTED), eq("PAYOUT"), eq(result.getId()), any(), any(), any());
        verify(kycAccessService).assertCreatorCanReceivePayout(user.getId());
    }

    @Test
    void requestPayout_WhenKycNotApproved_ShouldThrowException() {
        // Given
        BigDecimal amount = new BigDecimal("100.00");
        String currency = "EUR";

        doThrow(new KycNotApprovedException()).when(kycAccessService).assertCreatorCanReceivePayout(user.getId());

        // When & Then
        assertThrows(KycNotApprovedException.class, () -> payoutService.requestPayout(creatorId, amount, currency));
        verify(creatorPayoutRepository, never()).save(any());
    }

    @Test
    void executePayout_WhenKycNotApproved_ShouldThrowException() {
        // Given
        BigDecimal amount = new BigDecimal("100.00");
        String currency = "EUR";

        CreatorPayoutSettings settings = CreatorPayoutSettings.builder().creatorId(creatorId).enabled(true).stripeAccountId("acct_123").build();
        when(creatorPayoutSettingsRepository.findByCreatorId(creatorId)).thenReturn(Optional.of(settings));
        doThrow(new KycNotApprovedException()).when(kycAccessService).assertCreatorCanReceivePayout(user.getId());

        // When & Then
        assertThrows(KycNotApprovedException.class, () -> payoutService.executePayout(creatorId, amount, currency));
        verify(stripeClient, never()).transfers();
    }

    @Test
    void calculateAvailablePayout_CompletedPayoutReducesBalance() {
        // Given: 100 EUR earnings, 60 EUR already completed
        when(creatorEarningRepository.sumTotalNetTokensByCreator(user)).thenReturn(BigDecimal.ZERO);
        when(creatorEarningRepository.sumTotalNetRevenueByCreator(user)).thenReturn(new BigDecimal("100.00"));
        when(creatorPayoutRepository.sumAmountByCreatorIdAndStatus(creatorId, PayoutStatus.COMPLETED)).thenReturn(new BigDecimal("60.00"));
        when(creatorPayoutRepository.sumAmountByCreatorIdAndStatus(creatorId, PayoutStatus.PENDING)).thenReturn(BigDecimal.ZERO);

        // When
        BigDecimal available = payoutService.calculateAvailablePayout(creatorId);

        // Then: 100 - 60 = 40
        assertEquals(0, new BigDecimal("40.00").compareTo(available));
    }

    @Test
    void calculateAvailablePayout_PendingPayoutReducesBalance() {
        // Given: 100 EUR earnings, 30 EUR pending
        when(creatorEarningRepository.sumTotalNetTokensByCreator(user)).thenReturn(BigDecimal.ZERO);
        when(creatorEarningRepository.sumTotalNetRevenueByCreator(user)).thenReturn(new BigDecimal("100.00"));
        when(creatorPayoutRepository.sumAmountByCreatorIdAndStatus(creatorId, PayoutStatus.COMPLETED)).thenReturn(BigDecimal.ZERO);
        when(creatorPayoutRepository.sumAmountByCreatorIdAndStatus(creatorId, PayoutStatus.PENDING)).thenReturn(new BigDecimal("30.00"));

        // When
        BigDecimal available = payoutService.calculateAvailablePayout(creatorId);

        // Then: 100 - 30 = 70
        assertEquals(0, new BigDecimal("70.00").compareTo(available));
    }

    @Test
    void calculateAvailablePayout_FailedPayoutDoesNotReduceBalance() {
        // Given: 100 EUR earnings, 50 EUR failed (should not be subtracted)
        when(creatorEarningRepository.sumTotalNetTokensByCreator(user)).thenReturn(BigDecimal.ZERO);
        when(creatorEarningRepository.sumTotalNetRevenueByCreator(user)).thenReturn(new BigDecimal("100.00"));
        when(creatorPayoutRepository.sumAmountByCreatorIdAndStatus(creatorId, PayoutStatus.COMPLETED)).thenReturn(BigDecimal.ZERO);
        when(creatorPayoutRepository.sumAmountByCreatorIdAndStatus(creatorId, PayoutStatus.PENDING)).thenReturn(BigDecimal.ZERO);
        // FAILED payouts are not queried — balance should be full 100

        // When
        BigDecimal available = payoutService.calculateAvailablePayout(creatorId);

        // Then: 100 (FAILED payout has no effect)
        assertEquals(0, new BigDecimal("100.00").compareTo(available));
    }

    @Test
    void calculateAvailablePayout_CannotBeRepeatedlyPaidOut() {
        // Given: 100 EUR earnings, 100 EUR already completed — nothing left
        when(creatorEarningRepository.sumTotalNetTokensByCreator(user)).thenReturn(BigDecimal.ZERO);
        when(creatorEarningRepository.sumTotalNetRevenueByCreator(user)).thenReturn(new BigDecimal("100.00"));
        when(creatorPayoutRepository.sumAmountByCreatorIdAndStatus(creatorId, PayoutStatus.COMPLETED)).thenReturn(new BigDecimal("100.00"));
        when(creatorPayoutRepository.sumAmountByCreatorIdAndStatus(creatorId, PayoutStatus.PENDING)).thenReturn(BigDecimal.ZERO);

        // When
        BigDecimal available = payoutService.calculateAvailablePayout(creatorId);

        // Then: 100 - 100 = 0, no repeated payout possible
        assertEquals(0, BigDecimal.ZERO.compareTo(available));
        assertTrue(available.compareTo(BigDecimal.ZERO) <= 0, "Balance should be zero or negative, preventing repeated payouts");
    }

    private void mockNoLimit() {
        RiskProfile riskProfile = RiskProfile.builder()
                .userId(creatorId)
                .riskScore(10)
                .build();
        when(riskProfileService.generateRiskProfile(creatorId)).thenReturn(riskProfile);
        when(riskDecisionEngine.evaluate(eq(com.joinlivora.backend.fraud.model.RiskSubjectType.CREATOR), eq(riskProfile)))
                .thenReturn(RiskDecisionResult.builder()
                        .decision(RiskDecision.ALLOW)
                        .explanationId(UUID.randomUUID())
                        .riskScore(10)
                        .build());

        PayoutLimit limit = PayoutLimit.builder()
                .payoutFrequency(PayoutFrequency.NO_LIMIT)
                .maxPayoutAmount(null)
                .reason("Low risk")
                .build();
        when(payoutLimitPolicy.getLimit(10)).thenReturn(limit);

        when(creatorPayoutStateRepository.findByCreatorId(creatorId)).thenReturn(Optional.empty());
    }
}










