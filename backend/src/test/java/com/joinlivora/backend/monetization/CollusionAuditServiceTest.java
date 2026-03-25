package com.joinlivora.backend.monetization;

import com.joinlivora.backend.monetization.dto.CollusionResult;
import com.joinlivora.backend.user.User;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CollusionAuditServiceTest {

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private Counter collusionDetectedCounter;

    @Mock
    private Counter restrictedCounter;

    @InjectMocks
    private CollusionAuditService collusionAuditService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setEmail("creator@test.com");
    }

    @Test
    void audit_WithRisk_ShouldEmitMetric() {
        CollusionResult result = new CollusionResult(65, List.of("REPEATED_TIPPING"));
        when(meterRegistry.counter(eq("collusion_detected_total"), eq("score_range"), eq("40-69")))
                .thenReturn(collusionDetectedCounter);

        collusionAuditService.audit(user, result);

        verify(collusionDetectedCounter).increment();
    }

    @Test
    void audit_ZeroRisk_ShouldNotEmitMetric() {
        CollusionResult result = new CollusionResult(0, List.of());

        collusionAuditService.audit(user, result);

        verifyNoInteractions(meterRegistry);
    }

    @Test
    void recordRestriction_ShouldEmitMetric() {
        when(meterRegistry.counter(eq("creators_restricted_total"), eq("restriction_type"), eq("PAYOUTS_FROZEN")))
                .thenReturn(restrictedCounter);

        collusionAuditService.recordRestriction(user, "PAYOUTS_FROZEN", 85);

        verify(restrictedCounter).increment();
    }
}








