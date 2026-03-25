package com.joinlivora.backend.fraud.service;

import com.joinlivora.backend.fraud.model.FraudDecisionLevel;
import com.joinlivora.backend.fraud.model.RiskProfile;
import com.joinlivora.backend.monetization.CollusionDetectionService;
import com.joinlivora.backend.monetization.CreatorTrustService;
import com.joinlivora.backend.monetization.dto.CollusionResult;
import com.joinlivora.backend.payment.ChargebackHistoryService;
import com.joinlivora.backend.reputation.repository.CreatorReputationSnapshotRepository;
import com.joinlivora.backend.user.User;
import com.joinlivora.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service("riskProfileService")
@RequiredArgsConstructor
@Slf4j
public class RiskProfileService {

    private final FraudDetectionService fraudDetectionService;
    private final CollusionDetectionService collusionDetectionService;
    private final ChargebackHistoryService chargebackHistoryService;
    private final CreatorTrustService creatorTrustService;
    private final UserRepository userRepository;
    private final CreatorReputationSnapshotRepository reputationSnapshotRepository;

    @Transactional
    public RiskProfile generateRiskProfile(UUID userId) {
        log.info("Generating risk profile for creator: {}", userId);
        
        Long userIdLong = userId.getLeastSignificantBits();
        User user = userRepository.findById(userIdLong)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userIdLong));

        Map<String, Object> factors = new HashMap<>();

        // 1. Fraud Risk from FraudDetectionService
        // We look at signals from the beginning of time for a full profile
        FraudDecisionLevel highestFraudSignal = fraudDetectionService.getHighestSignalLevel(userIdLong, Instant.EPOCH);
        int fraudScore = convertFraudDecisionToScore(highestFraudSignal);
        if (fraudScore > 0) {
            factors.put("FRAUD_LEVEL_" + highestFraudSignal.name(), true);
        }

        // 2. Collusion Risk from CollusionDetectionService
        CollusionResult collusionResult = collusionDetectionService.detectCollusion(userId);
        int collusionScore = collusionResult.getCollusionScore();
        if (collusionScore >= 60) {
            factors.put("COLLUSION_DETECTED", true);
        }
        if (collusionResult.getPatternTypes() != null) {
            collusionResult.getPatternTypes().forEach(p -> factors.put(p, true));
        }

        // 3. Chargeback Risk from ChargebackHistoryService
        int chargebackScore = chargebackHistoryService.getChargebackRiskScore(userIdLong);
        if (chargebackScore > 0) {
            factors.put("CHARGEBACK_CORRELATION", true);
            if (chargebackScore >= 50) {
                factors.put("HIGH_CHARGEBACK_RATIO", true);
            }
        }

        // 4. Update Trust via CreatorTrustService
        // This ensures the creator's trustScore is up-to-date based on the latest collusion analysis
        creatorTrustService.evaluateTrust(user);
        int trustScore = user.getTrustScore();

        // Aggregate risk score (taking the maximum of different risk factors)
        int aggregateRiskScore = Math.max(fraudScore, Math.max(collusionScore, chargebackScore));

        // 5. Adjust based on Reputation Status
        aggregateRiskScore = adjustScoreByReputation(userId, aggregateRiskScore, factors);

        return RiskProfile.builder()
                .userId(userId)
                .riskScore(aggregateRiskScore)
                .trustScore(trustScore)
                .lastEvaluatedAt(Instant.now())
                .factors(factors)
                .build();
    }

    private int adjustScoreByReputation(UUID creatorId, int currentScore, Map<String, Object> factors) {
        return reputationSnapshotRepository.findById(creatorId)
                .map(snapshot -> {
                    int adjustment = switch (snapshot.getStatus()) {
                        case TRUSTED -> -20;
                        case WATCHED -> 10;
                        case RESTRICTED -> {
                            factors.put("LOW_REPUTATION", true);
                            yield 25;
                        }
                        case NORMAL -> 0;
                    };
                    log.debug("Reputation status {} for creator {} adjusted score by {}", 
                            snapshot.getStatus(), creatorId, adjustment);
                    return Math.max(0, Math.min(100, currentScore + adjustment));
                })
                .orElse(currentScore);
    }

    private int convertFraudDecisionToScore(FraudDecisionLevel decision) {
        if (decision == null) return 0;
        return switch (decision) {
            case CRITICAL, HIGH -> 100;
            case MEDIUM -> 50;
            case LOW -> 10; // Low signals still count for something
        };
    }
}
