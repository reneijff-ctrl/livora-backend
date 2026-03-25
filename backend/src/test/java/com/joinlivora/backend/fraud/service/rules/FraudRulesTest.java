package com.joinlivora.backend.fraud.service.rules;

import com.joinlivora.backend.fraud.model.DeviceFingerprint;
import com.joinlivora.backend.fraud.repository.DeviceFingerprintRepository;
import com.joinlivora.backend.payment.PaymentRepository;
import com.joinlivora.backend.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FraudRulesTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private DeviceFingerprintRepository deviceFingerprintRepository;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setEmail("test@test.com");
    }

    @Test
    void testAccountAgeRule() {
        AccountAgeRule rule = new AccountAgeRule();
        
        user.setCreatedAt(Instant.now().minus(2, ChronoUnit.HOURS));
        assertEquals(80, rule.evaluate(user, Collections.emptyMap()));

        user.setCreatedAt(Instant.now().minus(5, ChronoUnit.DAYS));
        assertEquals(50, rule.evaluate(user, Collections.emptyMap()));

        user.setCreatedAt(Instant.now().minus(40, ChronoUnit.DAYS));
        assertEquals(0, rule.evaluate(user, Collections.emptyMap()));
    }

    @Test
    void testSpendVelocityRule() {
        SpendVelocityRule rule = new SpendVelocityRule(paymentRepository);
        
        when(paymentRepository.sumAmountByUserIdAndSuccessAndCreatedAtAfter(eq(1L), any())).thenReturn(new BigDecimal("600"));
        assertEquals(100, rule.evaluate(user, Collections.emptyMap()));

        when(paymentRepository.sumAmountByUserIdAndSuccessAndCreatedAtAfter(eq(1L), any())).thenReturn(new BigDecimal("50"));
        assertEquals(0, rule.evaluate(user, Collections.emptyMap()));
    }

    @Test
    void testPaymentFailureRule() {
        PaymentFailureRule rule = new PaymentFailureRule(paymentRepository);
        
        when(paymentRepository.countByUserIdAndSuccessAndCreatedAtAfter(eq(1L), eq(false), any())).thenReturn(5L);
        assertEquals(100, rule.evaluate(user, Collections.emptyMap()));

        when(paymentRepository.countByUserIdAndSuccessAndCreatedAtAfter(eq(1L), eq(false), any())).thenReturn(0L);
        assertEquals(0, rule.evaluate(user, Collections.emptyMap()));
    }

    @Test
    void testLocationMismatchRule() {
        LocationMismatchRule rule = new LocationMismatchRule(paymentRepository);
        Map<String, Object> context = Map.of("country", "FR");

        when(paymentRepository.findLastSuccessfulCountriesByUserId(eq(1L), any())).thenReturn(List.of("US", "UK"));
        assertEquals(40, rule.evaluate(user, context));

        when(paymentRepository.findLastSuccessfulCountriesByUserId(eq(1L), any())).thenReturn(List.of("US", "FR"));
        assertEquals(0, rule.evaluate(user, context));
    }

    @Test
    void testDeviceFingerprintRule() {
        DeviceFingerprintRule rule = new DeviceFingerprintRule(deviceFingerprintRepository);
        Map<String, Object> context = Map.of("deviceFingerprint", "fp123");

        when(deviceFingerprintRepository.findAllByFingerprintHash("fp123")).thenReturn(List.of(
                DeviceFingerprint.builder().userId(1L).build(),
                DeviceFingerprint.builder().userId(2L).build(),
                DeviceFingerprint.builder().userId(3L).build(),
                DeviceFingerprint.builder().userId(4L).build()
        ));
        assertEquals(50, rule.evaluate(user, context));
    }
}








