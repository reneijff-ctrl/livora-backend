package com.joinlivora.backend.payout;

import com.joinlivora.backend.fraud.model.FraudRiskAssessment;
import com.joinlivora.backend.fraud.model.RiskLevel;
import com.joinlivora.backend.payout.dto.PayoutHoldStatusDTO;
import com.joinlivora.backend.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PayoutHoldServiceTest {

    @Mock
    private PayoutHoldPolicyRepository holdPolicyRepository;
    @Mock
    private PayoutHoldRepository payoutHoldRepository;
    @Mock
    private CreatorEarningRepository earningRepository;

    @InjectMocks
    private PayoutHoldService payoutHoldService;

    private User user;
    private UUID userId;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        userId = new UUID(0L, 1L);
    }

    @Test
    void createHold_HighRisk_ShouldCreateActiveHold() {
        FraudRiskAssessment assessment = FraudRiskAssessment.builder()
                .userId(userId)
                .riskLevel(RiskLevel.HIGH)
                .score(80)
                .build();

        when(payoutHoldRepository.save(any(PayoutHold.class))).thenAnswer(i -> i.getArgument(0));

        PayoutHold result = payoutHoldService.createHold(assessment, UUID.randomUUID());

        assertNotNull(result);
        assertEquals(PayoutHoldStatus.ACTIVE, result.getStatus());
        assertEquals(RiskLevel.HIGH, result.getRiskLevel());
        assertTrue(result.getHoldUntil().isAfter(Instant.now().plus(6, ChronoUnit.DAYS)));
    }

    @Test
    void createHold_LowRisk_ShouldNotCreateHold() {
        FraudRiskAssessment assessment = FraudRiskAssessment.builder()
                .userId(userId)
                .riskLevel(RiskLevel.LOW)
                .score(10)
                .build();

        PayoutHold result = payoutHoldService.createHold(assessment, UUID.randomUUID());

        assertNull(result);
        verify(payoutHoldRepository, never()).save(any());
    }

    @Test
    void hasActiveHold_WhenActiveHoldExists_ShouldReturnTrue() {
        PayoutHold hold = PayoutHold.builder()
                .status(PayoutHoldStatus.ACTIVE)
                .holdUntil(Instant.now().plus(1, ChronoUnit.DAYS))
                .riskLevel(RiskLevel.MEDIUM)
                .build();

        when(payoutHoldRepository.findAllByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(hold));

        boolean hasHold = payoutHoldService.hasActiveHold(user);

        assertTrue(hasHold);
    }

    @Test
    void isPayable_WhenLockedButHoldReleased_ShouldReturnTrue() {
        PayoutHold hold = PayoutHold.builder()
                .status(PayoutHoldStatus.RELEASED)
                .build();
        CreatorEarning earning = CreatorEarning.builder()
                .locked(true)
                .payoutHold(hold)
                .build();

        assertTrue(payoutHoldService.isPayable(earning));
    }

    @Test
    void isPayable_WhenLockedByCancelledHold_ShouldReturnFalse() {
        PayoutHold hold = PayoutHold.builder()
                .status(PayoutHoldStatus.CANCELLED)
                .build();
        CreatorEarning earning = CreatorEarning.builder()
                .locked(true)
                .payoutHold(hold)
                .build();

        assertFalse(payoutHoldService.isPayable(earning));
    }

    @Test
    void isPayable_WhenLockedByActiveHold_ShouldReturnFalse() {
        PayoutHold hold = PayoutHold.builder()
                .status(PayoutHoldStatus.ACTIVE)
                .holdUntil(Instant.now().plus(1, ChronoUnit.DAYS))
                .build();
        CreatorEarning earning = CreatorEarning.builder()
                .locked(true)
                .payoutHold(hold)
                .build();

        assertFalse(payoutHoldService.isPayable(earning));
    }

    @Test
    void releaseExpiredHolds_ShouldUpdateStatus() {
        PayoutHold expired = PayoutHold.builder()
                .id(UUID.randomUUID())
                .status(PayoutHoldStatus.ACTIVE)
                .holdUntil(Instant.now().minus(1, ChronoUnit.HOURS))
                .build();

        when(payoutHoldRepository.findAllByStatusAndHoldUntilBefore(eq(PayoutHoldStatus.ACTIVE), any(Instant.class)))
                .thenReturn(List.of(expired));

        payoutHoldService.releaseExpiredHolds();

        assertEquals(PayoutHoldStatus.RELEASED, expired.getStatus());
        verify(payoutHoldRepository).save(expired);
    }
}








