package com.joinlivora.backend.analytics;

import com.joinlivora.backend.analytics.dto.CreatorAdaptivePerformanceDTO;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
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
 * Controller for creators to access their Adaptive Tip Engine performance metrics.
 * Restricted to users with the CREATOR role.
 */
@RestController
@RequestMapping("/api/creator/analytics/adaptive-performance")
@RequiredArgsConstructor
@Slf4j
public class CreatorAdaptivePerformanceController {

    private final CreatorAdaptivePerformanceService service;
    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<CreatorAdaptivePerformanceDTO> getPerformance(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = userService.getByEmail(userDetails.getUsername());
        log.info("AUDIT: Creator {} accessed adaptive performance analytics", userDetails.getUsername());
        return ResponseEntity.ok(service.getCreatorMetrics(user));
    }
}
