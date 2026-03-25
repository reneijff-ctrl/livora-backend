package com.joinlivora.backend.payout;

import com.joinlivora.backend.analytics.CreatorStatsRepository;
import com.joinlivora.backend.payment.Payment;
import com.joinlivora.backend.payment.PaymentRepository;
import com.joinlivora.backend.token.CreatorEarningsRepository;
import com.joinlivora.backend.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreatorEarningsBalanceUpdateTest {

    @Mock
    private CreatorEarningRepository creatorEarningRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private CreatorEarningsRepository legacyCreatorEarningsRepository;
    @Mock
    private PayoutRepository payoutRepository;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private CreatorStatsRepository creatorStatsRepository;
    @Mock
    private LegacyCreatorProfileRepository creatorProfileRepository;
    @Mock
    private PayoutHoldService payoutHoldService;
    @Mock
    private StripePayoutAdapter stripePayoutAdapter;
    @Mock
    private StripeAccountRepository stripeAccountRepository;
    @Mock
    private PayoutCreatorEarningsRepository payoutCreatorEarningsRepository;

    @InjectMocks
    private CreatorEarningsService creatorEarningsService;

    private User creator;
    private User viewer;

    @BeforeEach
    void setUp() {
        creator = new User();
        creator.setId(2L);
        creator.setEmail("creator@test.com");

        viewer = new User();
        viewer.setId(1L);
        viewer.setEmail("viewer@test.com");

        ReflectionTestUtils.setField(creatorEarningsService, "platformFeePercentage", 30);
    }

    @Test
    void recordTipEarning_ShouldUpdatePayoutCreatorEarnings() {
        // Arrange
        Payment payment = new Payment();
        payment.setAmount(new BigDecimal("10.00"));
        payment.setCurrency("EUR");
        payment.setStripePaymentIntentId("pi_123");

        CreatorEarnings earnings = CreatorEarnings.builder()
                .creator(creator)
                .availableBalance(BigDecimal.ZERO)
                .pendingBalance(BigDecimal.ZERO)
                .totalEarned(BigDecimal.ZERO)
                .build();

        when(payoutCreatorEarningsRepository.findByCreator(creator)).thenReturn(Optional.of(earnings));
        // Mocking legacy balance update to avoid NPE if any
        when(legacyCreatorEarningsRepository.findByUser(creator)).thenReturn(Optional.empty());

        // Act
        creatorEarningsService.recordTipEarning(payment, creator);

        // Assert
        // Net amount is 7.00 (10.00 - 30%)
        verify(payoutCreatorEarningsRepository).save(argThat(e -> 
            e.getAvailableBalance().compareTo(new BigDecimal("7.00")) == 0 &&
            e.getTotalEarned().compareTo(new BigDecimal("7.00")) == 0 &&
            e.getPendingBalance().compareTo(BigDecimal.ZERO) == 0
        ));
    }

    @Test
    void recordDirectTipEarning_ShouldUpdatePayoutCreatorEarnings() {
        // Arrange
        com.joinlivora.backend.tip.DirectTip tip = com.joinlivora.backend.tip.DirectTip.builder()
                .creator(creator)
                .amount(new BigDecimal("10.00"))
                .currency("EUR")
                .status(com.joinlivora.backend.tip.TipStatus.COMPLETED)
                .build();

        CreatorEarnings earnings = CreatorEarnings.builder()
                .creator(creator)
                .availableBalance(BigDecimal.ZERO)
                .pendingBalance(BigDecimal.ZERO)
                .totalEarned(BigDecimal.ZERO)
                .build();

        when(payoutCreatorEarningsRepository.findByCreator(creator)).thenReturn(Optional.of(earnings));
        when(legacyCreatorEarningsRepository.findByUser(creator)).thenReturn(Optional.empty());

        // Act
        creatorEarningsService.recordDirectTipEarning(tip);

        // Assert
        // Net amount is 7.00 (10.00 - 30%)
        verify(payoutCreatorEarningsRepository).save(argThat(e -> 
            e.getAvailableBalance().compareTo(new BigDecimal("7.00")) == 0 &&
            e.getTotalEarned().compareTo(new BigDecimal("7.00")) == 0
        ));
        verify(creatorEarningRepository).save(any(CreatorEarning.class));
    }

    @Test
    void recordTokenTipEarning_ShouldUpdatePayoutCreatorEarnings() {
        // Arrange
        long amount = 100; // 100 tokens = 1.00 EUR
        UUID roomId = UUID.randomUUID();

        CreatorEarnings earnings = CreatorEarnings.builder()
                .creator(creator)
                .availableBalance(BigDecimal.ZERO)
                .pendingBalance(BigDecimal.ZERO)
                .totalEarned(BigDecimal.ZERO)
                .build();

        when(payoutCreatorEarningsRepository.findByCreator(creator)).thenReturn(Optional.of(earnings));
        when(legacyCreatorEarningsRepository.findByUser(creator)).thenReturn(Optional.empty());

        // Act
        creatorEarningsService.recordTokenTipEarning(viewer, creator, amount, roomId, null);

        // Assert
        // Net is 70 tokens (100 - 30%) -> 0.70 EUR
        verify(payoutCreatorEarningsRepository).save(argThat(e -> 
            e.getAvailableBalance().compareTo(new BigDecimal("0.70")) == 0 &&
            e.getTotalEarned().compareTo(new BigDecimal("0.70")) == 0
        ));
    }

    @Test
    void recordTipEarning_WithHold_ShouldUpdatePendingBalance() {
        // Arrange
        Payment payment = new Payment();
        payment.setAmount(new BigDecimal("10.00"));
        payment.setCurrency("EUR");
        payment.setRiskLevel(com.joinlivora.backend.fraud.model.RiskLevel.HIGH);

        CreatorEarnings earnings = CreatorEarnings.builder()
                .creator(creator)
                .availableBalance(BigDecimal.ZERO)
                .pendingBalance(BigDecimal.ZERO)
                .totalEarned(BigDecimal.ZERO)
                .build();

        when(payoutCreatorEarningsRepository.findByCreator(creator)).thenReturn(Optional.of(earnings));
        when(legacyCreatorEarningsRepository.findByUser(creator)).thenReturn(Optional.empty());
        when(payoutHoldService.createHold(any(), any(), any(), any())).thenReturn(mock(PayoutHold.class));

        // Act
        creatorEarningsService.recordTipEarning(payment, creator);

        // Assert
        verify(payoutCreatorEarningsRepository).save(argThat(e -> 
            e.getAvailableBalance().compareTo(BigDecimal.ZERO) == 0 &&
            e.getPendingBalance().compareTo(new BigDecimal("7.00")) == 0 &&
            e.getTotalEarned().compareTo(new BigDecimal("7.00")) == 0
        ));
    }

    @Test
    void unlockExpiredEarnings_ShouldMoveBalanceToAvailable() {
        // Arrange
        CreatorEarning expiredEarning = CreatorEarning.builder()
                .creator(creator)
                .netAmount(new BigDecimal("7.00"))
                .currency("EUR")
                .locked(true)
                .build();

        when(creatorEarningRepository.findExpiredLockedEarnings(any())).thenReturn(java.util.List.of(expiredEarning));
        
        CreatorEarnings earnings = CreatorEarnings.builder()
                .creator(creator)
                .availableBalance(BigDecimal.ZERO)
                .pendingBalance(new BigDecimal("7.00"))
                .totalEarned(new BigDecimal("7.00"))
                .build();

        when(payoutCreatorEarningsRepository.findByCreator(creator)).thenReturn(Optional.of(earnings));
        when(legacyCreatorEarningsRepository.findByUser(creator)).thenReturn(Optional.of(com.joinlivora.backend.token.CreatorEarnings.builder()
                .availableTokens(0)
                .lockedTokens(700)
                .build()));

        // Act
        creatorEarningsService.unlockExpiredEarnings();

        // Assert
        verify(payoutCreatorEarningsRepository).save(argThat(e -> 
            e.getAvailableBalance().compareTo(new BigDecimal("7.00")) == 0 &&
            e.getPendingBalance().compareTo(BigDecimal.ZERO) == 0
        ));
    }
}









