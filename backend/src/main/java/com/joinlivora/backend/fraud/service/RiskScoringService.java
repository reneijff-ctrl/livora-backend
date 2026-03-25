package com.joinlivora.backend.fraud.service;

import com.joinlivora.backend.fraud.model.RiskAction;
import com.joinlivora.backend.fraud.model.RiskFactor;
import com.joinlivora.backend.fraud.model.RiskScore;
import com.joinlivora.backend.fraud.repository.RiskScoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service responsible for calculating and persisting creator risk scores
 * based on various risk factors.
 */
@Service("riskScoringService")
@RequiredArgsConstructor
@Slf4j
public class RiskScoringService {

    private final RiskScoreRepository riskScoreRepository;

    private static final Map<RiskFactor, Integer> WEIGHTS = new EnumMap<>(RiskFactor.class);

    static {
        WEIGHTS.put(RiskFactor.CHARGEBACK_RATE, 40);
        WEIGHTS.put(RiskFactor.CHARGEBACK_COUNT, 25);
        WEIGHTS.put(RiskFactor.HIGH_TIP_FREQUENCY, 15);
        WEIGHTS.put(RiskFactor.RAPID_PAYOUT_REQUESTS, 10);
        WEIGHTS.put(RiskFactor.MULTIPLE_ACCOUNTS, 10);
        WEIGHTS.put(RiskFactor.MANUAL_FLAG, 50);
    }

    /**
     * Calculates the total risk score based on provided factors and their weights.
     *
     * @param factors A map of risk factors and their severity/occurrence (multiplier)
     * @return The total risk score, capped between 0 and 100
     */
    public int calculateTotalScore(Map<RiskFactor, Integer> factors) {
        if (factors == null || factors.isEmpty()) {
            return 0;
        }

        int totalScore = factors.entrySet().stream()
                .mapToInt(entry -> WEIGHTS.getOrDefault(entry.getKey(), 0) * entry.getValue())
                .sum();

        return Math.min(100, Math.max(0, totalScore));
    }

    /**
     * Persists a RiskScore for a given creator.
     *
     * @param userId  The UUID of the creator
     * @param factors The factors that contributed to the score
     * @return The saved RiskScore entity
     */
    @Transactional
    public RiskScore calculateAndPersist(UUID userId, Map<RiskFactor, Integer> factors) {
        int score = calculateTotalScore(factors);
        
        String breakdown = factors.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .map(e -> e.getKey().name() + "(" + e.getValue() + ")")
                .collect(Collectors.joining(", "));

        RiskScore riskScore = RiskScore.builder()
                .userId(userId)
                .score(score)
                .lastEvaluatedAt(Instant.now())
                .breakdown(breakdown)
                .build();

        log.info("Persisting risk score for creator {}: {} (Factors: {})", userId, score, breakdown);
        return riskScoreRepository.save(riskScore);
    }

    /**
     * Gets the weight for a specific risk factor.
     * 
     * @param factor The risk factor
     * @return The assigned weight
     */
    public int getWeight(RiskFactor factor) {
        return WEIGHTS.getOrDefault(factor, 0);
    }

    /**
     * Evaluates the recommended risk action based on the score.
     *
     * @param score The risk score (0-100)
     * @return The recommended RiskAction
     */
    public RiskAction evaluateAction(int score) {
        if (score >= 80) {
            return RiskAction.ACCOUNT_TERMINATED;
        } else if (score >= 60) {
            return RiskAction.ACCOUNT_SUSPENDED;
        } else if (score >= 40) {
            return RiskAction.PAYOUT_FROZEN;
        } else {
            return RiskAction.NO_ACTION;
        }
    }
}
