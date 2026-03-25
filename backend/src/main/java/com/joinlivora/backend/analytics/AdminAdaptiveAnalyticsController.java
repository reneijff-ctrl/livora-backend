package com.joinlivora.backend.analytics;

import com.joinlivora.backend.analytics.dto.AdaptiveEngineMetricsDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only controller for Adaptive Tip Engine global metrics.
 */
@RestController
@RequestMapping("/api/admin/analytics/adaptive-engine")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Slf4j
public class AdminAdaptiveAnalyticsController {

    private final AdaptiveEngineAnalyticsService adaptiveEngineAnalyticsService;

    /**
     * Get global metrics across all experiments.
     * Restricted to users with the ADMIN role.
     * 
     * @return Global metrics for the adaptive engine
     */
    @GetMapping
    public ResponseEntity<AdaptiveEngineMetricsDTO> getMetrics(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails != null) {
            log.info("AUDIT: Admin {} accessed global adaptive engine metrics", userDetails.getUsername());
        } else {
            log.info("AUDIT: Anonymous/System accessed global adaptive engine metrics");
        }
        return ResponseEntity.ok(adaptiveEngineAnalyticsService.getGlobalAdaptiveMetrics());
    }
}
