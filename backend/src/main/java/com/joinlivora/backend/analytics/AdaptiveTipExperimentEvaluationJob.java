package com.joinlivora.backend.analytics;

import com.joinlivora.backend.payout.CreatorEarningRepository;
import com.joinlivora.backend.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdaptiveTipExperimentEvaluationJob {

    private final AdaptiveTipExperimentRepository experimentRepository;
    private final CreatorEarningRepository creatorEarningRepository;
    private final AdaptiveTipEngineService adaptiveTipEngineService;

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void evaluateExperiments() {
        log.info("Starting Adaptive Tip Experiment Evaluation Job");
        
        List<AdaptiveTipExperiment> allPending = experimentRepository.findByEvaluatedAtIsNull().stream()
                .filter(e -> e.getCreatedAt().isBefore(LocalDateTime.now().minusHours(24)))
                .toList();

        for (AdaptiveTipExperiment experiment : allPending) {
            evaluate(experiment);
        }
        
        log.info("Finished evaluation of {} experiments", allPending.size());
    }

    private void evaluate(AdaptiveTipExperiment experiment) {
        User creator = experiment.getCreator();
        Instant experimentStart = experiment.getCreatedAt().toInstant(ZoneOffset.UTC);
        Instant experimentEnd24h = experimentStart.plus(24, java.time.temporal.ChronoUnit.HOURS);
        Instant baselineStart = experimentStart.minus(24, java.time.temporal.ChronoUnit.HOURS);

        BigDecimal baselineRevenue = creatorEarningRepository.sumNetEarningsByCreatorAndPeriod(creator, baselineStart, experimentStart);
        BigDecimal evaluationRevenue = creatorEarningRepository.sumNetEarningsByCreatorAndPeriod(creator, experimentStart, experimentEnd24h);

        baselineRevenue = baselineRevenue != null ? baselineRevenue : BigDecimal.ZERO;
        evaluationRevenue = evaluationRevenue != null ? evaluationRevenue : BigDecimal.ZERO;

        double lift = evaluationRevenue.doubleValue() - baselineRevenue.doubleValue();

        // Calculate new risk score after 24h
        int newRiskScore = adaptiveTipEngineService.calculateCurrentMetrics(creator).getRiskScore();

        experiment.setBaselineRevenue(baselineRevenue.doubleValue());
        experiment.setRevenueAfter24h(evaluationRevenue.doubleValue());
        experiment.setRevenueLift(lift);
        experiment.setRiskDelta(experiment.getSuggestedFloor() - experiment.getPreviousFloor());
        experiment.setNewRiskScore(newRiskScore);
        experiment.setSuccess(lift > 0);
        experiment.setEvaluatedAt(LocalDateTime.now());

        experimentRepository.save(experiment);
        
        log.info("Evaluated experiment {} for creator {}: Success={}", experiment.getId(), creator.getEmail(), experiment.getSuccess());
    }
}
