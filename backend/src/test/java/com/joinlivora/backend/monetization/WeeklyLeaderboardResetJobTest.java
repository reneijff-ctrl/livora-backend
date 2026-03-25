package com.joinlivora.backend.monetization;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.IsoFields;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WeeklyLeaderboardResetJobTest {

    @Mock
    private WeeklyTipService weeklyTipService;

    @InjectMocks
    private WeeklyLeaderboardResetJob job;

    @Test
    void resetPreviousWeekLeaderboard_ShouldCallServiceWithCorrectDate() {
        // We can't easily mock LocalDate.now() without extra libraries, 
        // but we can verify it calls the service.
        // Since it uses now().minusDays(1), we can estimate the week/year.
        
        LocalDate expectedDate = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        int expectedWeek = expectedDate.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        int expectedYear = expectedDate.get(IsoFields.WEEK_BASED_YEAR);

        job.resetPreviousWeekLeaderboard();

        verify(weeklyTipService).clearLeaderboardForWeek(expectedYear, expectedWeek);
    }

    @Test
    void resetPreviousWeekLeaderboard_WhenServiceThrows_ShouldHandleException() {
        doThrow(new RuntimeException("Redis error"))
                .when(weeklyTipService).clearLeaderboardForWeek(anyInt(), anyInt());

        // Should not throw
        job.resetPreviousWeekLeaderboard();

        verify(weeklyTipService).clearLeaderboardForWeek(anyInt(), anyInt());
    }
}








