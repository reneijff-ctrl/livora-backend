package com.joinlivora.backend.analytics;

import com.joinlivora.backend.payment.PaymentRepository;
import com.joinlivora.backend.payment.UserSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlatformAnalyticsService {

    private final PlatformAnalyticsRepository platformAnalyticsRepository;
    private final AnalyticsEventRepository analyticsEventRepository;
    private final PaymentRepository paymentRepository;
    private final UserSubscriptionRepository subscriptionRepository;
    private final CreatorStatsRepository creatorStatsRepository;
    private final ExperimentAnalyticsRepository experimentAnalyticsRepository;

    @Transactional
    public void generateDailyPlatformAnalytics(LocalDate date) {
        log.info("Generating daily platform analytics for {}", date);

        Instant start = date.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        BigDecimal revenue = paymentRepository.calculateRevenueByPeriod(start, end);
        long visits = analyticsEventRepository.countUniqueVisitsByPeriod(start, end);
        long registrations = analyticsEventRepository.countRegistrationsByPeriod(start, end);
        long newSubs = analyticsEventRepository.countNewSubscriptionsByPeriod(start, end);
        long churned = analyticsEventRepository.countCanceledSubscriptionsByPeriod(start, end);
        long activeSubs = subscriptionRepository.countActiveSubscriptions(); // This is a snapshot of current state

        PlatformAnalytics analytics = PlatformAnalytics.builder()
                .date(date)
                .totalRevenue(revenue != null ? revenue : BigDecimal.ZERO)
                .uniqueVisits(visits)
                .registrations(registrations)
                .newSubscriptions(newSubs)
                .churnedSubscriptions(churned)
                .activeSubscriptions(activeSubs)
                .build();

        platformAnalyticsRepository.save(analytics);
        log.info("Saved platform analytics for {}", date);
        
        computeExperimentAnalytics();
    }

    @Transactional
    public void computeExperimentAnalytics() {
        log.info("Computing experiment analytics");
        List<String> keys = analyticsEventRepository.findActiveExperimentKeys();
        for (String key : keys) {
            List<Object[]> results = analyticsEventRepository.countByExperimentKeyAndVariant(key);
            for (Object[] row : results) {
                String variant = (String) row[0];
                long count = ((Number) row[1]).longValue();
                
                ExperimentAnalytics entity = ExperimentAnalytics.builder()
                        .experimentKey(key)
                        .variant(variant)
                        .count(count)
                        .updatedAt(Instant.now())
                        .build();
                experimentAnalyticsRepository.save(entity);
            }
        }
    }

    @Transactional
    public void resetTodayStats() {
        log.info("ANALYTICS: Resetting daily stats for all creators via atomic update");
        creatorStatsRepository.resetAllTodayStats(Instant.now());
    }
}
