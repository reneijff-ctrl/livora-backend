package com.joinlivora.backend.analytics.adaptive;

import com.joinlivora.backend.analytics.CreatorAdaptivePerformanceService;
import com.joinlivora.backend.analytics.adaptive.dto.TipRecommendationResponse;
import com.joinlivora.backend.analytics.dto.CreatorAdaptivePerformanceDTO;
import com.joinlivora.backend.payout.CreatorEarning;
import com.joinlivora.backend.payout.CreatorEarningRepository;
import com.joinlivora.backend.user.User;
import lombok.RequiredArgsConstructor;
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

import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DynamicTipRecommendationService {

    private final CreatorAdaptivePerformanceService performanceService;
    private final WhaleRiskModel whaleRiskModel;
    private final CreatorEarningRepository creatorEarningRepository;

    public TipRecommendationResponse generateForCreator(User creator) {
        // 1. Fetch performance
        CreatorAdaptivePerformanceDTO performance = performanceService.getCreatorMetrics(creator);

        // Placeholder data for current implementation phase
        BigDecimal medianTip = BigDecimal.valueOf(5.00);
        double whaleRiskScore = whaleRiskModel.calculateRisk(creator);
        double tipFrequencyPerViewer = 3.5;
        int uniqueSupporters = 25;

        BigDecimal baseRecommendation = generateRecommendedMinimumTip(medianTip, whaleRiskScore, tipFrequencyPerViewer);

        // 2. Compute aggressivenessFactor
        double factor = 1.0;

        if (performance.getEngineConfidence() > 0.75) {
            factor += 0.15;
        }

        if ("POSITIVE".equals(performance.getMomentum())) {
            factor += 0.10;
        }

        if ("NEGATIVE".equals(performance.getMomentum())) {
            factor -= 0.10;
        }

        double volatility = calculateRevenueVolatility(creator);
        if (volatility > 0.9) {
            factor -= 0.15;
        } else if (volatility > 0.6) {
            factor -= 0.05;
        }

        if (whaleRiskScore > 0.80) {
            factor = 0.85;
        } else if (whaleRiskScore > 0.65) {
            factor = Math.min(factor, 0.95);
        }

        // Clamp factor between 0.85 and 1.30
        factor = Math.max(0.85, Math.min(1.30, factor));

        // 3. recommendedMinimumTip = baseRecommendation * factor
        BigDecimal recommendedFinal = baseRecommendation.multiply(BigDecimal.valueOf(factor));

        // Round to nearest 0.50 increment
        double finalValue = recommendedFinal.doubleValue();
        double finalRoundedValue = Math.round(finalValue * 2.0) / 2.0;
        BigDecimal finalTip = BigDecimal.valueOf(finalRoundedValue).setScale(2, RoundingMode.HALF_UP);

        String riskLevel;
        String explanation;
        if (whaleRiskScore > 0.7) {
            riskLevel = "CRITICAL";
            explanation = "High concentration of revenue from few supporters. Increasing floor is risky.";
        } else if (whaleRiskScore > 0.4) {
            riskLevel = "ELEVATED";
            explanation = "Moderate supporter concentration. Proceed with balanced floor adjustments.";
        } else {
            riskLevel = "SAFE";
            explanation = "Healthy supporter distribution. Safe to optimize for growth.";
        }

        double confidenceScore = uniqueSupporters > 20 ? 0.85 : 0.65;

        return new TipRecommendationResponse(finalTip, confidenceScore, riskLevel, explanation);
    }

    public BigDecimal generateRecommendedMinimumTip(
            BigDecimal medianTip,
            double whaleRiskScore,
            double tipFrequencyPerViewer
    ) {
        double multiplier = 1.0;

        if (whaleRiskScore > 0.7) {
            multiplier = 0.9;
        } else if (whaleRiskScore >= 0.4) {
            multiplier = 1.0;
        } else {
            multiplier = 1.15;
        }

        if (tipFrequencyPerViewer > 3) {
            multiplier += 0.05;
        }

        BigDecimal recommendedRaw = medianTip.multiply(BigDecimal.valueOf(multiplier));

        // Round to nearest 0.50
        // Formula: (round(value * 2) / 2)
        double value = recommendedRaw.doubleValue();
        double roundedValue = Math.round(value * 2.0) / 2.0;

        return BigDecimal.valueOf(roundedValue).setScale(2, RoundingMode.HALF_UP);
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
}
