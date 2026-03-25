package com.joinlivora.backend.monetization;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.IsoFields;

/**
 * Scheduled job to cleanup Redis leaderboard data from previous weeks.
 * Runs every Monday at 00:00 UTC.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WeeklyLeaderboardResetJob {

    private final WeeklyTipService weeklyTipService;

    @Scheduled(cron = "0 0 0 * * MON", zone = "UTC")
    public void resetPreviousWeekLeaderboard() {
        // Since this runs on Monday, minusDays(1) takes us to Sunday of the previous week
        LocalDate previousWeekDate = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        int week = previousWeekDate.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        int year = previousWeekDate.get(IsoFields.WEEK_BASED_YEAR);

        log.info("Starting WeeklyLeaderboardResetJob: clearing Redis keys for Year {}, Week {}", year, week);
        try {
            weeklyTipService.clearLeaderboardForWeek(year, week);
            log.info("Successfully completed WeeklyLeaderboardResetJob for Week {}, Year {}", week, year);
        } catch (Exception e) {
            log.error("Failed to complete WeeklyLeaderboardResetJob for Year {}, Week {}", year, week, e);
        }
    }
}
