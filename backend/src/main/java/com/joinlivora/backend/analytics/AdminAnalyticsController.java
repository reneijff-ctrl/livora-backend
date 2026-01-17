package com.joinlivora.backend.analytics;

import com.joinlivora.backend.payment.PaymentRepository;
import com.joinlivora.backend.payment.UserSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/analytics")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminAnalyticsController {

    private final AnalyticsEventRepository analyticsEventRepository;
    private final PaymentRepository paymentRepository;
    private final UserSubscriptionRepository subscriptionRepository;

    @GetMapping("/overview")
    public ResponseEntity<Map<String, Object>> getOverview() {
        Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);
        
        return ResponseEntity.ok(Map.of(
            "totalActiveSubscriptions", subscriptionRepository.countActiveSubscriptions(),
            "revenue30Days", paymentRepository.calculateRevenue(thirtyDaysAgo) != null ? paymentRepository.calculateRevenue(thirtyDaysAgo) : java.math.BigDecimal.ZERO,
            "uniqueVisits30Days", analyticsEventRepository.countUniqueVisits(thirtyDaysAgo),
            "registrations30Days", analyticsEventRepository.countRegistrations(thirtyDaysAgo)
        ));
    }

    @GetMapping("/experiments")
    public ResponseEntity<Map<String, Object>> getExperiments() {
        return ResponseEntity.ok(Map.of());
    }

    @GetMapping("/experiments/results")
    public ResponseEntity<Map<String, Object>> getExperimentResults(@org.springframework.web.bind.annotation.RequestParam String experimentKey) {
        java.util.List<Object[]> data = analyticsEventRepository.countByExperimentKeyAndVariant(experimentKey);
        return ResponseEntity.ok(data.stream().collect(java.util.stream.Collectors.toMap(
            row -> (String) row[0],
            row -> row[1]
        )));
    }

    @GetMapping("/revenue")
    public ResponseEntity<Map<String, Object>> getRevenue() {
        Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);
        
        BigDecimal totalRevenue = paymentRepository.calculateRevenue(thirtyDaysAgo);
        if (totalRevenue == null) totalRevenue = BigDecimal.ZERO;
        
        long activeSubs = subscriptionRepository.countActiveSubscriptions();
        
        BigDecimal arpu = activeSubs > 0 
            ? totalRevenue.divide(BigDecimal.valueOf(activeSubs), 2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        return ResponseEntity.ok(Map.of(
            "mrr", totalRevenue,
            "arpu", arpu
        ));
    }

    @GetMapping("/funnels")
    public ResponseEntity<Map<String, Object>> getFunnels() {
        Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);
        
        long visits = analyticsEventRepository.countUniqueVisits(thirtyDaysAgo);
        long registrations = analyticsEventRepository.countRegistrations(thirtyDaysAgo);
        long paid = analyticsEventRepository.countNewSubscriptions(thirtyDaysAgo);

        return ResponseEntity.ok(Map.of(
            "visits", visits,
            "registrations", registrations,
            "paid", paid,
            "visitToRegRate", visits > 0 ? (double) registrations / visits : 0,
            "regToPaidRate", registrations > 0 ? (double) paid / registrations : 0
        ));
    }

    @GetMapping("/churn")
    public ResponseEntity<Map<String, Object>> getChurn() {
        Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);
        
        long activeAtStart = subscriptionRepository.countActiveSubscriptions(); // Simplified
        long churned = subscriptionRepository.countCanceledSubscriptions(thirtyDaysAgo);
        
        return ResponseEntity.ok(Map.of(
            "churned30Days", churned,
            "churnRate", activeAtStart > 0 ? (double) churned / activeAtStart : 0
        ));
    }
}
