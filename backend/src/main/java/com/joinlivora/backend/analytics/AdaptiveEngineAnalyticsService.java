package com.joinlivora.backend.analytics;

import com.joinlivora.backend.analytics.dto.AdaptiveEngineMetricsDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service to calculate global analytics for the Adaptive Tip Engine experiments.
 */
@Service
@RequiredArgsConstructor
public class AdaptiveEngineAnalyticsService {

    private final AdaptiveTipExperimentRepository experimentRepository;

    /**
     * Calculates global metrics across all experiments using Stream aggregation.
     * Define "successful" as revenueLift > 0.
     * 
     * @return DTO containing aggregated metrics
     */
    public AdaptiveEngineMetricsDTO getGlobalAdaptiveMetrics() {
        List<AdaptiveTipExperiment> allExperiments = experimentRepository.findAll();
        
        long totalExperiments = allExperiments.size();
        
        long activeExperiments = allExperiments.stream()
                .filter(e -> e.getEvaluatedAt() == null)
                .count();

        List<AdaptiveTipExperiment> evaluated = allExperiments.stream()
                .filter(e -> e.getEvaluatedAt() != null)
                .toList();

        long successfulExperiments = evaluated.stream()
                .filter(e -> e.getRevenueLift() != null && e.getRevenueLift() > 0)
                .count();

        double successRate = evaluated.isEmpty() ? 0 : (double) successfulExperiments / evaluated.size();
        
        double averageRevenueLift = evaluated.stream()
                .mapToDouble(e -> e.getRevenueLift() != null ? e.getRevenueLift() : 0.0)
                .average()
                .orElse(0.0);

        double averageRiskDelta = evaluated.stream()
                .mapToDouble(e -> e.getRiskDelta() != null ? e.getRiskDelta() : 0.0)
                .average()
                .orElse(0.0);

        return AdaptiveEngineMetricsDTO.builder()
                .totalExperiments(totalExperiments)
                .successfulExperiments(successfulExperiments)
                .successRate(successRate)
                .averageRevenueLift(averageRevenueLift)
                .averageRiskDelta(averageRiskDelta)
                .activeExperiments(activeExperiments)
                .build();
    }
}
