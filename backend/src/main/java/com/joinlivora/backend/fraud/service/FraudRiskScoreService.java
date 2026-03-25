package com.joinlivora.backend.fraud.service;

import com.joinlivora.backend.fraud.model.FraudRiskScore;
import com.joinlivora.backend.fraud.repository.FraudRiskScoreRepository;
import com.joinlivora.backend.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service("fraudRiskScoreService")
@RequiredArgsConstructor
@Slf4j
public class FraudRiskScoreService {

    private final List<FraudRiskRule> rules;
    private final FraudRiskScoreRepository fraudRiskScoreRepository;
    private final com.joinlivora.backend.admin.service.AdminRealtimeEventService adminRealtimeEventService;

    /**
     * Calculates and persists the fraud risk score for a creator.
     * @param user The creator to evaluate
     * @param context Contextual data (IP, country, fingerprint, etc.)
     * @return The calculated score (0-100)
     */
    @Transactional
    public int calculateAndSaveScore(User user, Map<String, Object> context) {
        log.info("Calculating fraud risk score for creator: {} ({})", user.getEmail(), user.getId());

        Map<String, Object> factorsBreakdown = new HashMap<>();
        int totalScore = 0;

        for (FraudRiskRule rule : rules) {
            int ruleScore = rule.evaluate(user, context);
            factorsBreakdown.put(rule.getName(), ruleScore);
            totalScore += ruleScore;
        }

        // Cap score at 100
        int finalScore = Math.min(100, totalScore);

        FraudRiskScore riskScore = FraudRiskScore.builder()
                .userId(user.getId())
                .score(finalScore)
                .factors(factorsBreakdown)
                .evaluatedAt(Instant.now())
                .build();

        fraudRiskScoreRepository.save(riskScore);

        if (finalScore > 70) {
            adminRealtimeEventService.broadcastFraudSignal(user.getUsername(), finalScore);
        }

        log.info("Fraud risk score for creator {}: {} (Factors: {})", user.getEmail(), finalScore, factorsBreakdown);
        return finalScore;
    }

    public int getLatestScore(Long userId) {
        return fraudRiskScoreRepository.findById(userId)
                .map(FraudRiskScore::getScore)
                .orElse(0);
    }
}
