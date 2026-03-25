package com.joinlivora.backend.analytics;

import com.joinlivora.backend.analytics.dto.CreatorAdaptivePerformanceDTO;
import com.joinlivora.backend.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CreatorAdaptivePerformanceService {

    private final AdaptiveTipExperimentRepository experimentRepository;

    public CreatorAdaptivePerformanceDTO getCreatorMetrics(User creator) {
        List<AdaptiveTipExperiment> experiments = experimentRepository.findAllByCreatorOrderByCreatedAtDesc(creator);
        
        int experimentsRun = experiments.size();
        
        if (experimentsRun < 3) {
            return CreatorAdaptivePerformanceDTO.builder()
                    .experimentsRun(experimentsRun)
                    .successRate(0.0)
                    .averageRevenueLift(0.0)
                    .averageRiskReduction(0.0)
                    .momentum("STABLE")
                    .engineConfidence(0.0)
                    .build();
        }

        List<AdaptiveTipExperiment> evaluated = experiments.stream()
                .filter(e -> e.getEvaluatedAt() != null)
                .toList();

        long successfulCount = evaluated.stream()
                .filter(e -> e.getRevenueLift() != null && e.getRevenueLift() > 0)
                .count();

        double successRate = (double) successfulCount / experimentsRun;
        
        double averageRevenueLift = evaluated.stream()
                .mapToDouble(e -> e.getRevenueLift() != null ? e.getRevenueLift() : 0.0)
                .average()
                .orElse(0.0);

        double averageRiskReduction = evaluated.stream()
                .filter(e -> e.getNewRiskScore() != null)
                .mapToDouble(e -> (double) e.getRiskScore() - e.getNewRiskScore())
                .average()
                .orElse(0.0);

        // Momentum calculation
        String momentum = calculateMomentum(experiments);

        // Engine Confidence
        double confidence = successRate * Math.log10(experimentsRun + 1);
        double engineConfidence = Math.min(1.0, confidence);

        return CreatorAdaptivePerformanceDTO.builder()
                .experimentsRun(experimentsRun)
                .successRate(successRate)
                .averageRevenueLift(averageRevenueLift)
                .averageRiskReduction(averageRiskReduction)
                .momentum(momentum)
                .engineConfidence(engineConfidence)
                .build();
    }

    private String calculateMomentum(List<AdaptiveTipExperiment> experiments) {
        List<AdaptiveTipExperiment> evaluated = experiments.stream()
                .filter(e -> e.getEvaluatedAt() != null && e.getRevenueLift() != null)
                .toList();
        
        if (evaluated.size() < 6) {
            return "STABLE";
        }

        double recentAvg = evaluated.subList(0, 3).stream()
                .mapToDouble(AdaptiveTipExperiment::getRevenueLift)
                .average()
                .orElse(0.0);
        
        double previousAvg = evaluated.subList(3, 6).stream()
                .mapToDouble(AdaptiveTipExperiment::getRevenueLift)
                .average()
                .orElse(0.0);
        
        if (recentAvg > previousAvg) return "POSITIVE";
        if (recentAvg < previousAvg) return "NEGATIVE";
        return "STABLE";
    }
}
