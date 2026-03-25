package com.joinlivora.backend.payout;

import com.joinlivora.backend.exception.BusinessException;
import com.joinlivora.backend.payout.dto.PayoutStatusDTO;
import com.joinlivora.backend.user.FraudRiskLevel;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.fraud.model.UserRiskState;
import com.joinlivora.backend.fraud.repository.UserRiskStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreatorPayoutServiceTest {

    @Mock
    private PayoutService payoutService;
    @Mock
    private PayoutHoldService payoutHoldService;
    @Mock
    private LegacyCreatorProfileRepository creatorProfileRepository;
    @Mock
    private CreatorPayoutSettingsRepository creatorPayoutSettingsRepository;
    @Mock
    private UserRiskStateRepository userRiskStateRepository;
    @Mock
    private CreatorPayoutRepository creatorPayoutRepository;
    @Mock
    private LegacyCreatorStripeAccountRepository creatorStripeAccountRepository;

    @InjectMocks
    private CreatorPayoutService creatorPayoutService;

    private User user;
    private LegacyCreatorProfile profile;
    private UUID canonicalCreatorId;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setEmail("creator@test.com");
        user.setPayoutsEnabled(true);
        user.setFraudRiskLevel(FraudRiskLevel.LOW);

        canonicalCreatorId = new UUID(0L, user.getId());

        profile = new LegacyCreatorProfile();
        profile.setId(UUID.randomUUID());
        profile.setUser(user);
    }

    @Test
    void getCreatorPayouts_Success() {
        when(creatorProfileRepository.findByUser(user)).thenReturn(Optional.of(profile));
        
        CreatorPayout payout = CreatorPayout.builder()
                .id(UUID.randomUUID())
                .amount(new BigDecimal("100.00"))
                .currency("EUR")
                .status(PayoutStatus.PENDING)
                .createdAt(Instant.now())
                .build();
        
        when(creatorPayoutRepository.findAllByCreatorIdOrderByCreatedAtDesc(canonicalCreatorId))
                .thenReturn(java.util.List.of(payout));

        java.util.List<com.joinlivora.backend.payout.dto.CreatorPayoutResponseDTO> results = 
                creatorPayoutService.getCreatorPayouts(user);

        assertEquals(1, results.size());
        assertEquals("PENDING", results.get(0).getStatus());
        assertEquals(new BigDecimal("100.00"), results.get(0).getAmount());
    }

    @Test
    void getPayoutStatus_Success() {
        when(creatorProfileRepository.findByUser(user)).thenReturn(Optional.of(profile));
        when(payoutService.calculateAvailablePayout(canonicalCreatorId)).thenReturn(new BigDecimal("150.50"));
        when(payoutHoldService.hasActiveHold(user)).thenReturn(false);
        when(userRiskStateRepository.findById(user.getId())).thenReturn(Optional.empty());
        
        CreatorPayoutSettings settings = new CreatorPayoutSettings();
        settings.setEnabled(true);
        when(creatorPayoutSettingsRepository.findByCreatorId(canonicalCreatorId)).thenReturn(Optional.of(settings));

        PayoutStatusDTO status = creatorPayoutService.getPayoutStatus(user);

        assertTrue(status.isPayoutsEnabled());
        assertFalse(status.isHasActivePayoutHold());
        assertEquals(FraudRiskLevel.LOW, status.getFraudRiskLevel());
        assertEquals(new BigDecimal("150.50"), status.getAvailableBalance());
        assertNotNull(status.getNextPayoutDate());
    }

    @Test
    void getPayoutStatus_WhenPaymentLocked_ShouldReflectInStatus() {
        when(creatorProfileRepository.findByUser(user)).thenReturn(Optional.of(profile));
        when(payoutService.calculateAvailablePayout(canonicalCreatorId)).thenReturn(new BigDecimal("100.00"));
        when(payoutHoldService.hasActiveHold(user)).thenReturn(false);
        
        UserRiskState riskState = UserRiskState.builder()
                .userId(user.getId())
                .paymentLocked(true)
                .build();
        when(userRiskStateRepository.findById(user.getId())).thenReturn(Optional.of(riskState));
        
        CreatorPayoutSettings settings = new CreatorPayoutSettings();
        settings.setEnabled(true);
        when(creatorPayoutSettingsRepository.findByCreatorId(canonicalCreatorId)).thenReturn(Optional.of(settings));

        PayoutStatusDTO status = creatorPayoutService.getPayoutStatus(user);

        assertFalse(status.isPayoutsEnabled());
        assertTrue(status.isHasActivePayoutHold());
        assertEquals(new BigDecimal("100.00"), status.getAvailableBalance());
    }

    @Test
    void requestPayout_Success() throws Exception {
        when(creatorProfileRepository.findByUser(user)).thenReturn(Optional.of(profile));
        when(payoutService.calculateAvailablePayout(canonicalCreatorId)).thenReturn(new BigDecimal("100.00"));
        when(payoutHoldService.hasActiveHold(user)).thenReturn(false);
        when(userRiskStateRepository.findById(user.getId())).thenReturn(Optional.empty());

        LegacyCreatorStripeAccount stripeAccount = LegacyCreatorStripeAccount.builder()
                .payoutsEnabled(true)
                .build();
        when(creatorStripeAccountRepository.findByCreatorId(user.getId())).thenReturn(Optional.of(stripeAccount));

        CreatorPayoutSettings settings = new CreatorPayoutSettings();
        settings.setEnabled(true);
        settings.setMinimumPayoutAmount(new BigDecimal("50.00"));
        when(creatorPayoutSettingsRepository.findByCreatorId(canonicalCreatorId)).thenReturn(Optional.of(settings));

        CreatorPayout expectedPayout = new CreatorPayout();
        when(payoutService.executePayout(canonicalCreatorId, new BigDecimal("100.00"), "EUR")).thenReturn(expectedPayout);

        CreatorPayout result = creatorPayoutService.requestPayout(user);

        assertNotNull(result);
        assertEquals(expectedPayout, result);
    }

    @Test
    void requestPayout_BelowMinimum_ShouldThrowException() throws Exception {
        when(creatorProfileRepository.findByUser(user)).thenReturn(Optional.of(profile));
        when(payoutService.calculateAvailablePayout(canonicalCreatorId)).thenReturn(new BigDecimal("30.00"));
        when(payoutHoldService.hasActiveHold(user)).thenReturn(false);
        when(userRiskStateRepository.findById(user.getId())).thenReturn(Optional.empty());

        LegacyCreatorStripeAccount stripeAccount = LegacyCreatorStripeAccount.builder()
                .payoutsEnabled(true)
                .build();
        when(creatorStripeAccountRepository.findByCreatorId(user.getId())).thenReturn(Optional.of(stripeAccount));

        CreatorPayoutSettings settings = new CreatorPayoutSettings();
        settings.setEnabled(true);
        settings.setMinimumPayoutAmount(new BigDecimal("50.00"));
        when(creatorPayoutSettingsRepository.findByCreatorId(canonicalCreatorId)).thenReturn(Optional.of(settings));

        assertThrows(IllegalArgumentException.class, () -> creatorPayoutService.requestPayout(user));
    }

    @Test
    void requestPayout_HighRisk_ShouldThrowException() throws Exception {
        user.setFraudRiskLevel(FraudRiskLevel.HIGH);
        when(creatorProfileRepository.findByUser(user)).thenReturn(Optional.of(profile));
        when(payoutService.calculateAvailablePayout(canonicalCreatorId)).thenReturn(new BigDecimal("100.00"));
        when(payoutHoldService.hasActiveHold(user)).thenReturn(false);
        when(userRiskStateRepository.findById(user.getId())).thenReturn(Optional.empty());

        LegacyCreatorStripeAccount stripeAccount = LegacyCreatorStripeAccount.builder()
                .payoutsEnabled(true)
                .build();
        when(creatorStripeAccountRepository.findByCreatorId(user.getId())).thenReturn(Optional.of(stripeAccount));

        CreatorPayoutSettings settings = new CreatorPayoutSettings();
        settings.setEnabled(true);
        when(creatorPayoutSettingsRepository.findByCreatorId(canonicalCreatorId)).thenReturn(Optional.of(settings));

        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> creatorPayoutService.requestPayout(user));
    }

    @Test
    void requestPayout_StripeOnboardingIncomplete_ShouldThrowException() throws Exception {
        when(creatorProfileRepository.findByUser(user)).thenReturn(Optional.of(profile));
        when(payoutService.calculateAvailablePayout(canonicalCreatorId)).thenReturn(new BigDecimal("100.00"));
        when(payoutHoldService.hasActiveHold(user)).thenReturn(false);
        when(userRiskStateRepository.findById(user.getId())).thenReturn(Optional.empty());

        LegacyCreatorStripeAccount stripeAccount = LegacyCreatorStripeAccount.builder()
                .payoutsEnabled(false)
                .build();
        when(creatorStripeAccountRepository.findByCreatorId(user.getId())).thenReturn(Optional.of(stripeAccount));

        CreatorPayoutSettings settings = new CreatorPayoutSettings();
        settings.setEnabled(true);
        when(creatorPayoutSettingsRepository.findByCreatorId(canonicalCreatorId)).thenReturn(Optional.of(settings));

        BusinessException exception = assertThrows(BusinessException.class, () -> creatorPayoutService.requestPayout(user));
        assertEquals("Stripe onboarding incomplete", exception.getMessage());
    }

    @Test
    void requestPayout_StripeAccountMissing_ShouldThrowException() throws Exception {
        when(creatorProfileRepository.findByUser(user)).thenReturn(Optional.of(profile));
        when(payoutService.calculateAvailablePayout(canonicalCreatorId)).thenReturn(new BigDecimal("100.00"));
        when(payoutHoldService.hasActiveHold(user)).thenReturn(false);
        when(userRiskStateRepository.findById(user.getId())).thenReturn(Optional.empty());

        when(creatorStripeAccountRepository.findByCreatorId(user.getId())).thenReturn(Optional.empty());

        CreatorPayoutSettings settings = new CreatorPayoutSettings();
        settings.setEnabled(true);
        when(creatorPayoutSettingsRepository.findByCreatorId(canonicalCreatorId)).thenReturn(Optional.of(settings));

        BusinessException exception = assertThrows(BusinessException.class, () -> creatorPayoutService.requestPayout(user));
        assertEquals("Stripe onboarding incomplete", exception.getMessage());
    }

    @Test
    void getCreatorPayouts_UsesCanonicalCreatorId() {
        // Verify that payout history uses canonical UUID, not legacy profile ID
        when(creatorProfileRepository.findByUser(user)).thenReturn(Optional.of(profile));
        when(creatorPayoutRepository.findAllByCreatorIdOrderByCreatedAtDesc(canonicalCreatorId))
                .thenReturn(java.util.List.of());

        java.util.List<com.joinlivora.backend.payout.dto.CreatorPayoutResponseDTO> results =
                creatorPayoutService.getCreatorPayouts(user);

        assertTrue(results.isEmpty());
        // Verify canonical UUID was used, not profile.getId()
        verify(creatorPayoutRepository).findAllByCreatorIdOrderByCreatedAtDesc(canonicalCreatorId);
        verify(creatorPayoutRepository, never()).findAllByCreatorIdOrderByCreatedAtDesc(profile.getId());
    }

    @Test
    void requestPayout_UsesCanonicalCreatorIdForSettingsAndPayoutService() throws Exception {
        // Verify settings lookup and payoutService.executePayout both use canonical UUID
        when(creatorProfileRepository.findByUser(user)).thenReturn(Optional.of(profile));
        when(payoutService.calculateAvailablePayout(canonicalCreatorId)).thenReturn(new BigDecimal("100.00"));
        when(payoutHoldService.hasActiveHold(user)).thenReturn(false);
        when(userRiskStateRepository.findById(user.getId())).thenReturn(Optional.empty());

        LegacyCreatorStripeAccount stripeAccount = LegacyCreatorStripeAccount.builder()
                .payoutsEnabled(true)
                .build();
        when(creatorStripeAccountRepository.findByCreatorId(user.getId())).thenReturn(Optional.of(stripeAccount));

        CreatorPayoutSettings settings = new CreatorPayoutSettings();
        settings.setEnabled(true);
        settings.setMinimumPayoutAmount(new BigDecimal("50.00"));
        when(creatorPayoutSettingsRepository.findByCreatorId(canonicalCreatorId)).thenReturn(Optional.of(settings));

        CreatorPayout expectedPayout = new CreatorPayout();
        when(payoutService.executePayout(canonicalCreatorId, new BigDecimal("100.00"), "EUR")).thenReturn(expectedPayout);

        creatorPayoutService.requestPayout(user);

        // Verify canonical UUID used for settings lookup (not legacy profile ID)
        // Called twice: once in getPayoutStatus() and once in requestPayout()
        verify(creatorPayoutSettingsRepository, atLeast(1)).findByCreatorId(canonicalCreatorId);
        verify(creatorPayoutSettingsRepository, never()).findByCreatorId(profile.getId());
        // Verify canonical UUID used for payoutService.executePayout (not deprecated requestPayout)
        verify(payoutService).executePayout(canonicalCreatorId, new BigDecimal("100.00"), "EUR");
        verify(payoutService, never()).requestPayout(any(), any(), any());
    }

    @Test
    void requestPayout_DelegatesToExecutePayoutNotDeprecatedRequestPayout() throws Exception {
        // Verify that CreatorPayoutService.requestPayout delegates to executePayout (full lifecycle)
        // and never calls the deprecated requestPayout (which creates orphaned PENDING records)
        when(creatorProfileRepository.findByUser(user)).thenReturn(Optional.of(profile));
        when(payoutService.calculateAvailablePayout(canonicalCreatorId)).thenReturn(new BigDecimal("80.00"));
        when(payoutHoldService.hasActiveHold(user)).thenReturn(false);
        when(userRiskStateRepository.findById(user.getId())).thenReturn(Optional.empty());

        LegacyCreatorStripeAccount stripeAccount = LegacyCreatorStripeAccount.builder()
                .payoutsEnabled(true)
                .build();
        when(creatorStripeAccountRepository.findByCreatorId(user.getId())).thenReturn(Optional.of(stripeAccount));

        CreatorPayoutSettings settings = new CreatorPayoutSettings();
        settings.setEnabled(true);
        settings.setMinimumPayoutAmount(new BigDecimal("50.00"));
        when(creatorPayoutSettingsRepository.findByCreatorId(canonicalCreatorId)).thenReturn(Optional.of(settings));

        CreatorPayout completedPayout = new CreatorPayout();
        completedPayout.setStatus(PayoutStatus.COMPLETED);
        when(payoutService.executePayout(canonicalCreatorId, new BigDecimal("80.00"), "EUR")).thenReturn(completedPayout);

        CreatorPayout result = creatorPayoutService.requestPayout(user);

        assertEquals(PayoutStatus.COMPLETED, result.getStatus());
        verify(payoutService).executePayout(canonicalCreatorId, new BigDecimal("80.00"), "EUR");
        verify(payoutService, never()).requestPayout(any(), any(), any());
    }

    @Test
    void getPayoutStatus_UsesCanonicalCreatorIdForSettingsAndBalance() {
        // Verify getPayoutStatus uses canonical UUID for both balance and settings
        when(creatorProfileRepository.findByUser(user)).thenReturn(Optional.of(profile));
        when(payoutService.calculateAvailablePayout(canonicalCreatorId)).thenReturn(new BigDecimal("75.00"));
        when(payoutHoldService.hasActiveHold(user)).thenReturn(false);
        when(userRiskStateRepository.findById(user.getId())).thenReturn(Optional.empty());

        CreatorPayoutSettings settings = new CreatorPayoutSettings();
        settings.setEnabled(true);
        when(creatorPayoutSettingsRepository.findByCreatorId(canonicalCreatorId)).thenReturn(Optional.of(settings));

        PayoutStatusDTO status = creatorPayoutService.getPayoutStatus(user);

        assertEquals(new BigDecimal("75.00"), status.getAvailableBalance());
        verify(payoutService).calculateAvailablePayout(canonicalCreatorId);
        verify(creatorPayoutSettingsRepository).findByCreatorId(canonicalCreatorId);
        verify(payoutService, never()).calculateAvailablePayout(profile.getId());
    }
}










