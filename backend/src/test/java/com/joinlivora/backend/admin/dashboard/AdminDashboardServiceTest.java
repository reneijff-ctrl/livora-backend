package com.joinlivora.backend.admin.dashboard;

import com.joinlivora.backend.creator.repository.CreatorApplicationRepository;
import com.joinlivora.backend.creator.verification.CreatorVerificationRepository;
import com.joinlivora.backend.creator.verification.VerificationStatus;
import com.joinlivora.backend.payout.PayoutRequestRepository;
import com.joinlivora.backend.payout.PayoutRequestStatus;
import com.joinlivora.backend.payout.freeze.PayoutFreezeAuditRepository;
import com.joinlivora.backend.payout.freeze.PayoutFreezeRepository;
import com.joinlivora.backend.payment.PaymentRepository;
import com.joinlivora.backend.report.repository.ReportRepository;
import com.joinlivora.backend.streaming.StreamService;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.user.UserRepository;
import com.joinlivora.backend.websocket.PresenceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private CreatorVerificationRepository verificationRepository;
    @Mock
    private PayoutFreezeRepository payoutFreezeRepository;
    @Mock
    private PayoutRequestRepository payoutRequestRepository;
    @Mock
    private PayoutFreezeAuditRepository payoutFreezeAuditRepository;
    @Mock
    private CreatorApplicationRepository creatorApplicationRepository;
    @Mock
    private PresenceService presenceService;
    @Mock
    private StreamService LiveStreamService;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private ReportRepository reportRepository;

    @InjectMocks
    private AdminDashboardService adminDashboardService;

    @Test
    void getMetrics_ShouldReturnMetricsWithProperCounts() {
        when(userRepository.countByRole(Role.CREATOR)).thenReturn(10L);
        List<Object[]> verificationResults = new java.util.ArrayList<>();
        verificationResults.add(new Object[]{VerificationStatus.APPROVED, 5L});
        verificationResults.add(new Object[]{VerificationStatus.PENDING, 1L});
        when(verificationRepository.countByStatuses()).thenReturn(verificationResults);
        when(payoutFreezeRepository.countByActiveTrue()).thenReturn(2L);
        List<Object[]> pendingResults = new java.util.ArrayList<>();
        pendingResults.add(new Object[]{3L, new BigDecimal("150.00")});
        when(payoutRequestRepository.getPendingPayoutMetrics()).thenReturn(pendingResults);
        when(payoutFreezeAuditRepository.countEventsLast24h()).thenReturn(20L);
        when(presenceService.getOnlineUsersCount()).thenReturn(50L);
        when(presenceService.getActiveSessionsCount()).thenReturn(40L);
        when(LiveStreamService.getActiveStreamCount()).thenReturn(2L);
        when(userRepository.countByCreatedAtAfter(any())).thenReturn(100L);
        when(paymentRepository.calculateRevenue(any())).thenReturn(new BigDecimal("1000.00"));
        when(reportRepository.countByStatus(any())).thenReturn(5L);
        when(creatorApplicationRepository.countByStatus(any())).thenReturn(1L);

        AdminDashboardMetrics metrics = adminDashboardService.getMetrics();

        assertEquals(10L, metrics.getTotalCreators());
        assertEquals(5L, metrics.getVerifiedCreators());
        assertEquals(1L, metrics.getPendingVerifications());
        assertEquals(2L, metrics.getActiveFreezes());
        assertEquals(3L, metrics.getPendingPayouts());
        assertEquals(new BigDecimal("150.00"), metrics.getPendingAmount());
        assertEquals(20L, metrics.getAuditEvents24h());
        assertEquals(50L, metrics.getOnlineUsers());
        assertEquals(40L, metrics.getWebsocketSessions());
        assertEquals(2L, metrics.getActiveStreams());
        assertEquals(100L, metrics.getNewUsersToday());
        assertEquals(new BigDecimal("1000.00"), metrics.getTodayRevenue());
        assertEquals(5L, metrics.getOpenReports());
        assertEquals(1L, metrics.getPendingApplications());
    }

    @Test
    void getMetrics_ShouldHandleNullForSummingRepositories() {
        when(userRepository.countByRole(Role.CREATOR)).thenReturn(0L);
        when(verificationRepository.countByStatuses()).thenReturn(List.of());
        when(payoutFreezeRepository.countByActiveTrue()).thenReturn(0L);
        when(payoutRequestRepository.getPendingPayoutMetrics()).thenReturn(List.of());
        when(payoutFreezeAuditRepository.countEventsLast24h()).thenReturn(0L);
        when(presenceService.getOnlineUsersCount()).thenReturn(0L);
        when(presenceService.getActiveSessionsCount()).thenReturn(0L);
        when(LiveStreamService.getActiveStreamCount()).thenReturn(0L);
        when(userRepository.countByCreatedAtAfter(any())).thenReturn(0L);
        when(paymentRepository.calculateRevenue(any())).thenReturn(null);
        when(reportRepository.countByStatus(any())).thenReturn(0L);
        when(creatorApplicationRepository.countByStatus(any())).thenReturn(0L);

        AdminDashboardMetrics metrics = adminDashboardService.getMetrics();

        assertEquals(0L, metrics.getTotalCreators());
        assertEquals(0L, metrics.getVerifiedCreators());
        assertEquals(0L, metrics.getActiveFreezes());
        assertEquals(0L, metrics.getPendingPayouts());
        assertEquals(BigDecimal.ZERO, metrics.getPendingAmount());
        assertEquals(0L, metrics.getAuditEvents24h());
        assertEquals(0L, metrics.getOnlineUsers());
        assertEquals(0L, metrics.getWebsocketSessions());
        assertEquals(0L, metrics.getActiveStreams());
        assertEquals(0L, metrics.getNewUsersToday());
        assertEquals(BigDecimal.ZERO, metrics.getTodayRevenue());
        assertEquals(0L, metrics.getOpenReports());
        assertEquals(0L, metrics.getPendingApplications());
    }
}








