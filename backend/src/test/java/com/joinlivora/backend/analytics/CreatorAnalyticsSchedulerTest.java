package com.joinlivora.backend.analytics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CreatorAnalyticsSchedulerTest {

    @Mock
    private CreatorAnalyticsService creatorAnalyticsService;

    @Mock
    private PlatformAnalyticsService platformAnalyticsService;

    @Mock
    private LeaderboardCalculationService leaderboardCalculationService;

    @InjectMocks
    private CreatorAnalyticsScheduler scheduler;

    @Test
    void testGeneratePreviousDayAnalytics() {
        LocalDate yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        
        scheduler.generatePreviousDayAnalytics();
        
        verify(creatorAnalyticsService).generateDailyAnalytics(eq(yesterday));
        verify(platformAnalyticsService).generateDailyPlatformAnalytics(eq(yesterday));
    }

    @Test
    void testCalculateDailyRankings() {
        LocalDate yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        scheduler.calculateDailyRankings();
        verify(leaderboardCalculationService).calculateRankings(eq(LeaderboardPeriod.DAILY), eq(yesterday));
    }

    @Test
    void testCalculateWeeklyRankings() {
        LocalDate yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        scheduler.calculateWeeklyRankings();
        verify(leaderboardCalculationService).calculateRankings(eq(LeaderboardPeriod.WEEKLY), eq(yesterday));
    }

    @Test
    void testCalculateMonthlyRankings() {
        LocalDate yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        scheduler.calculateMonthlyRankings();
        verify(leaderboardCalculationService).calculateRankings(eq(LeaderboardPeriod.MONTHLY), eq(yesterday));
    }
}








