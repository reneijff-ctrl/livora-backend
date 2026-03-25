package com.joinlivora.backend.analytics;

import com.joinlivora.backend.analytics.dto.LeaderboardResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/leaderboards")
@RequiredArgsConstructor
public class LeaderboardController {

    private final LeaderboardCalculationService leaderboardCalculationService;

    @GetMapping
    public ResponseEntity<List<LeaderboardResponseDto>> getLeaderboard(
            @RequestParam LeaderboardPeriod period,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "20") int limit
    ) {
        // Enforce maximum limit to prevent abuse
        int cappedLimit = Math.min(limit, 100);
        return ResponseEntity.ok(leaderboardCalculationService.getLeaderboard(period, category, cappedLimit));
    }
}
