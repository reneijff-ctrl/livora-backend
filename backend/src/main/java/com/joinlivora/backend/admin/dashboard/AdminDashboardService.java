package com.joinlivora.backend.admin.dashboard;

import com.joinlivora.backend.creator.model.CreatorApplicationStatus;
import com.joinlivora.backend.creator.repository.CreatorApplicationRepository;
import com.joinlivora.backend.creator.verification.CreatorVerificationRepository;
import com.joinlivora.backend.creator.verification.VerificationStatus;
import com.joinlivora.backend.livestream.repository.LivestreamSessionRepository;
import com.joinlivora.backend.payout.PayoutRequestRepository;
import com.joinlivora.backend.payout.PayoutRequestStatus;
import com.joinlivora.backend.payout.freeze.PayoutFreezeAuditRepository;
import com.joinlivora.backend.payout.freeze.PayoutFreezeRepository;
import com.joinlivora.backend.payment.PaymentRepository;
import com.joinlivora.backend.report.model.ReportStatus;
import com.joinlivora.backend.report.repository.ReportRepository;
import com.joinlivora.backend.streaming.StreamService;
import com.joinlivora.backend.user.Role;
import com.joinlivora.backend.websocket.PresenceService;
import com.joinlivora.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private final UserRepository userRepository;
    private final CreatorVerificationRepository verificationRepository;
    private final PayoutFreezeRepository payoutFreezeRepository;
    private final PayoutRequestRepository payoutRequestRepository;
    private final PayoutFreezeAuditRepository payoutFreezeAuditRepository;
    private final CreatorApplicationRepository creatorApplicationRepository;
    private final PresenceService presenceService;
    private final StreamService streamService;
    private final LivestreamSessionRepository livestreamSessionRepository;
    private final PaymentRepository paymentRepository;
    private final ReportRepository reportRepository;

    @Cacheable("adminDashboardData")
    @Transactional(readOnly = true)
    public AdminDashboardResponse getDashboardData() {
        return AdminDashboardResponse.builder()
                .metrics(getMetrics())
                .charts(getCharts())
                .build();
    }

    @Cacheable("adminDashboardMetrics")
    @Transactional(readOnly = true)
    public AdminDashboardMetrics getMetrics() {
        Long totalCreators = Optional.ofNullable(userRepository.countByRole(Role.CREATOR)).orElse(0L);
        
        List<Object[]> verificationStats = verificationRepository.countByStatuses();
        long verifiedCreators = 0L;
        long pendingVerifications = 0L;
        for (Object[] row : verificationStats) {
            VerificationStatus status = (VerificationStatus) row[0];
            long count = ((Number) row[1]).longValue();
            if (status == VerificationStatus.APPROVED) verifiedCreators = count;
            else if (status == VerificationStatus.PENDING) pendingVerifications = count;
        }

        Long activeFreezes = Optional.ofNullable(payoutFreezeRepository.countByActiveTrue()).orElse(0L);
        
        List<Object[]> pendingPayoutMetrics = payoutRequestRepository.getPendingPayoutMetrics();
        long pendingPayouts = 0L;
        BigDecimal pendingAmount = BigDecimal.ZERO;
        if (!pendingPayoutMetrics.isEmpty()) {
            Object[] row = pendingPayoutMetrics.get(0);
            pendingPayouts = ((Number) row[0]).longValue();
            pendingAmount = (BigDecimal) row[1];
        }

        Long pendingApplications = Optional.ofNullable(creatorApplicationRepository.countByStatus(CreatorApplicationStatus.UNDER_REVIEW)).orElse(0L);
        Long auditEvents24h = Optional.ofNullable(payoutFreezeAuditRepository.countEventsLast24h()).orElse(0L);
        Long onlineUsers = Optional.ofNullable(presenceService.getOnlineUsersCount()).orElse(0L);
        Long websocketSessions = Optional.ofNullable(presenceService.getActiveSessionsCount()).orElse(0L);
        Long activeStreams = Optional.ofNullable(streamService.getActiveStreamCount()).orElse(0L);
        
        Instant startOfToday = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        Long newUsersToday = Optional.ofNullable(userRepository.countByCreatedAtAfter(startOfToday)).orElse(0L);
        BigDecimal todayRevenue = Optional.ofNullable(paymentRepository.calculateRevenue(startOfToday)).orElse(BigDecimal.ZERO);
        
        Long openReports = Optional.ofNullable(reportRepository.countByStatus(ReportStatus.PENDING)).orElse(0L);

        return AdminDashboardMetrics.builder()
                .totalCreators(totalCreators)
                .verifiedCreators(verifiedCreators)
                .pendingApplications(pendingApplications)
                .activeFreezes(activeFreezes)
                .pendingPayouts(pendingPayouts)
                .pendingAmount(pendingAmount)
                .onlineUsers(onlineUsers)
                .activeStreams(activeStreams)
                .newUsersToday(newUsersToday)
                .todayRevenue(todayRevenue)
                .openReports(openReports)
                .auditEvents24h(auditEvents24h)
                .websocketSessions(websocketSessions)
                .pendingVerifications(pendingVerifications)
                .build();
    }
    
    @Cacheable("adminDashboardPayoutVolume")
    @Transactional(readOnly = true)
    public List<ChartDataPoint> getPayoutVolumeLast30Days() {
        Instant from = Instant.now().minus(30, ChronoUnit.DAYS);
        List<Object[]> rows = payoutRequestRepository.sumCompletedPayoutsGroupedByDay(from);
        return rows.stream().map(row -> {
            Object dayObj = row[0];
            LocalDate date;
            if (dayObj instanceof java.sql.Date) {
                date = ((java.sql.Date) dayObj).toLocalDate();
            } else if (dayObj instanceof LocalDate) {
                date = (LocalDate) dayObj;
            } else if (dayObj instanceof Instant) {
                date = ((Instant) dayObj).atZone(java.time.ZoneId.systemDefault()).toLocalDate();
            } else {
                date = Instant.parse(dayObj.toString()).atZone(java.time.ZoneId.systemDefault()).toLocalDate();
            }
            BigDecimal value = (BigDecimal) row[1];
            return new ChartDataPoint(date, value);
        }).collect(Collectors.toList());
    }

    @Cacheable("adminDashboardFreezesPerDay")
    @Transactional(readOnly = true)
    public List<ChartDataPoint> getFreezesPerDayLast30Days() {
        Instant from = Instant.now().minus(30, ChronoUnit.DAYS);
        List<Object[]> rows = payoutFreezeAuditRepository.countFreezesGroupedByDay(from);
        return rows.stream().map(row -> {
            Object dayObj = row[0];
            LocalDate date;
            if (dayObj instanceof java.sql.Date) {
                date = ((java.sql.Date) dayObj).toLocalDate();
            } else if (dayObj instanceof LocalDate) {
                date = (LocalDate) dayObj;
            } else if (dayObj instanceof Instant) {
                date = ((Instant) dayObj).atZone(java.time.ZoneId.systemDefault()).toLocalDate();
            } else {
                date = Instant.parse(dayObj.toString()).atZone(java.time.ZoneId.systemDefault()).toLocalDate();
            }
            BigDecimal value = BigDecimal.valueOf(((Number) row[1]).longValue());
            return new ChartDataPoint(date, value);
        }).collect(Collectors.toList());
    }

    @Cacheable("adminDashboardCharts")
    @Transactional(readOnly = true)
    public AdminDashboardChartsDTO getCharts() {
        Instant sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS);
        LocalDateTime twentyFourHoursAgo = LocalDateTime.now().minus(24, ChronoUnit.HOURS);

        List<ChartPoint> dailyUsersLast7Days = userRepository.countNewUsersGroupedByDay(sevenDaysAgo).stream()
                .map(row -> new ChartPoint(row[0].toString(), BigDecimal.valueOf(((Number) row[1]).longValue())))
                .collect(Collectors.toList());

        List<ChartPoint> dailyRevenueLast7Days = paymentRepository.calculateRevenueGroupedByDay(sevenDaysAgo).stream()
                .map(row -> new ChartPoint(row[0].toString(), (BigDecimal) row[1]))
                .collect(Collectors.toList());

        List<ChartPoint> streamsLast24Hours = livestreamSessionRepository.countStreamsGroupedByHour(twentyFourHoursAgo).stream()
                .map(row -> {
                    String label = row[0].toString();
                    if (row[0] instanceof java.sql.Timestamp) {
                        label = ((java.sql.Timestamp) row[0]).toLocalDateTime().format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:00"));
                    } else if (row[0] instanceof LocalDateTime) {
                        label = ((LocalDateTime) row[0]).format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:00"));
                    }
                    return new ChartPoint(label, BigDecimal.valueOf(((Number) row[1]).longValue()));
                })
                .collect(Collectors.toList());

        return AdminDashboardChartsDTO.builder()
                .dailyUsersLast7Days(dailyUsersLast7Days)
                .dailyRevenueLast7Days(dailyRevenueLast7Days)
                .streamsLast24Hours(streamsLast24Hours)
                .build();
    }
}
