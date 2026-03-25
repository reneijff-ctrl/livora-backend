package com.joinlivora.backend.fraud.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.joinlivora.backend.fraud.model.EnforcementAction;
import com.joinlivora.backend.fraud.model.FraudScore;
import com.joinlivora.backend.fraud.repository.FraudEventRepository;
import com.joinlivora.backend.fraud.repository.FraudScoreRepository;
import com.joinlivora.backend.payment.AutoFreezePolicyService;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import com.joinlivora.backend.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EnforcementServiceTest {

    @Mock
    private AutoFreezePolicyService autoFreezePolicyService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FraudEventRepository fraudEventRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private com.joinlivora.backend.audit.service.AuditService auditService;

    @InjectMocks
    private EnforcementService enforcementService;

    private UUID userId;
    private User user;

    @BeforeEach
    void setUp() {
        userId = new UUID(0L, 1L);
        user = new User();
        user.setId(1L);
        user.setEmail("test@example.com");
        user.setStatus(UserStatus.ACTIVE);
    }

    @Test
    void freezePayouts_ShouldExecuteAndLog() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        enforcementService.freezePayouts(userId, "Test Reason", 1, 0.1, "evt_123", "SYSTEM", "STRIPE", "1.2.3.4", 45, 40);

        verify(autoFreezePolicyService).freezePayouts(user, "Test Reason");
        verify(fraudEventRepository).save(argThat(event -> 
                event.getEventType() == com.joinlivora.backend.fraud.model.FraudEventType.PAYOUT_FROZEN &&
                event.getMetadata().contains("evt_123") &&
                event.getMetadata().contains("chargebackCount") &&
                event.getMetadata().contains("rate") &&
                event.getMetadata().contains("triggeredBy") &&
                event.getMetadata().contains("SYSTEM") &&
                event.getMetadata().contains("source") &&
                event.getMetadata().contains("STRIPE") &&
                event.getMetadata().contains("ipAddress") &&
                event.getMetadata().contains("1.2.3.4") &&
                event.getMetadata().contains("riskScore") &&
                event.getMetadata().contains("45") &&
                event.getMetadata().contains("thresholdReached") &&
                event.getMetadata().contains("40")
        ));
        verify(auditService).logEvent(eq(null), eq("PAYOUT_FROZEN"), eq("USER"), eq(userId), any(), eq("1.2.3.4"), isNull());
    }

    @Test
    void freezePayouts_Idempotency_ShouldSkipIfAlreadyFrozen() {
        user.setStatus(UserStatus.PAYOUTS_FROZEN);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        enforcementService.freezePayouts(userId, "Test Reason", 1, 0.1, "evt_123", "SYSTEM", "STRIPE", "1.2.3.4", 45, 40);

        verifyNoInteractions(autoFreezePolicyService);
        verifyNoInteractions(fraudEventRepository);
    }

    @Test
    void suspendAccount_ShouldExecuteAndLog() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        enforcementService.suspendAccount(userId, "Test Reason", 2, 0.5, "evt_456", "SYSTEM", "INTERNAL", null, 65, 60);

        verify(autoFreezePolicyService).suspendAccount(user, "Test Reason");
        verify(fraudEventRepository).save(argThat(event -> 
                event.getEventType() == com.joinlivora.backend.fraud.model.FraudEventType.ACCOUNT_SUSPENDED &&
                event.getMetadata().contains("evt_456") &&
                event.getMetadata().contains("triggeredBy") &&
                event.getMetadata().contains("SYSTEM") &&
                event.getMetadata().contains("riskScore") &&
                event.getMetadata().contains("65") &&
                event.getMetadata().contains("thresholdReached") &&
                event.getMetadata().contains("60")
        ));
        verify(auditService).logEvent(eq(null), eq("ACCOUNT_SUSPENDED"), eq("USER"), eq(userId), any(), isNull(), isNull());
    }

    @Test
    void suspendAccount_Idempotency_ShouldSkipIfAlreadySuspended() {
        user.setStatus(UserStatus.SUSPENDED);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        enforcementService.suspendAccount(userId, "Test Reason", null, null, null, "SYSTEM", "INTERNAL", null, null, null);

        verifyNoInteractions(autoFreezePolicyService);
        verifyNoInteractions(fraudEventRepository);
    }

    @Test
    void terminateAccount_ShouldExecuteAndLog() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        enforcementService.terminateAccount(userId, "Test Reason", 3, 0.8, "evt_789", "SYSTEM", "STRIPE", "5.6.7.8", 85, 80);

        verify(autoFreezePolicyService).terminateAccount(user, "Test Reason");
        verify(fraudEventRepository).save(argThat(event -> 
                event.getEventType() == com.joinlivora.backend.fraud.model.FraudEventType.ACCOUNT_TERMINATED &&
                event.getMetadata().contains("evt_789") &&
                event.getMetadata().contains("ipAddress") &&
                event.getMetadata().contains("5.6.7.8") &&
                event.getMetadata().contains("riskScore") &&
                event.getMetadata().contains("85") &&
                event.getMetadata().contains("thresholdReached") &&
                event.getMetadata().contains("80")
        ));
        verify(auditService).logEvent(eq(null), eq("ACCOUNT_TERMINATED"), eq("USER"), eq(userId), any(), eq("5.6.7.8"), isNull());
    }

    @Test
    void terminateAccount_Idempotency_ShouldSkipIfAlreadyTerminated() {
        user.setStatus(UserStatus.TERMINATED);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        enforcementService.terminateAccount(userId, "Test Reason");

        verify(autoFreezePolicyService, never()).terminateAccount(any(), any());
    }

    @Test
    void recordChargeback_ShouldPersistEvent() {
        enforcementService.recordChargeback(userId, "fraudulent", 1, 0.1, "evt_123", "SYSTEM", "STRIPE", "1.1.1.1");

        verify(fraudEventRepository).save(argThat(event -> 
                event.getEventType() == com.joinlivora.backend.fraud.model.FraudEventType.CHARGEBACK_REPORTED &&
                event.getReason().equals("fraudulent") &&
                event.getMetadata().contains("evt_123") &&
                event.getMetadata().contains("chargebackCount") &&
                event.getMetadata().contains("triggeredBy") &&
                event.getMetadata().contains("SYSTEM") &&
                event.getMetadata().contains("ipAddress") &&
                event.getMetadata().contains("1.1.1.1")
        ));
    }

    @Test
    void recordManualOverride_ShouldPersistEvent() {
        enforcementService.recordManualOverride(userId, "Manual unblock", "ADMIN", "INTERNAL", "9.9.9.9");

        verify(fraudEventRepository).save(argThat(event ->
                event.getEventType() == com.joinlivora.backend.fraud.model.FraudEventType.MANUAL_OVERRIDE &&
                event.getReason().equals("Manual unblock") &&
                event.getMetadata().contains("triggeredBy") &&
                event.getMetadata().contains("ADMIN") &&
                event.getMetadata().contains("source") &&
                event.getMetadata().contains("INTERNAL") &&
                event.getMetadata().contains("ipAddress") &&
                event.getMetadata().contains("9.9.9.9")
        ));
    }

    @Test
    void recordPaymentSuccess_ShouldPersistEvent() {
        enforcementService.recordPaymentSuccess(userId, "pi_123", 5000L, "usd", "evt_123", "1.1.1.1");

        verify(fraudEventRepository).save(argThat(event -> 
                event.getEventType() == com.joinlivora.backend.fraud.model.FraudEventType.PAYMENT_SUCCESS &&
                event.getMetadata().contains("pi_123") &&
                event.getMetadata().contains("5000") &&
                event.getMetadata().contains("usd") &&
                event.getMetadata().contains("evt_123") &&
                event.getMetadata().contains("1.1.1.1")
        ));
    }
}








