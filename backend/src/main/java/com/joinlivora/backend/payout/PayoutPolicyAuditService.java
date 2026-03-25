package com.joinlivora.backend.payout;

import com.joinlivora.backend.payout.dto.PayoutFrequency;
import com.joinlivora.backend.payout.dto.PayoutLimit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service("payoutPolicyAuditService")
@RequiredArgsConstructor
@Slf4j
public class PayoutPolicyAuditService {

    private final PayoutPolicyDecisionRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAutoDecision(UUID creatorId, int riskScore, PayoutLimit limit, UUID explanationId) {
        logDecision(creatorId, riskScore, limit.getMaxPayoutAmount(), limit.getPayoutFrequency(), DecisionSource.AUTO, limit.getReason(), explanationId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAdminDecision(UUID creatorId, BigDecimal limitAmount, PayoutFrequency frequency, String reason) {
        logDecision(creatorId, null, limitAmount, frequency, DecisionSource.ADMIN, reason, null);
    }

    private void logDecision(UUID creatorId, Integer riskScore, BigDecimal limitAmount, PayoutFrequency frequency, DecisionSource source, String reason, UUID explanationId) {
        log.info("PAYOUT POLICY: Logging {} decision for creator {}. Limit: {} @ {}, Risk: {}, ExplanationId: {}", 
                source, creatorId, limitAmount, frequency, riskScore, explanationId);
        
        PayoutPolicyDecision decision = PayoutPolicyDecision.builder()
                .creatorId(creatorId)
                .riskScore(riskScore)
                .appliedLimitAmount(limitAmount)
                .appliedLimitFrequency(frequency)
                .decisionSource(source)
                .reason(reason)
                .explanationId(explanationId)
                .build();
        
        repository.save(decision);
    }
}
