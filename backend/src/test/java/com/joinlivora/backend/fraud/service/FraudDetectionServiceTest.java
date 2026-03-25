package com.joinlivora.backend.fraud.service;

import com.joinlivora.backend.analytics.AnalyticsEvent;
import com.joinlivora.backend.analytics.AnalyticsEventRepository;
import com.joinlivora.backend.analytics.AnalyticsEventType;
import com.joinlivora.backend.fraud.model.FraudDecisionLevel;
import com.joinlivora.backend.fraud.model.RuleFraudSignal;
import com.joinlivora.backend.fraud.model.FraudSource;
import com.joinlivora.backend.fraud.model.UserRiskState;
import com.joinlivora.backend.fraud.repository.RuleFraudSignalRepository;
import com.joinlivora.backend.fraud.repository.UserRiskStateRepository;
import com.joinlivora.backend.payment.PaymentRepository;
import com.joinlivora.backend.user.FraudRiskLevel;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FraudDetectionServiceTest {

    @Mock
    private AnalyticsEventRepository analyticsEventRepository;

    @Mock
    private RuleFraudSignalRepository fraudSignalRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserRiskStateRepository userRiskStateRepository;

    @InjectMocks
    private FraudDetectionService fraudDetectionService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(123L);
        user.setEmail("test@example.com");
        user.setFraudRiskLevel(FraudRiskLevel.LOW);
        lenient().when(userRiskStateRepository.findById(any())).thenReturn(Optional.empty());
        lenient().when(fraudSignalRepository.countByUserIdAndCreatedAtAfter(any(), any())).thenReturn(0L);
        lenient().when(fraudSignalRepository.countByUserIdAndRiskLevelAndCreatedAtAfter(any(), any(), any())).thenReturn(0L);
    }

    @Test
    void evaluate_LowRisk_ShouldReturnLow() {
        when(paymentRepository.countByUserIdAndSuccessAndCreatedAtAfter(anyLong(), eq(false), any()))
                .thenReturn(0L);
        when(analyticsEventRepository.findDistinctIpsByUserIdAndCreatedAtAfter(any(), any()))
                .thenReturn(List.of("1.2.3.4"));
        when(analyticsEventRepository.findFirstByUserIdAndEventTypeOrderByCreatedAtDesc(any(), eq(AnalyticsEventType.USER_LOGIN_SUCCESS)))
                .thenReturn(Optional.empty());

        FraudDecisionLevel decision = fraudDetectionService.evaluate(user, "1.2.3.4", "US");

        assertEquals(FraudDecisionLevel.LOW, decision);
        verify(fraudSignalRepository, never()).save(any());
        verify(userRiskStateRepository).save(argThat(state -> 
                state.getCurrentRisk() == FraudDecisionLevel.LOW &&
                !state.isPaymentLocked() &&
                state.getBlockedUntil() == null
        ));
    }

    @Test
    void evaluate_HighRisk_FailedPayments24h_ShouldReturnHigh() {
        when(paymentRepository.countByUserIdAndSuccessAndCreatedAtAfter(eq(123L), eq(false), any()))
                .thenReturn(3L); // >= 3 in 24h
        when(analyticsEventRepository.findDistinctIpsByUserIdAndCreatedAtAfter(any(), any()))
                .thenReturn(List.of("1.2.3.4"));
        
        // Mock DB finding signals
        lenient().when(fraudSignalRepository.countByUserIdAndRiskLevelAndCreatedAtAfter(any(), eq(FraudDecisionLevel.MEDIUM), any()))
                .thenReturn(1L); // Triggered by 10m rule
        lenient().when(fraudSignalRepository.countByUserIdAndRiskLevelAndCreatedAtAfter(any(), eq(FraudDecisionLevel.HIGH), any()))
                .thenReturn(1L); // Triggered by 24h rule

        FraudDecisionLevel decision = fraudDetectionService.evaluate(user, "1.2.3.4", "US");

        assertEquals(FraudDecisionLevel.HIGH, decision);
        verify(fraudSignalRepository).save(argThat(s -> s.getRiskLevel() == FraudDecisionLevel.HIGH && s.getReason().contains("More than 3 failed payments")));
    }

    @Test
    void evaluate_MediumRisk_FailedPayments10m_ShouldReturnMedium() {
        // Mock 24h as 2 (below 3) and 10m as 2
        when(paymentRepository.countByUserIdAndSuccessAndCreatedAtAfter(eq(123L), eq(false), argThat(t -> t.isBefore(Instant.now().minus(1, java.time.temporal.ChronoUnit.HOURS)))))
                .thenReturn(2L);
        when(paymentRepository.countByUserIdAndSuccessAndCreatedAtAfter(eq(123L), eq(false), argThat(t -> t.isAfter(Instant.now().minus(1, java.time.temporal.ChronoUnit.HOURS)))))
                .thenReturn(2L);

        when(analyticsEventRepository.findDistinctIpsByUserIdAndCreatedAtAfter(any(), any()))
                .thenReturn(List.of("1.2.3.4"));
        
        // Mock DB finding the medium signal we just saved
        when(fraudSignalRepository.countByUserIdAndRiskLevelAndCreatedAtAfter(any(), eq(FraudDecisionLevel.MEDIUM), any()))
                .thenReturn(1L);

        FraudDecisionLevel decision = fraudDetectionService.evaluate(user, "1.2.3.4", "US");

        assertEquals(FraudDecisionLevel.MEDIUM, decision);
        verify(fraudSignalRepository).save(argThat(s -> s.getRiskLevel() == FraudDecisionLevel.MEDIUM && s.getReason().contains("At least 2 failed payments within 10 minutes")));
    }

    @Test
    void evaluate_MediumRisk_DifferentIp_ShouldReturnMedium() {
        when(paymentRepository.countByUserIdAndSuccessAndCreatedAtAfter(anyLong(), eq(false), any()))
                .thenReturn(0L);
        when(analyticsEventRepository.findDistinctIpsByUserIdAndCreatedAtAfter(any(), any()))
                .thenReturn(List.of("5.6.7.8")); // Different from current 1.2.3.4
        when(analyticsEventRepository.findFirstByUserIdAndEventTypeOrderByCreatedAtDesc(any(), eq(AnalyticsEventType.USER_LOGIN_SUCCESS)))
                .thenReturn(Optional.empty());
        
        // Mock DB finding the medium signal we just saved
        when(fraudSignalRepository.countByUserIdAndRiskLevelAndCreatedAtAfter(any(), eq(FraudDecisionLevel.MEDIUM), any()))
                .thenReturn(1L);

        FraudDecisionLevel decision = fraudDetectionService.evaluate(user, "1.2.3.4", "US");

        assertEquals(FraudDecisionLevel.MEDIUM, decision);
        verify(fraudSignalRepository).save(any(RuleFraudSignal.class));
    }

    @Test
    void evaluate_MediumRisk_CountryMismatch_ShouldReturnMedium() {
        AnalyticsEvent lastLogin = new AnalyticsEvent();
        lastLogin.setMetadata(Map.of("country", "UK"));

        when(paymentRepository.countByUserIdAndSuccessAndCreatedAtAfter(anyLong(), eq(false), any()))
                .thenReturn(0L);
        when(analyticsEventRepository.findDistinctIpsByUserIdAndCreatedAtAfter(any(), any()))
                .thenReturn(List.of("1.2.3.4"));
        when(analyticsEventRepository.findFirstByUserIdAndEventTypeOrderByCreatedAtDesc(any(), eq(AnalyticsEventType.USER_LOGIN_SUCCESS)))
                .thenReturn(Optional.of(lastLogin));
        
        // Mock DB finding the medium signal we just saved
        when(fraudSignalRepository.countByUserIdAndRiskLevelAndCreatedAtAfter(any(), eq(FraudDecisionLevel.MEDIUM), any()))
                .thenReturn(1L);

        FraudDecisionLevel decision = fraudDetectionService.evaluate(user, "1.2.3.4", "US"); // Mismatch US vs UK

        assertEquals(FraudDecisionLevel.MEDIUM, decision);
        verify(fraudSignalRepository).save(argThat(s -> "COUNTRY_MISMATCH".equals(s.getReason())));
    }

    @Test
    void evaluate_HighRisk_IpAndCountryMismatch_ShouldReturnHigh() {
        AnalyticsEvent lastLogin = new AnalyticsEvent();
        lastLogin.setMetadata(Map.of("country", "UK"));

        when(paymentRepository.countByUserIdAndSuccessAndCreatedAtAfter(anyLong(), eq(false), any()))
                .thenReturn(0L);
        when(analyticsEventRepository.findDistinctIpsByUserIdAndCreatedAtAfter(any(), any()))
                .thenReturn(List.of("5.6.7.8"));
        when(analyticsEventRepository.findFirstByUserIdAndEventTypeOrderByCreatedAtDesc(any(), eq(AnalyticsEventType.USER_LOGIN_SUCCESS)))
                .thenReturn(Optional.of(lastLogin));
        
        // Mock escalation count - after saving 2 signals, it should count at least 2
        when(fraudSignalRepository.countByUserIdAndRiskLevelAndCreatedAtAfter(any(), eq(FraudDecisionLevel.MEDIUM), any()))
                .thenReturn(2L);
        when(fraudSignalRepository.countByUserIdAndRiskLevelAndCreatedAtAfter(any(), eq(FraudDecisionLevel.HIGH), any()))
                .thenReturn(1L); // The escalation signal

        FraudDecisionLevel decision = fraudDetectionService.evaluate(user, "1.2.3.4", "US"); // Both mismatches

        assertEquals(FraudDecisionLevel.HIGH, decision);
        // Should have 3 signals saved: 2x MEDIUM (rules) + 1x HIGH (escalation)
        verify(fraudSignalRepository, times(3)).save(any(RuleFraudSignal.class));
        verify(fraudSignalRepository).save(argThat(s -> s.getReason().contains("AUTOMATIC_ESCALATION")));
    }

    @Test
    void recordChargeback_ShouldSaveSignalWithMediumRisk() {
        user.setFraudRiskLevel(FraudRiskLevel.LOW);
        fraudDetectionService.recordChargeback(user, "pi_123");

        verify(fraudSignalRepository).save(argThat(signal -> 
                signal.getUserId().equals(123L) &&
                "CHARGEBACK_RECEIVED".equals(signal.getReason()) &&
                signal.getType() == com.joinlivora.backend.fraud.model.FraudSignalType.CHARGEBACK &&
                signal.getRiskLevel() == FraudDecisionLevel.MEDIUM &&
                signal.getSource() == FraudSource.PAYMENT
        ));
        // Verify creator risk level NOT updated here (deferred to ChargebackService)
        assertEquals(FraudRiskLevel.LOW, user.getFraudRiskLevel());
        verify(userRepository, never()).save(user);
        verify(userRiskStateRepository, never()).save(any());
    }

    @Test
    void evaluate_VelocityAnomaly_ShouldReturnHigh() {
        when(paymentRepository.countByUserIdAndSuccessAndCreatedAtAfter(eq(123L), eq(false), any()))
                .thenReturn(5L) // 24h call
                .thenReturn(0L) // 10m call
                .thenReturn(5L); // 1h call

        when(analyticsEventRepository.findDistinctIpsByUserIdAndCreatedAtAfter(any(), any()))
                .thenReturn(List.of("1.2.3.4"));
        
        // Mock DB finding signals
        when(fraudSignalRepository.countByUserIdAndRiskLevelAndCreatedAtAfter(any(), eq(FraudDecisionLevel.MEDIUM), any()))
                .thenReturn(0L);
        when(fraudSignalRepository.countByUserIdAndRiskLevelAndCreatedAtAfter(any(), eq(FraudDecisionLevel.HIGH), any()))
                .thenReturn(1L);

        FraudDecisionLevel decision = fraudDetectionService.evaluate(user, "1.2.3.4", "US");

        assertEquals(FraudDecisionLevel.HIGH, decision);
        verify(fraudSignalRepository).save(argThat(s -> s.getRiskLevel() == FraudDecisionLevel.HIGH && s.getReason().contains("VELOCITY_ANOMALY")));
        verify(userRiskStateRepository).save(argThat(UserRiskState::isPaymentLocked));
        verify(userRiskStateRepository).save(argThat(s -> s.getBlockedUntil() != null));
    }

    @Test
    void evaluate_Escalation_LowToMedium_ShouldWork() {
        user.setFraudRiskLevel(FraudRiskLevel.LOW);
        when(paymentRepository.countByUserIdAndSuccessAndCreatedAtAfter(anyLong(), eq(false), any())).thenReturn(0L);
        when(analyticsEventRepository.findDistinctIpsByUserIdAndCreatedAtAfter(any(), any())).thenReturn(List.of("1.2.3.4"));
        when(analyticsEventRepository.findFirstByUserIdAndEventTypeOrderByCreatedAtDesc(any(), any())).thenReturn(Optional.empty());

        // Mock 2 existing signals (any level) within 24h
        when(fraudSignalRepository.countByUserIdAndCreatedAtAfter(any(), any())).thenReturn(2L);
        // Escalation saves a MEDIUM signal
        when(fraudSignalRepository.countByUserIdAndRiskLevelAndCreatedAtAfter(any(), eq(FraudDecisionLevel.MEDIUM), any())).thenReturn(1L);

        FraudDecisionLevel decision = fraudDetectionService.evaluate(user, "1.2.3.4", "US");

        assertEquals(FraudDecisionLevel.MEDIUM, decision);
        verify(fraudSignalRepository).save(argThat(s -> s.getRiskLevel() == FraudDecisionLevel.MEDIUM && s.getReason().contains("LOW -> MEDIUM")));
    }

    @Test
    void evaluate_Escalation_ShouldUpgradeToHighRisk() {
        // Mock current evaluation as LOW
        when(paymentRepository.countByUserIdAndSuccessAndCreatedAtAfter(anyLong(), eq(false), any()))
                .thenReturn(0L);
        when(analyticsEventRepository.findDistinctIpsByUserIdAndCreatedAtAfter(any(), any()))
                .thenReturn(List.of("1.2.3.4"));
        when(analyticsEventRepository.findFirstByUserIdAndEventTypeOrderByCreatedAtDesc(any(), eq(AnalyticsEventType.USER_LOGIN_SUCCESS)))
                .thenReturn(Optional.empty());

        // Mock 2 existing MEDIUM signals within 24h
        when(fraudSignalRepository.countByUserIdAndRiskLevelAndCreatedAtAfter(any(), eq(FraudDecisionLevel.MEDIUM), any()))
                .thenReturn(2L);
        when(fraudSignalRepository.countByUserIdAndRiskLevelAndCreatedAtAfter(any(), eq(FraudDecisionLevel.HIGH), any()))
                .thenReturn(1L); // Escalation signal

        FraudDecisionLevel decision = fraudDetectionService.evaluate(user, "1.2.3.4", "US");

        assertEquals(FraudDecisionLevel.HIGH, decision);
        assertEquals(FraudRiskLevel.HIGH, user.getFraudRiskLevel());
        verify(fraudSignalRepository).save(argThat(s -> s.getReason().contains("AUTOMATIC_ESCALATION")));
        verify(userRepository).save(user);
    }

    @Test
    void resolveSignal_ShouldUpdateSignal() {
        java.util.UUID signalId = java.util.UUID.randomUUID();
        RuleFraudSignal signal = RuleFraudSignal.builder()
                .id(signalId)
                .type(com.joinlivora.backend.fraud.model.FraudSignalType.VELOCITY_WARNING)
                .resolved(false)
                .build();
        User admin = new User();
        admin.setId(456L);
        admin.setEmail("admin@test.com");

        when(fraudSignalRepository.findById(signalId)).thenReturn(Optional.of(signal));

        fraudDetectionService.resolveSignal(signalId, admin);

        assertEquals(true, signal.isResolved());
        assertEquals(new java.util.UUID(0L, 456L), signal.getResolvedBy());
        org.junit.jupiter.api.Assertions.assertNotNull(signal.getResolvedAt());
        verify(fraudSignalRepository).save(signal);
    }

    @Test
    void overrideRiskLevel_Upgrade_ShouldWork() {
        user.setFraudRiskLevel(FraudRiskLevel.LOW);
        User admin = new User();
        admin.setEmail("admin@test.com");

        fraudDetectionService.overrideRiskLevel(user, FraudRiskLevel.HIGH, admin);

        assertEquals(FraudRiskLevel.HIGH, user.getFraudRiskLevel());
        verify(userRepository).save(user);
        verify(fraudSignalRepository).save(argThat(s -> 
                s.getRiskLevel() == FraudDecisionLevel.HIGH &&
                s.getSource() == FraudSource.ADMIN &&
                s.getReason().contains("ADMIN_OVERRIDE")));
    }

    @Test
    void overrideRiskLevel_Downgrade_NoUnresolvedHighRisk_ShouldWork() {
        user.setFraudRiskLevel(FraudRiskLevel.HIGH);
        User admin = new User();
        admin.setEmail("admin@test.com");

        Long userId = user.getId();
        when(fraudSignalRepository.existsByUserIdAndRiskLevelAndResolvedFalse(userId, FraudDecisionLevel.HIGH))
                .thenReturn(false);

        fraudDetectionService.overrideRiskLevel(user, FraudRiskLevel.LOW, admin);

        assertEquals(FraudRiskLevel.LOW, user.getFraudRiskLevel());
        verify(userRepository).save(user);
        verify(fraudSignalRepository).save(argThat(s -> s.getRiskLevel() == FraudDecisionLevel.LOW));
    }

    @Test
    void overrideRiskLevel_Downgrade_WithUnresolvedHighRisk_ShouldFail() {
        user.setFraudRiskLevel(FraudRiskLevel.HIGH);
        User admin = new User();
        admin.setEmail("admin@test.com");

        Long userId = user.getId();
        when(fraudSignalRepository.existsByUserIdAndRiskLevelAndResolvedFalse(userId, FraudDecisionLevel.HIGH))
                .thenReturn(true);

        assertThrows(IllegalStateException.class, () -> {
            fraudDetectionService.overrideRiskLevel(user, FraudRiskLevel.LOW, admin);
        });
        
        assertEquals(FraudRiskLevel.HIGH, user.getFraudRiskLevel());
        verify(userRepository, never()).save(any());
    }

    @Test
    void processCooldown_NoUnresolvedHighRisk_ShouldCooldown() {
        Long userId = user.getId();
        UserRiskState state = UserRiskState.builder()
                .userId(userId)
                .currentRisk(FraudDecisionLevel.HIGH)
                .blockedUntil(Instant.now().minusSeconds(60))
                .paymentLocked(true)
                .build();

        when(fraudSignalRepository.existsByUserIdAndRiskLevelAndResolvedFalse(userId, FraudDecisionLevel.HIGH))
                .thenReturn(false);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        fraudDetectionService.processCooldown(state);

        verify(fraudSignalRepository).save(argThat(s -> 
                s.getRiskLevel() == FraudDecisionLevel.MEDIUM &&
                s.getSource() == FraudSource.SYSTEM &&
                s.getReason().contains("FRAUD_COOLDOWN")));
        
        assertEquals(FraudRiskLevel.MEDIUM, user.getFraudRiskLevel());
        verify(userRepository).save(user);
        verify(userRiskStateRepository).save(argThat(s -> 
                s.getCurrentRisk() == FraudDecisionLevel.MEDIUM &&
                !s.isPaymentLocked() &&
                s.getBlockedUntil() == null));
    }

    @Test
    void processCooldown_WithUnresolvedHighRisk_ShouldNotCooldown() {
        Long userId = user.getId();
        UserRiskState state = UserRiskState.builder()
                .userId(userId)
                .currentRisk(FraudDecisionLevel.HIGH)
                .blockedUntil(Instant.now().minusSeconds(60))
                .paymentLocked(true)
                .build();

        when(fraudSignalRepository.existsByUserIdAndRiskLevelAndResolvedFalse(userId, FraudDecisionLevel.HIGH))
                .thenReturn(true);

        fraudDetectionService.processCooldown(state);

        verify(fraudSignalRepository, never()).save(any());
        verify(userRepository, never()).save(any());
        verify(userRiskStateRepository, never()).save(any());
    }

    @Test
    void unblockUser_ShouldClearBlockAndUnlockPayments() {
        User admin = new User();
        admin.setEmail("admin@test.com");

        fraudDetectionService.unblockUser(user, admin);

        verify(fraudSignalRepository).save(argThat(s ->
                s.getRiskLevel() == FraudDecisionLevel.MEDIUM &&
                s.getSource() == FraudSource.ADMIN &&
                s.getReason().contains("ADMIN_UNBLOCK")));

        verify(userRiskStateRepository).save(argThat(s ->
                s.getCurrentRisk() == FraudDecisionLevel.MEDIUM &&
                !s.isPaymentLocked() &&
                s.getBlockedUntil() == null));
        
        assertEquals(FraudRiskLevel.MEDIUM, user.getFraudRiskLevel());
        verify(userRepository).save(user);
    }
}








