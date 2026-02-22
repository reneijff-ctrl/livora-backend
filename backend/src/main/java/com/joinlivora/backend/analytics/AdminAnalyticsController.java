package com.joinlivora.backend.analytics;

import com.joinlivora.backend.analytics.dto.CreatorAnalyticsResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/analytics")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminAnalyticsController {

    private final PlatformAnalyticsRepository platformAnalyticsRepository;
    private final CreatorAnalyticsService creatorAnalyticsService;
    private final ExperimentAnalyticsRepository experimentAnalyticsRepository;

    @GetMapping("/creators/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<List<CreatorAnalyticsResponseDTO>> getCreatorAnalytics(
            @PathVariable UUID id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok(creatorAnalyticsService.getAnalyticsByProfileId(id, from, to));
    }

    @GetMapping("/overview")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> getOverview() {
        LocalDate thirtyDaysAgo = LocalDate.now(ZoneOffset.UTC).minusDays(30);
        
        PlatformAnalytics latest = platformAnalyticsRepository.findFirstByOrderByDateDesc().orElse(null);
        
        BigDecimal revenue = platformAnalyticsRepository.sumRevenueSince(thirtyDaysAgo);
        Long visits = platformAnalyticsRepository.sumUniqueVisitsSince(thirtyDaysAgo);
        Long registrations = platformAnalyticsRepository.sumRegistrationsSince(thirtyDaysAgo);

        return ResponseEntity.ok(Map.of(
            "totalActiveSubscriptions", latest != null ? latest.getActiveSubscriptions() : 0,
            "revenue30Days", revenue != null ? revenue : BigDecimal.ZERO,
            "uniqueVisits30Days", visits != null ? visits : 0,
            "registrations30Days", registrations != null ? registrations : 0
        ));
    }

    @GetMapping("/experiments")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> getExperiments() {
        return ResponseEntity.ok(Map.of());
    }

    @GetMapping("/experiments/results")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> getExperimentResults(@RequestParam String experimentKey) {
        List<ExperimentAnalytics> results = experimentAnalyticsRepository.findAllByExperimentKey(experimentKey);
        return ResponseEntity.ok(results.stream().collect(Collectors.toMap(
            ExperimentAnalytics::getVariant,
            ExperimentAnalytics::getCount
        )));
    }

    @GetMapping("/revenue")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> getRevenue() {
        LocalDate thirtyDaysAgo = LocalDate.now(ZoneOffset.UTC).minusDays(30);
        
        BigDecimal totalRevenue = platformAnalyticsRepository.sumRevenueSince(thirtyDaysAgo);
        if (totalRevenue == null) totalRevenue = BigDecimal.ZERO;
        
        PlatformAnalytics latest = platformAnalyticsRepository.findFirstByOrderByDateDesc().orElse(null);
        long activeSubs = latest != null ? latest.getActiveSubscriptions() : 0;
        
        BigDecimal arpu = activeSubs > 0 
            ? totalRevenue.divide(BigDecimal.valueOf(activeSubs), 2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        return ResponseEntity.ok(Map.of(
            "mrr", totalRevenue,
            "arpu", arpu
        ));
    }

    @GetMapping("/funnels")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> getFunnels() {
        LocalDate thirtyDaysAgo = LocalDate.now(ZoneOffset.UTC).minusDays(30);
        
        Long visits = platformAnalyticsRepository.sumUniqueVisitsSince(thirtyDaysAgo);
        Long registrations = platformAnalyticsRepository.sumRegistrationsSince(thirtyDaysAgo);
        Long paid = platformAnalyticsRepository.sumNewSubscriptionsSince(thirtyDaysAgo);

        long v = visits != null ? visits : 0;
        long r = registrations != null ? registrations : 0;
        long p = paid != null ? paid : 0;

        return ResponseEntity.ok(Map.of(
            "visits", v,
            "registrations", r,
            "paid", p,
            "visitToRegRate", v > 0 ? (double) r / v : 0,
            "regToPaidRate", r > 0 ? (double) p / r : 0
        ));
    }

    @GetMapping("/churn")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> getChurn() {
        LocalDate thirtyDaysAgo = LocalDate.now(ZoneOffset.UTC).minusDays(30);
        
        PlatformAnalytics latest = platformAnalyticsRepository.findFirstByOrderByDateDesc().orElse(null);
        long activeAtEnd = latest != null ? latest.getActiveSubscriptions() : 0;
        Long churned = platformAnalyticsRepository.sumChurnedSubscriptionsSince(thirtyDaysAgo);
        
        long c = churned != null ? churned : 0;
        
        return ResponseEntity.ok(Map.of(
            "churned30Days", c,
            "churnRate", activeAtEnd + c > 0 ? (double) c / (activeAtEnd + c) : 0
        ));
    }
}
