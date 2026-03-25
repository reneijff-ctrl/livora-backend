package com.joinlivora.backend.monitoring;

import com.joinlivora.backend.fraud.repository.RuleFraudSignalRepository;
import com.joinlivora.backend.fraud.repository.UserRiskStateRepository;
import com.joinlivora.backend.monetization.TipRepository;
import com.joinlivora.backend.monitoring.dto.RevenueSummaryResponse;
import com.joinlivora.backend.monitoring.dto.SystemHealthResponse;
import com.joinlivora.backend.monitoring.dto.SystemMetricsResponse;
import com.joinlivora.backend.payment.PaymentRepository;
import com.joinlivora.backend.payment.UserSubscriptionRepository;
import com.joinlivora.backend.streaming.StreamRepository;
import com.joinlivora.backend.streaming.client.MediasoupClient;
import com.joinlivora.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminMonitoringServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private StreamRepository StreamRepository;
    @Mock
    private TipRepository tipRepository;
    @Mock
    private RuleFraudSignalRepository fraudSignalRepository;
    @Mock
    private UserRiskStateRepository userRiskStateRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private UserSubscriptionRepository userSubscriptionRepository;
    @Mock
    private MediasoupClient mediasoupClient;
    
    private StripeHealthIndicator stripeHealthIndicator;
    private AdminMonitoringService monitoringService;

    @BeforeEach
    void setUp() {
        stripeHealthIndicator = mock(StripeHealthIndicator.class);
        monitoringService = new AdminMonitoringService(
                userRepository,
                StreamRepository,
                tipRepository,
                fraudSignalRepository,
                userRiskStateRepository,
                paymentRepository,
                userSubscriptionRepository,
                mediasoupClient,
                Optional.of(stripeHealthIndicator)
        );
    }

    @Test
    void getSystemHealth_ShouldReturnUp() {
        when(stripeHealthIndicator.health()).thenReturn(org.springframework.boot.actuate.health.Health.up().build());
        
        SystemHealthResponse response = monitoringService.getSystemHealth();
        assertEquals("UP", response.getStatus());
        assertTrue(response.getUptime() > 0);
        assertNotNull(response.getComponents());
        assertEquals("UP", response.getComponents().get("stripe"));
    }

    @Test
    void getSystemHealth_WhenStripeDisabled_ShouldReturnDisabled() {
        AdminMonitoringService disabledService = new AdminMonitoringService(
                userRepository,
                StreamRepository,
                tipRepository,
                fraudSignalRepository,
                userRiskStateRepository,
                paymentRepository,
                userSubscriptionRepository,
                mediasoupClient,
                Optional.empty()
        );

        SystemHealthResponse response = disabledService.getSystemHealth();
        assertEquals("DISABLED", response.getComponents().get("stripe"));
    }

    @Test
    void getSystemMetrics_ShouldReturnData() {
        when(userRepository.count()).thenReturn(100L);
        when(StreamRepository.countByIsLiveTrue()).thenReturn(0L);
        when(paymentRepository.count()).thenReturn(50L);

        SystemMetricsResponse response = monitoringService.getSystemMetrics();
        assertEquals(100L, response.getTotalUsers());
        assertEquals(0L, response.getActiveStreams());
        assertEquals(50L, response.getTotalPayments());
    }

    @Test
    void getRevenueSummary_ShouldAggregateData() {
        when(paymentRepository.calculateRevenue(any(Instant.class))).thenReturn(new BigDecimal("1000.00"));
        when(tipRepository.sumAllPaidTips()).thenReturn(new BigDecimal("500.00"));
        when(tipRepository.count()).thenReturn(20L);
        when(StreamRepository.countByIsLiveTrue()).thenReturn(0L);
        when(userRiskStateRepository.countByPaymentLockedTrue()).thenReturn(5L);
        when(userSubscriptionRepository.countActiveSubscriptions()).thenReturn(10L);

        RevenueSummaryResponse response = monitoringService.getRevenueSummary();
        assertEquals(new BigDecimal("1000.00"), response.getTotalRevenue());
        assertEquals(20L, response.getTotalTipsCount());
        assertEquals(new BigDecimal("500.00"), response.getTotalTipsAmount());
        assertEquals(0L, response.getActiveStreamsCount());
        assertEquals(5L, response.getFraudBlocksCount());
        assertEquals(10L, response.getActiveSubscriptionsCount());
    }
}








