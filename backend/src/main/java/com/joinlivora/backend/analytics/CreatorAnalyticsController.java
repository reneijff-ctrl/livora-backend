package com.joinlivora.backend.analytics;

import com.joinlivora.backend.analytics.dto.AdaptiveTipEngineResponseDTO;
import com.joinlivora.backend.analytics.dto.CreatorAnalyticsResponseDTO;
import com.joinlivora.backend.analytics.dto.CreatorEarningsBreakdownDTO;
import com.joinlivora.backend.analytics.dto.TopContentDTO;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/creator/analytics")
@RequiredArgsConstructor
@Slf4j
public class CreatorAnalyticsController {

    private final CreatorAnalyticsService creatorAnalyticsService;
    private final AdaptiveTipEngineService adaptiveTipEngineService;
    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasAnyRole('CREATOR', 'ADMIN')")
    public ResponseEntity<List<CreatorAnalyticsResponseDTO>> getAnalytics(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        LocalDate fromDate = from != null ? LocalDate.parse(from) : null;
        LocalDate toDate = to != null ? LocalDate.parse(to) : null;

        if (fromDate == null && toDate == null) {
            toDate = LocalDate.now();
            fromDate = toDate.minusDays(30);
        }

        User user = userService.getByEmail(userDetails.getUsername());
        log.info("AUDIT: User {} accessed analytics from {} to {}", userDetails.getUsername(), fromDate, toDate);
        return ResponseEntity.ok(creatorAnalyticsService.getAnalytics(user, fromDate, toDate));
    }

    @GetMapping("/earnings-breakdown")
    @PreAuthorize("hasAnyRole('CREATOR', 'ADMIN')")
    public ResponseEntity<CreatorEarningsBreakdownDTO> getEarningsBreakdown(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = userService.getByEmail(userDetails.getUsername());
        log.info("AUDIT: User {} accessed earnings breakdown from {} to {}", userDetails.getUsername(), from, to);
        return ResponseEntity.ok(creatorAnalyticsService.getEarningsBreakdown(user, from, to));
    }

    @GetMapping("/top-content")
    @PreAuthorize("hasAnyRole('CREATOR', 'ADMIN')")
    public ResponseEntity<TopContentDTO> getTopContent(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = userService.getByEmail(userDetails.getUsername());
        log.info("AUDIT: User {} accessed top content analytics", userDetails.getUsername());
        return ResponseEntity.ok(creatorAnalyticsService.getTopContent(user));
    }

    @GetMapping("/adaptive-tip")
    @PreAuthorize("hasAnyRole('CREATOR', 'ADMIN')")
    public ResponseEntity<AdaptiveTipEngineResponseDTO> getAdaptiveTip(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User user = userService.getByEmail(userDetails.getUsername());
        log.info("AUDIT: User {} accessed adaptive tip engine", userDetails.getUsername());
        return ResponseEntity.ok(adaptiveTipEngineService.evaluate(user));
    }
}
