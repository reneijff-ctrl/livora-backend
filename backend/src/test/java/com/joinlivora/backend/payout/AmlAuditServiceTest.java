package com.joinlivora.backend.payout;

import com.joinlivora.backend.payout.dto.AmlResult;
import com.joinlivora.backend.user.User;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AmlAuditServiceTest {

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private Counter amlRiskCounter;

    @Mock
    private Counter blockedCounter;

    @InjectMocks
    private AmlAuditService amlAuditService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setEmail("test@test.com");
    }

    @Test
    void audit_LowRisk_ShouldEmitDetectedMetric() {
        AmlResult result = new AmlResult(30, List.of("RULE1"));
        when(meterRegistry.counter(eq("aml_risk_detected_total"), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(amlRiskCounter);

        amlAuditService.audit(user, BigDecimal.TEN, result, false);

        verify(amlRiskCounter).increment();
        verifyNoInteractions(blockedCounter);
    }

    @Test
    void audit_HighRiskBlocked_ShouldEmitBothMetrics() {
        AmlResult result = new AmlResult(75, List.of("RULE1", "RULE2"));
        when(meterRegistry.counter(eq("aml_risk_detected_total"), eq("risk_score_range"), eq("70-89"), eq("blocked"), eq("true")))
                .thenReturn(amlRiskCounter);
        when(meterRegistry.counter(eq("payouts_blocked_total"), eq("type"), eq("AML_RISK")))
                .thenReturn(blockedCounter);

        amlAuditService.audit(user, new BigDecimal("100.00"), result, true);

        verify(amlRiskCounter).increment();
        verify(blockedCounter).increment();
    }

    @Test
    void audit_ZeroRisk_ShouldNotEmitMetrics() {
        AmlResult result = new AmlResult(0, List.of());
        
        amlAuditService.audit(user, BigDecimal.TEN, result, false);

        verifyNoInteractions(meterRegistry);
    }
}








