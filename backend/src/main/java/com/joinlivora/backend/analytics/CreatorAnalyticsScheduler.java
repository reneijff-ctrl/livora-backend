package com.joinlivora.backend.analytics;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;

@Component
@RequiredArgsConstructor
@Slf4j
public class CreatorAnalyticsScheduler {

    private final CreatorAnalyticsService creatorAnalyticsService;
    private final PlatformAnalyticsService platformAnalyticsService;
    private final LeaderboardCalculationService leaderboardCalculationService;

    /**
     * Runs daily at 00:00 UTC to reset "today" metrics.
     */
    @Scheduled(cron = "0 0 0 * * *", zone = "UTC")
    public void resetTodayStats() {
        log.info("ANALYTICS_JOB: Resetting today's stats for creators");
        platformAnalyticsService.resetTodayStats();
    }

    /**
     * Runs daily at 00:10 UTC to generate analytics for the previous day.
     */
    @Scheduled(cron = "0 10 0 * * *", zone = "UTC")
    public void generatePreviousDayAnalytics() {
        LocalDate yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        log.info("ANALYTICS_JOB: Starting daily analytics generation for {}", yesterday);
        
        creatorAnalyticsService.generateDailyAnalytics(yesterday);
        platformAnalyticsService.generateDailyPlatformAnalytics(yesterday);

        log.info("ANALYTICS_JOB: Completed daily analytics generation for {}", yesterday);
    }

    /**
     * Runs daily at 00:20 UTC to calculate rankings for the previous day.
     */
    @Scheduled(cron = "0 20 0 * * *", zone = "UTC")
    public void calculateDailyRankings() {
        LocalDate yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        log.info("LEADERBOARD_JOB: Starting daily ranking calculation for {}", yesterday);
        leaderboardCalculationService.calculateRankings(LeaderboardPeriod.DAILY, yesterday);
        log.info("LEADERBOARD_JOB: Completed daily ranking calculation for {}", yesterday);
    }

    /**
     * Runs every Monday at 00:30 UTC to calculate weekly rankings.
     */
    @Scheduled(cron = "0 30 0 * * MON", zone = "UTC")
    public void calculateWeeklyRankings() {
        LocalDate yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1); // Sunday
        log.info("LEADERBOARD_JOB: Starting weekly ranking calculation for week ending {}", yesterday);
        leaderboardCalculationService.calculateRankings(LeaderboardPeriod.WEEKLY, yesterday);
        log.info("LEADERBOARD_JOB: Completed weekly ranking calculation for {}", yesterday);
    }

    /**
     * Runs on the first day of month at 00:40 UTC to calculate monthly rankings.
     */
    @Scheduled(cron = "0 40 0 1 * *", zone = "UTC")
    public void calculateMonthlyRankings() {
        LocalDate yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1); // Last day of previous month
        log.info("LEADERBOARD_JOB: Starting monthly ranking calculation for month ending {}", yesterday);
        leaderboardCalculationService.calculateRankings(LeaderboardPeriod.MONTHLY, yesterday);
        log.info("LEADERBOARD_JOB: Completed monthly ranking calculation for {}", yesterday);
    }
}
