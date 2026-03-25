package com.joinlivora.backend.payout;

import com.joinlivora.backend.payout.policy.PayoutEligibilityService;
import com.joinlivora.backend.exception.BusinessException;
import com.joinlivora.backend.fraud.model.UserRiskState;
import com.joinlivora.backend.fraud.repository.UserRiskStateRepository;
import com.joinlivora.backend.fraud.service.FraudScoreService;
import com.joinlivora.backend.payout.dto.PayoutEligibilityResponseDTO;
import com.joinlivora.backend.payout.dto.PayoutHoldStatusDTO;
import com.joinlivora.backend.payout.dto.PayoutRequestAdminDetailDTO;
import com.joinlivora.backend.user.FraudRiskLevel;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserStatus;
import com.stripe.model.Transfer;
import com.stripe.param.TransferCreateParams;
import com.stripe.service.TransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PayoutRequestServiceTest {

    @Mock
    private PayoutRequestRepository payoutRequestRepository;
    @Mock
    private PayoutHoldService payoutHoldService;
    @Mock
    private UserRiskStateRepository userRiskStateRepository;
    @Mock
    private LegacyCreatorProfileRepository creatorProfileRepository;
    @Mock
    private CreatorPayoutSettingsRepository creatorPayoutSettingsRepository;
    @Mock
    private LegacyCreatorStripeAccountRepository creatorStripeAccountRepository;
    @Mock
    private CreatorBalanceService creatorBalanceService;
    @Mock
    private CreatorEarningRepository creatorEarningRepository;
    @Mock
    private FraudScoreService fraudScoreService;
    @Mock
    private PayoutEligibilityService payoutEligibilityService;
    @Mock
    private PayoutService payoutService;
    @Mock
    private com.stripe.StripeClient stripeClient;

    @InjectMocks
    private PayoutRequestService payoutRequestService;

    private User user;
    private LegacyCreatorProfile profile;
    private CreatorPayoutSettings settings;
    private LegacyCreatorStripeAccount stripeAccount;
    private UUID canonicalCreatorId;

    @BeforeEach
    void setUp() {
        user = new User("test@example.com", "password", Role.CREATOR);
        user.setId(1L);
        user.setStatus(UserStatus.ACTIVE);
        user.setPayoutsEnabled(true);
        user.setFraudRiskLevel(FraudRiskLevel.LOW);

        canonicalCreatorId = new UUID(0L, user.getId());

        profile = LegacyCreatorProfile.builder()
                .id(UUID.randomUUID())
                .user(user)
                .build();

        settings = CreatorPayoutSettings.builder()
                .creatorId(canonicalCreatorId)
                .enabled(true)
                .minimumPayoutAmount(new BigDecimal("50.00"))
                .build();

        stripeAccount = LegacyCreatorStripeAccount.builder()
                .creatorId(user.getId())
                .payoutsEnabled(true)
                .stripeAccountId("acct_123")
                .build();
    }

    @Test
    void checkEligibility_AllGood_ShouldBeEligible() {
        when(userRiskStateRepository.findById(user.getId())).thenReturn(Optional.empty());
        when(payoutHoldService.hasActiveHold(user)).thenReturn(false);
        when(creatorProfileRepository.findByUser(user)).thenReturn(Optional.of(profile));
        when(creatorPayoutSettingsRepository.findByCreatorId(canonicalCreatorId)).thenReturn(Optional.of(settings));
        when(creatorBalanceService.getAvailableBalance(user)).thenReturn(Map.of("EUR", new BigDecimal("100.00")));
        when(creatorStripeAccountRepository.findByCreatorId(user.getId())).thenReturn(Optional.of(stripeAccount));

        PayoutEligibilityResponseDTO result = payoutRequestService.checkEligibility(user);

        assertTrue(result.isEligible());
        assertTrue(result.getReasons().isEmpty());
    }

    @Test
    void checkEligibility_NotCreator_ShouldNotBeEligible() {
        user.setRole(Role.USER);

        PayoutEligibilityResponseDTO result = payoutRequestService.checkEligibility(user);

        assertFalse(result.isEligible());
        assertTrue(result.getReasons().contains("User is not a creator"));
    }

    @Test
    void checkEligibility_StatusNotActive_ShouldNotBeEligible() {
        user.setStatus(UserStatus.SUSPENDED);

        PayoutEligibilityResponseDTO result = payoutRequestService.checkEligibility(user);

        assertFalse(result.isEligible());
        assertTrue(result.getReasons().stream().anyMatch(r -> r.contains("User account is not active")));
    }

    @Test
    void checkEligibility_PayoutsDisabled_ShouldNotBeEligible() {
        user.setPayoutsEnabled(false);

        PayoutEligibilityResponseDTO result = payoutRequestService.checkEligibility(user);

        assertFalse(result.isEligible());
        assertTrue(result.getReasons().contains("Payouts are disabled for this account"));
    }

    @Test
    void checkEligibility_HighFraudRisk_ShouldNotBeEligible() {
        user.setFraudRiskLevel(FraudRiskLevel.HIGH);

        PayoutEligibilityResponseDTO result = payoutRequestService.checkEligibility(user);

        assertFalse(result.isEligible());
        assertTrue(result.getReasons().contains("Payouts are blocked due to high fraud risk level"));
    }

    @Test
    void checkEligibility_PaymentLocked_ShouldNotBeEligible() {
        UserRiskState riskState = UserRiskState.builder()
                .userId(user.getId())
                .paymentLocked(true)
                .build();
        when(userRiskStateRepository.findById(user.getId())).thenReturn(Optional.of(riskState));

        PayoutEligibilityResponseDTO result = payoutRequestService.checkEligibility(user);

        assertFalse(result.isEligible());
        assertTrue(result.getReasons().contains("Payouts are locked due to security reasons"));
    }

    @Test
    void checkEligibility_HasActiveHold_ShouldNotBeEligible() {
        when(payoutHoldService.hasActiveHold(user)).thenReturn(true);

        PayoutEligibilityResponseDTO result = payoutRequestService.checkEligibility(user);

        assertFalse(result.isEligible());
        assertTrue(result.getReasons().contains("An active payout hold exists on this account"));
    }

    @Test
    void checkEligibility_SettingsDisabled_ShouldNotBeEligible() {
        settings.setEnabled(false);
        when(creatorProfileRepository.findByUser(user)).thenReturn(Optional.of(profile));
        when(creatorPayoutSettingsRepository.findByCreatorId(canonicalCreatorId)).thenReturn(Optional.of(settings));

        PayoutEligibilityResponseDTO result = payoutRequestService.checkEligibility(user);

        assertFalse(result.isEligible());
        assertTrue(result.getReasons().contains("Payouts are disabled in creator settings"));
    }

    @Test
    void checkEligibility_LowBalance_ShouldNotBeEligible() {
        when(creatorProfileRepository.findByUser(user)).thenReturn(Optional.of(profile));
        when(creatorPayoutSettingsRepository.findByCreatorId(canonicalCreatorId)).thenReturn(Optional.of(settings));
        when(creatorBalanceService.getAvailableBalance(user)).thenReturn(Map.of("EUR", new BigDecimal("10.00")));

        PayoutEligibilityResponseDTO result = payoutRequestService.checkEligibility(user);

        assertFalse(result.isEligible());
        assertTrue(result.getReasons().stream().anyMatch(r -> r.contains("below minimum payout amount")));
    }

    @Test
    void checkEligibility_StripePayoutsDisabled_ShouldNotBeEligible() {
        stripeAccount.setPayoutsEnabled(false);
        when(creatorStripeAccountRepository.findByCreatorId(user.getId())).thenReturn(Optional.of(stripeAccount));

        PayoutEligibilityResponseDTO result = payoutRequestService.checkEligibility(user);

        assertFalse(result.isEligible());
        assertTrue(result.getReasons().stream().anyMatch(r -> r.contains("Stripe payouts are not enabled")));
    }

    @Test
    void createPayoutRequest_Eligible_ShouldSaveAndReturnRequest() {
        BigDecimal balance = new BigDecimal("100.00");
        when(creatorBalanceService.getAvailableBalance(user)).thenReturn(Map.of("EUR", balance));
        when(payoutService.calculateAvailablePayout(canonicalCreatorId)).thenReturn(balance);
        
        when(userRiskStateRepository.findById(user.getId())).thenReturn(Optional.empty());
        when(payoutHoldService.hasActiveHold(user)).thenReturn(false);
        when(creatorProfileRepository.findByUser(user)).thenReturn(Optional.of(profile));
        when(creatorPayoutSettingsRepository.findByCreatorId(canonicalCreatorId)).thenReturn(Optional.of(settings));
        when(creatorStripeAccountRepository.findByCreatorId(user.getId())).thenReturn(Optional.of(stripeAccount));

        when(payoutRequestRepository.save(any(PayoutRequest.class))).thenAnswer(i -> {
            PayoutRequest req = i.getArgument(0);
            req.setId(UUID.randomUUID());
            return req;
        });

        CreatorEarning earningEur = CreatorEarning.builder()
                .netAmount(balance)
                .currency("EUR")
                .locked(false)
                .build();
        CreatorEarning earningToken = CreatorEarning.builder()
                .netAmount(new BigDecimal("500"))
                .currency("TOKEN")
                .locked(false)
                .build();
        when(creatorEarningRepository.findAvailableEarningsByCreator(user)).thenReturn(java.util.List.of(earningEur, earningToken));

        PayoutRequest result = payoutRequestService.createPayoutRequest(user);

        assertNotNull(result);
        verify(payoutEligibilityService).assertEligibleForPayout(user.getId(), balance);
        assertEquals(profile.getId(), result.getCreatorId());
        assertEquals(balance, result.getAmount());
        assertEquals("EUR", result.getCurrency());
        assertEquals(PayoutRequestStatus.PENDING, result.getStatus());
        
        assertTrue(earningEur.isLocked());
        assertEquals(result, earningEur.getPayoutRequest());
        
        assertFalse(earningToken.isLocked());
        assertNull(earningToken.getPayoutRequest());
        
        verify(payoutRequestRepository).save(any(PayoutRequest.class));
        verify(creatorEarningRepository).saveAll(anyList());
    }

    @Test
    void getPayoutRequestsByUser_ShouldReturnList() {
        PayoutRequest request = new PayoutRequest();
        when(creatorProfileRepository.findByUser(user)).thenReturn(Optional.of(profile));
        when(payoutRequestRepository.findAllByCreatorIdOrderByCreatedAtDesc(profile.getId())).thenReturn(java.util.List.of(request));

        java.util.List<PayoutRequest> result = payoutRequestService.getPayoutRequestsByUser(user);

        assertEquals(1, result.size());
        assertEquals(request, result.get(0));
        verify(payoutRequestRepository).findAllByCreatorIdOrderByCreatedAtDesc(profile.getId());
    }

    @Test
    void createPayoutRequest_EligibilityBlocked_ShouldThrowException() {
        BigDecimal balance = new BigDecimal("100.00");
        when(creatorBalanceService.getAvailableBalance(user)).thenReturn(Map.of("EUR", balance));
        
        doThrow(new com.joinlivora.backend.common.exception.PayoutBlockedException("Blocked"))
                .when(payoutEligibilityService).assertEligibleForPayout(user.getId(), balance);
        // Note: cross-check not reached because eligibility assertion throws first

        assertThrows(com.joinlivora.backend.common.exception.PayoutBlockedException.class, 
                () -> payoutRequestService.createPayoutRequest(user));
        
        verify(payoutRequestRepository, never()).save(any());
    }

    @Test
    void createPayoutRequest_ZeroBalance_ShouldThrowException() {
        when(creatorBalanceService.getAvailableBalance(user)).thenReturn(Map.of("EUR", BigDecimal.ZERO));

        BusinessException exception = assertThrows(BusinessException.class, () -> payoutRequestService.createPayoutRequest(user));
        assertEquals("Payout amount must be greater than zero", exception.getMessage());
        verify(payoutRequestRepository, never()).save(any());
    }

    @Test
    void createPayoutRequest_Ineligible_ShouldThrowBusinessException() {
        user.setRole(Role.USER);
        when(creatorBalanceService.getAvailableBalance(user)).thenReturn(Map.of("EUR", new BigDecimal("100.00")));
        when(payoutService.calculateAvailablePayout(canonicalCreatorId)).thenReturn(new BigDecimal("100.00"));

        BusinessException exception = assertThrows(BusinessException.class, () -> payoutRequestService.createPayoutRequest(user));
        assertEquals("User is not a creator", exception.getMessage());
        verify(payoutRequestRepository, never()).save(any());
    }

    @Test
    void getPayoutRequestAdminDetail_Success() {
        UUID id = UUID.randomUUID();
        user.setTrustScore(85);
        PayoutRequest request = PayoutRequest.builder()
                .id(id)
                .creatorId(profile.getId())
                .amount(new BigDecimal("100.00"))
                .currency("EUR")
                .status(PayoutRequestStatus.PENDING)
                .createdAt(java.time.Instant.now())
                .build();

        when(payoutRequestRepository.findById(id)).thenReturn(Optional.of(request));
        when(creatorProfileRepository.findById(profile.getId())).thenReturn(Optional.of(profile));
        when(fraudScoreService.calculateFraudScore(any(UUID.class))).thenReturn(15);
        when(creatorStripeAccountRepository.findByCreatorId(user.getId())).thenReturn(Optional.of(stripeAccount));
        
        PayoutHoldStatusDTO holdStatus = PayoutHoldStatusDTO.builder()
                .holdLevel(HoldLevel.MEDIUM)
                .reason("Review")
                .build();
        when(payoutHoldService.getPayoutHoldStatus(user)).thenReturn(holdStatus);

        PayoutRequestAdminDetailDTO result = payoutRequestService.getPayoutRequestAdminDetail(id);

        assertNotNull(result);
        assertEquals(id, result.getId());
        assertEquals(user.getEmail(), result.getCreatorEmail());
        assertEquals(15, result.getFraudScore());
        assertEquals(85, result.getTrustScore());
        assertEquals(1, result.getPayoutHolds().size());
        assertEquals(HoldLevel.MEDIUM, result.getPayoutHolds().get(0).getHoldLevel());
        assertTrue(result.isStripeReady());
    }

    @Test
    void approvePayoutRequest_Success() throws Exception {
        UUID id = UUID.randomUUID();
        PayoutRequest request = PayoutRequest.builder()
                .id(id)
                .creatorId(profile.getId())
                .amount(new BigDecimal("100.00"))
                .currency("EUR")
                .status(PayoutRequestStatus.PENDING)
                .build();
        when(payoutRequestRepository.findById(id)).thenReturn(Optional.of(request));
        when(creatorProfileRepository.findById(profile.getId())).thenReturn(Optional.of(profile));
        when(creatorStripeAccountRepository.findByCreatorId(user.getId())).thenReturn(Optional.of(stripeAccount));

        TransferService transferService = mock(TransferService.class);
        when(stripeClient.transfers()).thenReturn(transferService);
        Transfer transfer = mock(Transfer.class);
        when(transfer.getId()).thenReturn("tr_approved_123");
        
        // Capture and verify params
        when(transferService.create(any(TransferCreateParams.class))).thenAnswer(invocation -> {
            TransferCreateParams params = invocation.getArgument(0);
            assertEquals(10000L, params.getAmount());
            assertEquals("eur", params.getCurrency());
            assertEquals("acct_123", params.getDestination());
            assertEquals(id.toString(), params.getMetadata().get("payoutRequestId"));
            assertEquals(profile.getId().toString(), params.getMetadata().get("creator"));
            return transfer;
        });

        when(payoutRequestRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        PayoutRequest result = payoutRequestService.approvePayoutRequest(id);

        assertEquals(PayoutRequestStatus.APPROVED, result.getStatus());
        assertEquals("tr_approved_123", result.getStripeTransferId());
        assertNotNull(result.getUpdatedAt());
        verify(payoutRequestRepository).save(request);
    }

    @Test
    void approvePayoutRequest_ShouldNotSetCompleted_BeforeWebhook() throws Exception {
        UUID id = UUID.randomUUID();
        PayoutRequest request = PayoutRequest.builder()
                .id(id)
                .creatorId(profile.getId())
                .amount(new BigDecimal("50.00"))
                .currency("EUR")
                .status(PayoutRequestStatus.PENDING)
                .build();
        when(payoutRequestRepository.findById(id)).thenReturn(Optional.of(request));
        when(creatorProfileRepository.findById(profile.getId())).thenReturn(Optional.of(profile));
        when(creatorStripeAccountRepository.findByCreatorId(user.getId())).thenReturn(Optional.of(stripeAccount));

        TransferService transferService = mock(TransferService.class);
        when(stripeClient.transfers()).thenReturn(transferService);
        Transfer transfer = mock(Transfer.class);
        when(transfer.getId()).thenReturn("tr_test");
        when(transferService.create(any(TransferCreateParams.class))).thenReturn(transfer);
        when(payoutRequestRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        PayoutRequest result = payoutRequestService.approvePayoutRequest(id);

        // Must be APPROVED, not COMPLETED — COMPLETED only via webhook
        assertNotEquals(PayoutRequestStatus.COMPLETED, result.getStatus());
        assertEquals(PayoutRequestStatus.APPROVED, result.getStatus());
    }

    @Test
    void rejectPayoutRequest_Success() {
        UUID id = UUID.randomUUID();
        PayoutRequest request = PayoutRequest.builder()
                .id(id)
                .status(PayoutRequestStatus.PENDING)
                .build();
        CreatorEarning earning = CreatorEarning.builder()
                .locked(true)
                .payoutRequest(request)
                .build();

        when(payoutRequestRepository.findById(id)).thenReturn(Optional.of(request));
        when(creatorEarningRepository.findAllByPayoutRequest(request)).thenReturn(java.util.List.of(earning));
        when(payoutRequestRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        PayoutRequest result = payoutRequestService.rejectPayoutRequest(id, "Fraud suspected");

        assertEquals(PayoutRequestStatus.REJECTED, result.getStatus());
        assertEquals("Fraud suspected", result.getRejectionReason());
        assertNotNull(result.getUpdatedAt());
        assertFalse(earning.isLocked());
        assertNull(earning.getPayoutRequest());
        verify(creatorEarningRepository).saveAll(anyList());
        verify(payoutRequestRepository).save(request);
    }

    @Test
    void approvePayoutRequest_AlreadyProcessed_ShouldThrowException() {
        UUID id = UUID.randomUUID();
        PayoutRequest request = PayoutRequest.builder()
                .id(id)
                .status(PayoutRequestStatus.COMPLETED)
                .build();
        when(payoutRequestRepository.findById(id)).thenReturn(Optional.of(request));

        assertThrows(BusinessException.class, () -> payoutRequestService.approvePayoutRequest(id));
    }

    @Test
    void approvePayoutRequest_StripeFailure_ShouldSetStatusFailedAndUnlock() throws Exception {
        UUID id = UUID.randomUUID();
        PayoutRequest request = PayoutRequest.builder()
                .id(id)
                .creatorId(profile.getId())
                .amount(new BigDecimal("100.00"))
                .currency("EUR")
                .status(PayoutRequestStatus.PENDING)
                .build();
        
        CreatorEarning earning = CreatorEarning.builder()
                .locked(true)
                .payoutRequest(request)
                .build();

        when(payoutRequestRepository.findById(id)).thenReturn(Optional.of(request));
        when(creatorProfileRepository.findById(profile.getId())).thenReturn(Optional.of(profile));
        when(creatorStripeAccountRepository.findByCreatorId(user.getId())).thenReturn(Optional.of(stripeAccount));
        when(creatorEarningRepository.findAllByPayoutRequest(request)).thenReturn(java.util.List.of(earning));

        com.stripe.service.TransferService transferService = mock(com.stripe.service.TransferService.class);
        when(stripeClient.transfers()).thenReturn(transferService);
        when(transferService.create(any(com.stripe.param.TransferCreateParams.class))).thenThrow(new RuntimeException("Stripe error"));

        when(payoutRequestRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // We expect it to throw BusinessException currently
        BusinessException exception = assertThrows(BusinessException.class, () -> payoutRequestService.approvePayoutRequest(id));
        assertTrue(exception.getMessage().contains("Stripe transfer failed"));

        assertEquals(PayoutRequestStatus.FAILED, request.getStatus());
        assertNotNull(request.getUpdatedAt());
        assertTrue(request.getRejectionReason().contains("Stripe transfer failed"));
        assertFalse(earning.isLocked());
        assertNull(earning.getPayoutRequest());
        
        verify(payoutRequestRepository, atLeastOnce()).save(request);
        verify(creatorEarningRepository).saveAll(anyList());
    }

    @Test
    void createPayoutRequest_CanonicalAvailableZero_ShouldThrowBusinessException() {
        // Double-spend prevention: even if CreatorBalanceService shows funds,
        // if calculateAvailablePayout returns 0 (already paid via scheduled payout), block the request
        when(creatorBalanceService.getAvailableBalance(user)).thenReturn(Map.of("EUR", new BigDecimal("100.00")));
        when(payoutService.calculateAvailablePayout(canonicalCreatorId)).thenReturn(BigDecimal.ZERO);

        BusinessException exception = assertThrows(BusinessException.class, () -> payoutRequestService.createPayoutRequest(user));
        assertEquals("No available balance after accounting for existing payouts", exception.getMessage());
        verify(payoutRequestRepository, never()).save(any());
    }

    @Test
    void createPayoutRequest_ShouldUseMinOfBothBalances() {
        // If canonical available (80) is less than balance service (100), use 80 as safe amount
        BigDecimal balance = new BigDecimal("100.00");
        BigDecimal canonicalAvailable = new BigDecimal("80.00");
        when(creatorBalanceService.getAvailableBalance(user)).thenReturn(Map.of("EUR", balance));
        when(payoutService.calculateAvailablePayout(canonicalCreatorId)).thenReturn(canonicalAvailable);

        when(userRiskStateRepository.findById(user.getId())).thenReturn(Optional.empty());
        when(payoutHoldService.hasActiveHold(user)).thenReturn(false);
        when(creatorProfileRepository.findByUser(user)).thenReturn(Optional.of(profile));
        when(creatorPayoutSettingsRepository.findByCreatorId(canonicalCreatorId)).thenReturn(Optional.of(settings));
        when(creatorStripeAccountRepository.findByCreatorId(user.getId())).thenReturn(Optional.of(stripeAccount));

        when(payoutRequestRepository.save(any(PayoutRequest.class))).thenAnswer(i -> {
            PayoutRequest req = i.getArgument(0);
            req.setId(UUID.randomUUID());
            return req;
        });
        when(creatorEarningRepository.findAvailableEarningsByCreator(user)).thenReturn(java.util.List.of());

        PayoutRequest result = payoutRequestService.createPayoutRequest(user);

        // Amount should be min(100, 80) = 80
        assertEquals(canonicalAvailable, result.getAmount());
    }

    @Test
    void checkEligibility_UsesCanonicalCreatorIdForSettingsLookup() {
        // Verify eligibility uses canonical UUID (not legacy profile ID) for settings
        when(userRiskStateRepository.findById(user.getId())).thenReturn(Optional.empty());
        when(payoutHoldService.hasActiveHold(user)).thenReturn(false);
        when(creatorProfileRepository.findByUser(user)).thenReturn(Optional.of(profile));
        when(creatorPayoutSettingsRepository.findByCreatorId(canonicalCreatorId)).thenReturn(Optional.of(settings));
        when(creatorBalanceService.getAvailableBalance(user)).thenReturn(Map.of("EUR", new BigDecimal("100.00")));
        when(creatorStripeAccountRepository.findByCreatorId(user.getId())).thenReturn(Optional.of(stripeAccount));

        PayoutEligibilityResponseDTO result = payoutRequestService.checkEligibility(user);

        assertTrue(result.isEligible());
        // Verify canonical UUID was used, not legacy profile ID
        verify(creatorPayoutSettingsRepository).findByCreatorId(canonicalCreatorId);
        verify(creatorPayoutSettingsRepository, never()).findByCreatorId(profile.getId());
    }
}











