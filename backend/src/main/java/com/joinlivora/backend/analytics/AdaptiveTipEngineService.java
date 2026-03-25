package com.joinlivora.backend.analytics;

import com.joinlivora.backend.analytics.dto.AdaptiveTipEngineResponseDTO;
import com.joinlivora.backend.analytics.dto.CreatorAdaptivePerformanceDTO;
import com.joinlivora.backend.analytics.adaptive.WhaleRiskModel;
import com.joinlivora.backend.payout.CreatorEarning;
import com.joinlivora.backend.payout.CreatorEarningRepository;
import com.joinlivora.backend.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdaptiveTipEngineService {

    private final CreatorEarningRepository creatorEarningRepository;
    private final AdaptiveTipExperimentRepository experimentRepository;
    private final WhaleRiskModel whaleRiskModel;
    private final CreatorAdaptivePerformanceService performanceService;
    private static final int BASE_COOLDOWN_MINUTES = 30;

    public AdaptiveTipEngineResponseDTO evaluate(User creator) {
        AdaptiveTipEngineResponseDTO metrics = calculateCurrentMetrics(creator);
        
        if (metrics == null || "NOT_READY".equals(metrics.getStatus()) || "COOLDOWN".equals(metrics.getStatus())) {
            return metrics != null ? metrics : buildNotReadyResponse("No metrics available.");
        }

        // 7. Track Experiment if READY/CAUTION
        int bucket = Math.abs(creator.getId().hashCode()) % 3;
        AdaptiveTipExperiment experiment = AdaptiveTipExperiment.builder()
                .creator(creator)
                .suggestedFloor(metrics.getSuggestedFloor())
                .previousFloor(metrics.getAvgPerSale())
                .riskScore(metrics.getRiskScore())
                .momentum(metrics.getMomentum())
                .confidenceTier(metrics.getConfidenceTier())
                .experimentGroup("BUCKET_" + bucket)
                .build();
        experimentRepository.save(experiment);

        return metrics;
    }

    public AdaptiveTipEngineResponseDTO calculateCurrentMetrics(User creator) {
        List<CreatorEarning> transactions = creatorEarningRepository.findAllByCreatorOrderByCreatedAtDesc(creator);
        
        if (transactions.isEmpty()) {
            return buildNotReadyResponse("No transaction history available.");
        }

        double volatility = calculateRevenueVolatility(creator);

        // 1. Calculate Metrics
        double totalRevenue = transactions.stream()
                .mapToDouble(tx -> tx.getNetAmount().doubleValue())
                .sum();
        
        double avgPerSale = totalRevenue / transactions.size();
        
        Map<String, Double> supporterTotals = new HashMap<>();
        for (CreatorEarning tx : transactions) {
            String name = tx.getUser() != null ? tx.getUser().getEmail() : "Anonymous";
            supporterTotals.merge(name, tx.getNetAmount().doubleValue(), Double::sum);
        }
        
        int uniqueSpenders = supporterTotals.size();
        double top1Share = supporterTotals.values().stream()
                .max(Double::compare)
                .map(max -> max / totalRevenue)
                .orElse(0.0);

        // 2. Momentum
        double last7Revenue = calculateRevenueForPeriod(transactions, 7, 0);
        double previous7Revenue = calculateRevenueForPeriod(transactions, 14, 7);
        double momentum = previous7Revenue > 0 ? (last7Revenue - previous7Revenue) / previous7Revenue : 0;

        // 3. Confidence Tier
        String confidenceTier = calculateConfidenceTier(uniqueSpenders);

        // 4. Risk Score
        int riskScore = calculateRiskScore(uniqueSpenders, top1Share, confidenceTier, avgPerSale, momentum);

        // 5. Status & Reason
        String status = determineStatus(riskScore);
        String reason = generateReason(status);

        // 6. Dynamic Cooldown
        CreatorAdaptivePerformanceDTO performance = performanceService.getCreatorMetrics(creator);
        double whaleRiskScore = whaleRiskModel.calculateRisk(creator);
        
        int dynamicCooldownMinutes = calculateDynamicCooldown(
            whaleRiskScore,
            performance.getEngineConfidence(),
            performance.getSuccessRate(),
            volatility
        );

        Long cooldownRemainingSeconds = experimentRepository.findAllByCreatorOrderByCreatedAtDesc(creator).stream()
                .findFirst()
                .map(lastExperiment -> {
                    long elapsedSeconds = java.time.Duration.between(lastExperiment.getCreatedAt(), java.time.LocalDateTime.now()).getSeconds();
                    long cooldownSeconds = dynamicCooldownMinutes * 60L;
                    return Math.max(0L, cooldownSeconds - elapsedSeconds);
                })
                .orElse(0L);

        if (cooldownRemainingSeconds > 0) {
            status = "COOLDOWN";
        }

        // 7. Suggested Floor (Experiment-based)
        int bucket = Math.abs(creator.getId().hashCode()) % 3;
        double multiplier = switch (bucket) {
            case 0 -> 1.15;
            case 1 -> 1.25;
            case 2 -> 1.35;
            default -> 1.15;
        };

        // Aggressiveness logic based on volatility
        if (volatility > 0.9) {
            multiplier -= 0.15;
        } else if (volatility > 0.6) {
            multiplier -= 0.05;
        }

        double suggestedFloor = calculateSuggestedFloor(avgPerSale, multiplier, volatility);

        return AdaptiveTipEngineResponseDTO.builder()
                .status(status)
                .riskScore(riskScore)
                .avgPerSale(avgPerSale)
                .suggestedFloor(suggestedFloor)
                .momentum(momentum)
                .top1Share(top1Share)
                .confidenceTier(confidenceTier)
                .cooldownRemainingSeconds(cooldownRemainingSeconds)
                .reason(reason)
                .build();
    }

    private double calculateRevenueForPeriod(List<CreatorEarning> transactions, int daysLimit, int startFrom) {
        Instant now = Instant.now();
        Instant start = now.minus(daysLimit, ChronoUnit.DAYS);
        Instant end = now.minus(startFrom, ChronoUnit.DAYS);
        
        return transactions.stream()
                .filter(tx -> tx.getCreatedAt().isAfter(start) && tx.getCreatedAt().isBefore(end))
                .mapToDouble(tx -> tx.getNetAmount().doubleValue())
                .sum();
    }

    private String calculateConfidenceTier(int uniqueSpenders) {
        if (uniqueSpenders > 7) return "HIGH";
        if (uniqueSpenders >= 3) return "MEDIUM";
        return "LOW";
    }

    private int calculateRiskScore(int uniqueSpenders, double top1Share, String confidenceTier, double avgPerSale, double momentum) {
        int score = 0;
        if (uniqueSpenders < 3) score += 2;
        if (top1Share > 0.6) score += 2;
        if ("LOW".equals(confidenceTier)) score += 1;
        if (avgPerSale < 40) score += 1;

        if (momentum > 0.25) score -= 1;
        if (momentum < -0.20) score += 1;

        return Math.max(0, score);
    }

    private String determineStatus(int riskScore) {
        if (riskScore >= 4) return "NOT_READY";
        if (riskScore >= 2) return "CAUTION";
        return "READY";
    }

    private String generateReason(String status) {
        if ("NOT_READY".equals(status)) return "Risk score critical. Optimization paused.";
        if ("CAUTION".equals(status)) return "Risk score elevated. Proceed with caution.";
        return "";
    }

    private double calculateSuggestedFloor(double avgPerSale, double multiplier, double revenueVolatility) {
        double volatilityAdjustment = 1 - Math.min(revenueVolatility, 0.3);
        return Math.round(avgPerSale * multiplier * volatilityAdjustment);
    }

    private int calculateDynamicCooldown(
            double whaleRiskScore,
            double engineConfidence,
            double successRate,
            double volatility
    ) {
        int cooldown = 30;

        if (whaleRiskScore > 0.75)
            cooldown += 15;

        if (engineConfidence < 0.50)
            cooldown += 10;

        if (successRate > 0.70)
            cooldown -= 10;

        if (volatility > 0.9)
            cooldown += 10;

        return Math.max(15, Math.min(60, cooldown));
    }

    private double calculateRevenueVolatility(User creator) {
        Instant sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS);
        List<CreatorEarning> transactions = creatorEarningRepository.findAllByCreatorOrderByCreatedAtDesc(creator);
        
        if (transactions.isEmpty()) return 0;

        Map<LocalDate, Double> dailyRevenue = new HashMap<>();
        for (int i = 0; i < 7; i++) {
            dailyRevenue.put(LocalDate.now(ZoneId.of("UTC")).minusDays(i), 0.0);
        }

        for (CreatorEarning tx : transactions) {
            if (tx.getCreatedAt().isAfter(sevenDaysAgo)) {
                LocalDate date = LocalDate.ofInstant(tx.getCreatedAt(), ZoneId.of("UTC"));
                dailyRevenue.merge(date, tx.getNetAmount().doubleValue(), Double::sum);
            }
        }

        Collection<Double> values = dailyRevenue.values();
        double average = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        
        if (average == 0) return 0;

        double sumSq = values.stream()
                .mapToDouble(v -> Math.pow(v - average, 2))
                .sum();
        double stdDev = Math.sqrt(sumSq / 7.0);

        return stdDev / average;
    }

    private AdaptiveTipEngineResponseDTO buildNotReadyResponse(String reason) {
        return AdaptiveTipEngineResponseDTO.builder()
                .status("NOT_READY")
                .reason(reason)
                .build();
    }
}
