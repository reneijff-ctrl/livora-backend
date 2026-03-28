package com.joinlivora.backend.payout;

import com.joinlivora.backend.analytics.CreatorStats;
import com.joinlivora.backend.analytics.CreatorStatsRepository;
import com.joinlivora.backend.payment.Payment;
import com.joinlivora.backend.payment.PaymentRepository;
import com.joinlivora.backend.payout.dto.CreatorEarningsUpdateDTO;
import com.joinlivora.backend.payout.event.EarningsUpdatedEvent;
import com.joinlivora.backend.token.CreatorEarnings;
import com.joinlivora.backend.token.CreatorEarningsRepository;
import com.joinlivora.backend.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreatorEarningsServiceTest {

    @Mock
    private CreatorEarningRepository creatorEarningRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private CreatorEarningsRepository creatorEarningsRepository;
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
    @Mock
    private PlatformBalanceRepository platformBalanceRepository;

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
    void recordTokenTipEarning_ShouldRecordCorrectAmounts() {
        long amount = 100;
        UUID roomId = UUID.randomUUID();
        
        when(creatorEarningsRepository.findByUserWithLock(creator)).thenReturn(Optional.empty());
        when(creatorEarningsRepository.saveAndFlush(any(CreatorEarnings.class))).thenAnswer(inv -> inv.getArgument(0));
        when(payoutCreatorEarningsRepository.findByUserWithLock(creator)).thenReturn(Optional.empty());
        when(platformBalanceRepository.findSingleWithLock()).thenReturn(Optional.empty());

        creatorEarningsService.recordTokenTipEarning(viewer, creator, amount, roomId, null);

        // Verify history record
        ArgumentCaptor<CreatorEarning> earningCaptor = ArgumentCaptor.forClass(CreatorEarning.class);
        verify(creatorEarningRepository).save(earningCaptor.capture());
        
        CreatorEarning recorded = earningCaptor.getValue();
        assertEquals(new BigDecimal("100"), recorded.getGrossAmount());
        assertEquals(new BigDecimal("30"), recorded.getPlatformFee()); // 30% of 100
        assertEquals(new BigDecimal("70"), recorded.getNetAmount());
        assertEquals("TOKEN", recorded.getCurrency());
        assertEquals(EarningSource.TIP, recorded.getSourceType());

        // Verify balance update
        verify(creatorEarningsRepository).save(argThat(earnings -> 
                earnings.getTotalEarnedTokens() == 70 && earnings.getAvailableTokens() == 70));
    }

    @Test
    void recordTipEarning_Stripe_ShouldRecordCorrectAmountsAndCreditTokens() {
        Payment payment = new Payment();
        payment.setAmount(new BigDecimal("10.00"));
        payment.setCurrency("EUR");
        payment.setStripePaymentIntentId("pi_123");

        when(creatorEarningsRepository.findByUserWithLock(creator)).thenReturn(Optional.empty());
        when(creatorEarningsRepository.saveAndFlush(any(CreatorEarnings.class))).thenAnswer(inv -> inv.getArgument(0));
        when(payoutCreatorEarningsRepository.findByUserWithLock(creator)).thenReturn(Optional.empty());
        when(platformBalanceRepository.findSingleWithLock()).thenReturn(Optional.empty());

        creatorEarningsService.recordTipEarning(payment, creator);

        // Verify history record
        ArgumentCaptor<CreatorEarning> earningCaptor = ArgumentCaptor.forClass(CreatorEarning.class);
        verify(creatorEarningRepository).save(earningCaptor.capture());
        
        CreatorEarning recorded = earningCaptor.getValue();
        assertEquals(new BigDecimal("10.00"), recorded.getGrossAmount());
        assertEquals(new BigDecimal("3.00"), recorded.getPlatformFee()); // 30% of 10.00
        assertEquals(new BigDecimal("7.00"), recorded.getNetAmount());
        assertEquals("EUR", recorded.getCurrency());
        assertEquals(EarningSource.TIP, recorded.getSourceType());

        // Verify balance update (7.00 EUR -> 700 Tokens)
        verify(creatorEarningsRepository).save(argThat(earnings -> 
                earnings.getTotalEarnedTokens() == 700 && earnings.getAvailableTokens() == 700));
        
        verify(paymentRepository).save(payment);
        assertEquals(creator, payment.getCreator());
    }

    @Test
    void recordHighlightedChatEarning_ShouldRecordCorrectAmounts() {
        Payment payment = new Payment();
        payment.setAmount(new BigDecimal("5.00")); // e.g. PINNED highlight
        payment.setCurrency("EUR");
        payment.setStripePaymentIntentId("pi_highlight");

        when(creatorEarningsRepository.findByUserWithLock(creator)).thenReturn(Optional.empty());
        when(creatorEarningsRepository.saveAndFlush(any(CreatorEarnings.class))).thenAnswer(inv -> inv.getArgument(0));
        when(payoutCreatorEarningsRepository.findByUserWithLock(creator)).thenReturn(Optional.empty());
        when(platformBalanceRepository.findSingleWithLock()).thenReturn(Optional.empty());

        creatorEarningsService.recordHighlightedChatEarning(payment, creator);

        ArgumentCaptor<CreatorEarning> earningCaptor = ArgumentCaptor.forClass(CreatorEarning.class);
        verify(creatorEarningRepository).save(earningCaptor.capture());
        
        CreatorEarning recorded = earningCaptor.getValue();
        assertEquals(new BigDecimal("5.00"), recorded.getGrossAmount());
        assertEquals(new BigDecimal("1.50"), recorded.getPlatformFee()); // 30%
        assertEquals(new BigDecimal("3.50"), recorded.getNetAmount());
        assertEquals(EarningSource.HIGHLIGHTED_CHAT, recorded.getSourceType());

        // 3.50 EUR -> 350 Tokens
        verify(creatorEarningsRepository).save(argThat(earnings -> 
                earnings.getTotalEarnedTokens() == 350 && earnings.getAvailableTokens() == 350));
    }

    @Test
    void recordEarning_ShouldBroadcastToWebSocketAndPublishEvent() {
        long amount = 100;
        UUID roomId = UUID.randomUUID();
        
        when(creatorEarningsRepository.findByUserWithLock(creator)).thenReturn(Optional.empty());
        when(creatorEarningsRepository.saveAndFlush(any(CreatorEarnings.class))).thenAnswer(inv -> inv.getArgument(0));
        when(payoutCreatorEarningsRepository.findByUserWithLock(creator)).thenReturn(Optional.empty());
        when(platformBalanceRepository.findSingleWithLock()).thenReturn(Optional.empty());
        
        // Mock dependencies for getAggregatedEarnings (called via broadcastUpdate)
        LegacyCreatorProfile profile = LegacyCreatorProfile.builder().id(UUID.randomUUID()).user(creator).build();
        when(creatorProfileRepository.findByUser(creator)).thenReturn(Optional.of(profile));
        
        CreatorStats stats = CreatorStats.builder().creatorId(profile.getId()).totalNetEarnings(new BigDecimal("1000.00")).build();
        when(creatorStatsRepository.findById(profile.getId())).thenReturn(Optional.of(stats));

        creatorEarningsService.recordTokenTipEarning(viewer, creator, amount, roomId, null);

        // Verify WebSocket
        String expectedTopic = "/exchange/amq.topic/creator." + creator.getId() + ".earnings";
        verify(messagingTemplate).convertAndSend(eq(expectedTopic), any(CreatorEarningsUpdateDTO.class));

        // Verify Spring Event
        ArgumentCaptor<EarningsUpdatedEvent> eventCaptor = ArgumentCaptor.forClass(EarningsUpdatedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        
        EarningsUpdatedEvent event = eventCaptor.getValue();
        assertEquals(creator, event.getCreator());
        assertEquals(EarningSource.TIP, event.getSource());
        assertEquals(new BigDecimal("100"), event.getAmount());
        assertEquals("TOKEN", event.getCurrency());
    }

    @Test
    void getCreatorStats_ShouldIncludeHighlightsCount() {
        LegacyCreatorProfile profile = LegacyCreatorProfile.builder().id(UUID.randomUUID()).user(creator).build();
        when(creatorProfileRepository.findByUser(creator)).thenReturn(Optional.of(profile));

        CreatorStats stats = CreatorStats.builder()
                .creatorId(profile.getId())
                .totalNetEarnings(new BigDecimal("100.00"))
                .subscriptionCount(10)
                .tipsCount(5)
                .highlightsCount(3)
                .build();
        when(creatorStatsRepository.findById(profile.getId())).thenReturn(Optional.of(stats));

        java.util.Map<String, Object> statsMap = creatorEarningsService.getCreatorStats(creator);

        assertEquals(new BigDecimal("100.00"), statsMap.get("totalNetEarnings"));
        assertEquals(10L, statsMap.get("subscriptionCount"));
        assertEquals(5L, statsMap.get("tipsCount"));
        assertEquals(3L, statsMap.get("highlightsCount"));
    }

    @Test
    void reverseEarningByStripeId_ShouldDeductBalanceAndRecordReversal() {
        String stripeId = "pi_refund";
        CreatorEarning earning = CreatorEarning.builder()
                .id(UUID.randomUUID())
                .creator(creator)
                .grossAmount(new BigDecimal("10.00"))
                .platformFee(new BigDecimal("3.00"))
                .netAmount(new BigDecimal("7.00"))
                .currency("EUR")
                .sourceType(EarningSource.TIP)
                .stripeChargeId(stripeId)
                .build();

        when(creatorEarningRepository.findByStripeChargeId(stripeId)).thenReturn(Optional.of(earning));
        when(creatorEarningsRepository.findByUserWithLock(creator)).thenReturn(Optional.of(CreatorEarnings.builder()
                .user(creator)
                .totalEarnedTokens(1000)
                .availableTokens(1000)
                .build()));
        when(payoutCreatorEarningsRepository.findByUserWithLock(creator)).thenReturn(Optional.of(com.joinlivora.backend.payout.CreatorEarnings.builder()
                .creator(creator)
                .totalEarned(new BigDecimal("10.00"))
                .availableBalance(new BigDecimal("10.00"))
                .build()));
        when(platformBalanceRepository.findSingleWithLock()).thenReturn(Optional.empty());

        creatorEarningsService.reverseEarningByStripeId(stripeId);

        // 7.00 EUR -> 700 Tokens should be deducted
        verify(creatorEarningsRepository).save(argThat(e -> 
                e.getTotalEarnedTokens() == 300 && e.getAvailableTokens() == 300));

        ArgumentCaptor<CreatorEarning> captor = ArgumentCaptor.forClass(CreatorEarning.class);
        verify(creatorEarningRepository).save(captor.capture());
        CreatorEarning reversal = captor.getValue();
        assertEquals(new BigDecimal("-10.00"), reversal.getGrossAmount());
        assertEquals(new BigDecimal("-7.00"), reversal.getNetAmount());
        assertEquals("pi_refund_reversal", reversal.getStripeChargeId());
        assertEquals(EarningSource.CHARGEBACK, reversal.getSourceType());
    }

    @Test
    void recordTokenTipEarning_RoundingTest() {
        // 33 tokens -> 33 * 0.30 = 9.9 -> 10 platform fee
        long amount = 33;
        
        when(creatorEarningsRepository.findByUserWithLock(creator)).thenReturn(Optional.empty());
        when(creatorEarningsRepository.saveAndFlush(any(CreatorEarnings.class))).thenAnswer(inv -> inv.getArgument(0));
        when(payoutCreatorEarningsRepository.findByUserWithLock(creator)).thenReturn(Optional.empty());
        when(platformBalanceRepository.findSingleWithLock()).thenReturn(Optional.empty());

        creatorEarningsService.recordTokenTipEarning(viewer, creator, amount, null, null);

        ArgumentCaptor<CreatorEarning> earningCaptor = ArgumentCaptor.forClass(CreatorEarning.class);
        verify(creatorEarningRepository).save(earningCaptor.capture());
        
        CreatorEarning recorded = earningCaptor.getValue();
        assertEquals(new BigDecimal("10"), recorded.getPlatformFee());
        assertEquals(new BigDecimal("23"), recorded.getNetAmount());
    }

    @Test
    void recordTokenTipEarning_WithHold_ShouldLockEarnings() {
        long amount = 100;
        UUID roomId = UUID.randomUUID();
        PayoutHold hold = PayoutHold.builder().id(UUID.randomUUID()).build();

        when(payoutHoldService.createHold(eq(creator), eq(com.joinlivora.backend.fraud.model.RiskLevel.HIGH), any(), anyString()))
                .thenReturn(hold);
        when(creatorEarningsRepository.findByUserWithLock(creator)).thenReturn(Optional.empty());
        when(creatorEarningsRepository.saveAndFlush(any(CreatorEarnings.class))).thenAnswer(inv -> inv.getArgument(0));
        when(payoutCreatorEarningsRepository.findByUserWithLock(creator)).thenReturn(Optional.empty());
        when(platformBalanceRepository.findSingleWithLock()).thenReturn(Optional.empty());

        creatorEarningsService.recordTokenTipEarning(viewer, creator, amount, roomId, com.joinlivora.backend.fraud.model.RiskLevel.HIGH);

        // Verify history record is locked and linked to hold
        ArgumentCaptor<CreatorEarning> earningCaptor = ArgumentCaptor.forClass(CreatorEarning.class);
        verify(creatorEarningRepository).save(earningCaptor.capture());
        
        CreatorEarning recorded = earningCaptor.getValue();
        assertTrue(recorded.isLocked());
        assertEquals(hold, recorded.getPayoutHold());

        // Verify balance update uses lockedTokens
        verify(creatorEarningsRepository).save(argThat(ce -> 
                ce.getTotalEarnedTokens() == 70 && ce.getLockedTokens() == 70 && ce.getAvailableTokens() == 0));
    }

    @Test
    void getAggregatedEarnings_ShouldReturnCorrectTotals() {
        LegacyCreatorProfile profile = LegacyCreatorProfile.builder().id(UUID.randomUUID()).user(creator).build();
        when(creatorProfileRepository.findByUser(creator)).thenReturn(Optional.of(profile));

        CreatorStats stats = CreatorStats.builder()
                .creatorId(profile.getId())
                .totalNetTokens(1000L)
                .totalNetEarnings(new BigDecimal("50.00"))
                .todayNetTokens(100L)
                .todayNetEarnings(new BigDecimal("10.00"))
                .updatedAt(Instant.now())
                .build();
        when(creatorStatsRepository.findById(profile.getId())).thenReturn(Optional.of(stats));
        
        when(payoutRepository.sumEurAmountByUserAndStatus(creator, PayoutStatus.PENDING)).thenReturn(new BigDecimal("20.00"));

        com.joinlivora.backend.payout.dto.CreatorEarningsDTO dto = creatorEarningsService.getAggregatedEarnings(creator);

        assertEquals(1000L, dto.getTotalTokens());
        assertEquals(new BigDecimal("50.00"), dto.getTotalRevenue());
        assertEquals(100L, dto.getTodayTokens());
        assertEquals(new BigDecimal("10.00"), dto.getTodayRevenue());
        assertEquals(new BigDecimal("20.00"), dto.getPendingPayout());
        assertNotNull(dto.getLastUpdated());
    }

    @Test
    void creditCreatorBalance_ConcurrentFirstTip_ShouldHandleDuplicateKeyConflict() {
        // Simulate race condition: findByUserWithLock returns empty, saveAndFlush throws duplicate key
        when(creatorEarningsRepository.findByUserWithLock(creator))
                .thenReturn(Optional.empty())  // first call during getOrCreate
                .thenReturn(Optional.of(CreatorEarnings.builder()  // second call after duplicate conflict
                        .user(creator)
                        .totalEarnedTokens(0)
                        .availableTokens(0)
                        .lockedTokens(0)
                        .build()));
        when(creatorEarningsRepository.saveAndFlush(any(CreatorEarnings.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        creatorEarningsService.creditCreatorBalance(creator, 100);

        // Verify it retried with findByUserWithLock after conflict
        verify(creatorEarningsRepository, times(2)).findByUserWithLock(creator);
        // Verify balance was updated on the fetched existing row
        verify(creatorEarningsRepository).save(argThat(earnings ->
                earnings.getTotalEarnedTokens() == 100 && earnings.getAvailableTokens() == 100));
    }

    @Test
    void creditLockedBalance_ConcurrentFirstTip_ShouldHandleDuplicateKeyConflict() {
        when(creatorEarningsRepository.findByUserWithLock(creator))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(CreatorEarnings.builder()
                        .user(creator)
                        .totalEarnedTokens(0)
                        .availableTokens(0)
                        .lockedTokens(0)
                        .build()));
        when(creatorEarningsRepository.saveAndFlush(any(CreatorEarnings.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        creatorEarningsService.creditLockedBalance(creator, 50);

        verify(creatorEarningsRepository, times(2)).findByUserWithLock(creator);
        verify(creatorEarningsRepository).save(argThat(earnings ->
                earnings.getTotalEarnedTokens() == 50 && earnings.getLockedTokens() == 50));
    }
}









