package com.joinlivora.backend.payout;

import com.joinlivora.backend.fraud.model.RiskSubjectType;
import com.joinlivora.backend.payment.Payment;
import com.joinlivora.backend.payout.event.EarningsUnlockedEvent;
import com.joinlivora.backend.token.CreatorEarnings;
import com.joinlivora.backend.token.CreatorEarningsRepository;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EarningsHoldIntegrationTest {

    @Autowired
    private CreatorEarningsService earningsService;

    @Autowired
    private CreatorEarningRepository earningRepository;

    @Autowired
    private CreatorEarningsRepository summaryRepository;

    @Autowired
    private PayoutHoldPolicyRepository holdPolicyRepository;

    @Autowired
    private PayoutHoldRepository payoutHoldRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestEventListener testEventListener;

    private User creator;

    @org.springframework.boot.test.context.TestConfiguration
    static class TestConfig {
        @org.springframework.context.annotation.Bean
        public TestEventListener testEventListener() {
            return new TestEventListener();
        }
    }

    static class TestEventListener {
        public EarningsUnlockedEvent event;
        @org.springframework.context.event.EventListener
        public void handle(EarningsUnlockedEvent event) {
            this.event = event;
        }
    }

    @BeforeEach
    void setUp() {
        creator = new User();
        String creatorSuffix = UUID.randomUUID().toString().substring(0, 8);
        creator.setEmail("creator-" + creatorSuffix + "@test.com");
        creator.setUsername("creator-" + creatorSuffix);
        creator.setPassword("password");
        creator.setRole(com.joinlivora.backend.user.Role.CREATOR);
        creator = userRepository.save(creator);
    }

    @Test
    void whenHoldActive_earningsShouldBeLocked() {
        User viewer = new User();
        viewer.setEmail("viewer@test.com");
        viewer.setUsername("viewer");
        viewer.setPassword("password");
        viewer.setRole(com.joinlivora.backend.user.Role.USER);
        viewer = userRepository.save(viewer);

        // Given an active hold policy
        holdPolicyRepository.save(PayoutHoldPolicy.builder()
                .subjectType(RiskSubjectType.CREATOR)
                .subjectId(new UUID(0L, creator.getId()))
                .holdLevel(HoldLevel.MEDIUM)
                .holdDays(7)
                .expiresAt(Instant.now().plus(7, ChronoUnit.DAYS))
                .build());

        // When earnings are generated
        Payment payment = new Payment();
        payment.setAmount(new BigDecimal("100.00"));
        payment.setCurrency("EUR");
        payment.setUser(viewer);

        earningsService.recordTipEarning(payment, creator);

        // Then CreatorEarning history record should be locked
        List<CreatorEarning> history = earningRepository.findAllByCreatorOrderByCreatedAtDesc(creator);
        assertEquals(1, history.size());
        assertTrue(history.get(0).isLocked());
        assertNotNull(history.get(0).getHoldPolicy());

        // And CreatorEarnings summary should have locked tokens
        CreatorEarnings summary = summaryRepository.findByUser(creator).orElseThrow();
        assertTrue(summary.getLockedTokens() > 0);
        assertEquals(0, summary.getAvailableTokens());
    }

    @Test
    void whenNoHoldActive_earningsShouldNotBeLocked() {
        User viewer = new User();
        viewer.setEmail("viewer-2@test.com");
        viewer.setUsername("viewer-2");
        viewer.setPassword("password");
        viewer.setRole(com.joinlivora.backend.user.Role.USER);
        viewer = userRepository.save(viewer);

        // When earnings are generated without hold
        Payment payment = new Payment();
        payment.setAmount(new BigDecimal("100.00"));
        payment.setCurrency("EUR");
        payment.setUser(viewer);

        earningsService.recordTipEarning(payment, creator);

        // Then CreatorEarning history record should NOT be locked
        List<CreatorEarning> history = earningRepository.findAllByCreatorOrderByCreatedAtDesc(creator);
        assertEquals(1, history.size());
        assertFalse(history.get(0).isLocked());

        // And CreatorEarnings summary should have available tokens
        CreatorEarnings summary = summaryRepository.findByUser(creator).orElseThrow();
        assertEquals(0, summary.getLockedTokens());
        assertTrue(summary.getAvailableTokens() > 0);
    }

    @Test
    void whenPayoutHoldActive_earningsShouldBeLocked() {
        User viewer = new User();
        viewer.setEmail("viewer-3@test.com");
        viewer.setUsername("viewer-3");
        viewer.setPassword("password");
        viewer.setRole(com.joinlivora.backend.user.Role.USER);
        viewer = userRepository.save(viewer);

        // Given an active PayoutHold
        payoutHoldRepository.save(PayoutHold.builder()
                .userId(new UUID(0L, creator.getId()))
                .status(PayoutHoldStatus.ACTIVE)
                .riskLevel(com.joinlivora.backend.fraud.model.RiskLevel.HIGH)
                .holdUntil(Instant.now().plus(7, ChronoUnit.DAYS))
                .build());

        // When earnings are generated
        Payment payment = new Payment();
        payment.setAmount(new BigDecimal("100.00"));
        payment.setCurrency("EUR");
        payment.setUser(viewer);

        earningsService.recordTipEarning(payment, creator);

        // Then CreatorEarning history record should be locked
        List<CreatorEarning> history = earningRepository.findAllByCreatorOrderByCreatedAtDesc(creator);
        assertEquals(1, history.size());
        assertTrue(history.get(0).isLocked());
        assertNotNull(history.get(0).getPayoutHold());

        // And CreatorEarnings summary should have locked tokens
        CreatorEarnings summary = summaryRepository.findByUser(creator).orElseThrow();
        assertTrue(summary.getLockedTokens() > 0);
    }

    @Test
    void unlockExpiredPayoutHolds_ShouldWork() {
        // Given a locked earning from an expired PayoutHold
        PayoutHold expiredHold = payoutHoldRepository.save(PayoutHold.builder()
                .userId(new UUID(0L, creator.getId()))
                .status(PayoutHoldStatus.ACTIVE)
                .riskLevel(com.joinlivora.backend.fraud.model.RiskLevel.MEDIUM)
                .holdUntil(Instant.now().minus(1, ChronoUnit.HOURS))
                .build());

        BigDecimal netAmount = new BigDecimal("70.00");
        CreatorEarning lockedEarning = CreatorEarning.builder()
                .creator(creator)
                .grossAmount(new BigDecimal("100.00"))
                .platformFee(new BigDecimal("30.00"))
                .netAmount(netAmount)
                .currency("EUR")
                .sourceType(EarningSource.TIP)
                .locked(true)
                .payoutHold(expiredHold)
                .build();
        earningRepository.save(lockedEarning);

        summaryRepository.save(CreatorEarnings.builder()
                .user(creator)
                .totalEarnedTokens(7000)
                .availableTokens(0)
                .lockedTokens(7000)
                .build());

        // When releasing and unlocking
        payoutHoldRepository.findAllByStatusAndHoldUntilBefore(PayoutHoldStatus.ACTIVE, Instant.now()).forEach(h -> {
            h.setStatus(PayoutHoldStatus.RELEASED);
            payoutHoldRepository.save(h);
        });
        earningsService.unlockExpiredEarnings();

        // Then earning should be unlocked
        CreatorEarning updatedEarning = earningRepository.findById(lockedEarning.getId()).orElseThrow();
        assertFalse(updatedEarning.isLocked());

        // And summary should be updated
        CreatorEarnings summary = summaryRepository.findByUser(creator).orElseThrow();
        assertEquals(7000, summary.getAvailableTokens());
        assertEquals(0, summary.getLockedTokens());
    }

    @Autowired
    private PayoutService payoutService;

    @Test
    void cancelledPayoutHolds_ShouldPermanentlyBlockPayout() {
        // Given a locked earning from a CANCELLED PayoutHold
        PayoutHold cancelledHold = payoutHoldRepository.save(PayoutHold.builder()
                .userId(new UUID(0L, creator.getId()))
                .status(PayoutHoldStatus.CANCELLED)
                .riskLevel(com.joinlivora.backend.fraud.model.RiskLevel.HIGH)
                .holdUntil(Instant.now().minus(1, ChronoUnit.HOURS))
                .build());

        BigDecimal netAmount = new BigDecimal("70.00");
        CreatorEarning lockedEarning = CreatorEarning.builder()
                .creator(creator)
                .grossAmount(new BigDecimal("100.00"))
                .platformFee(new BigDecimal("30.00"))
                .netAmount(netAmount)
                .currency("EUR")
                .sourceType(EarningSource.TIP)
                .locked(true)
                .payoutHold(cancelledHold)
                .build();
        earningRepository.save(lockedEarning);

        summaryRepository.save(CreatorEarnings.builder()
                .user(creator)
                .totalEarnedTokens(7000)
                .availableTokens(0)
                .lockedTokens(7000)
                .build());

        // When attempting to unlock
        earningsService.unlockExpiredEarnings();

        // Then earning should STILL be locked
        CreatorEarning updatedEarning = earningRepository.findById(lockedEarning.getId()).orElseThrow();
        assertTrue(updatedEarning.isLocked());

        // And summary should NOT be updated
        CreatorEarnings summary = summaryRepository.findByUser(creator).orElseThrow();
        assertEquals(0, summary.getAvailableTokens());
        assertEquals(7000, summary.getLockedTokens());
    }

    @Test
    void calculateAvailablePayout_ShouldSkipLockedEarnings() {
        // Given one locked and one unlocked earning
        earningRepository.save(CreatorEarning.builder()
                .creator(creator)
                .grossAmount(new BigDecimal("10000"))
                .platformFee(new BigDecimal("3000"))
                .netAmount(new BigDecimal("7000"))
                .currency("TOKEN") // 7000 tokens
                .sourceType(EarningSource.TIP)
                .locked(true)
                .build());

        earningRepository.save(CreatorEarning.builder()
                .creator(creator)
                .grossAmount(new BigDecimal("5000"))
                .platformFee(new BigDecimal("1500"))
                .netAmount(new BigDecimal("3500"))
                .currency("TOKEN") // 3500 tokens
                .sourceType(EarningSource.TIP)
                .locked(false)
                .build());

        CreatorEarnings summary = summaryRepository.save(CreatorEarnings.builder()
                .user(creator)
                .totalEarnedTokens(10500)
                .availableTokens(3500)
                .lockedTokens(7000)
                .build());

        // When calculating available payout
        BigDecimal available = payoutService.calculateAvailablePayout(summary.getId());

        // Then it should only include the unlocked one (3500 tokens * 0.01 = 35.00 EUR)
        assertEquals(0, new BigDecimal("35.00").compareTo(available));
    }

    @Test
    void unlockExpiredEarnings_ShouldWorkAndEmitEvent() {
        // Given a locked earning from an expired hold
        PayoutHoldPolicy expiredPolicy = holdPolicyRepository.save(PayoutHoldPolicy.builder()
                .subjectType(RiskSubjectType.CREATOR)
                .subjectId(new UUID(0L, creator.getId()))
                .holdLevel(HoldLevel.SHORT)
                .holdDays(3)
                .expiresAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .build());

        BigDecimal netAmount = new BigDecimal("70.00");
        CreatorEarning lockedEarning = CreatorEarning.builder()
                .creator(creator)
                .grossAmount(new BigDecimal("100.00"))
                .platformFee(new BigDecimal("30.00"))
                .netAmount(netAmount)
                .currency("EUR")
                .sourceType(EarningSource.TIP)
                .locked(true)
                .holdPolicy(expiredPolicy)
                .build();
        earningRepository.save(lockedEarning);

        summaryRepository.save(CreatorEarnings.builder()
                .user(creator)
                .totalEarnedTokens(7000)
                .availableTokens(0)
                .lockedTokens(7000)
                .build());

        // When unlocking
        earningsService.unlockExpiredEarnings();

        // Then earning should be unlocked
        CreatorEarning updatedEarning = earningRepository.findById(lockedEarning.getId()).orElseThrow();
        assertFalse(updatedEarning.isLocked());

        // And summary should be updated
        CreatorEarnings summary = summaryRepository.findByUser(creator).orElseThrow();
        assertEquals(7000, summary.getAvailableTokens());
        assertEquals(0, summary.getLockedTokens());

        // And event should be emitted
        assertNotNull(testEventListener.event);
        assertEquals(creator.getId(), testEventListener.event.getCreator().getId());
        assertEquals(7000, testEventListener.event.getUnlockedTokens());
    }
}








