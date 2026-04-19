package com.joinlivora.backend.payment;

import com.joinlivora.backend.chargeback.model.ChargebackCase;
import com.joinlivora.backend.chargeback.repository.ChargebackCaseRepository;
import com.joinlivora.backend.fraud.model.RiskLevel;
import com.joinlivora.backend.payment.dto.RiskEscalationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service("chargebackRiskEscalationService")
@RequiredArgsConstructor
@Slf4j
public class ChargebackRiskEscalationService {

    private final ChargebackCaseRepository chargebackCaseRepository;

    public RiskEscalationResult evaluateEscalation(Long creatorId) {
        if (creatorId == null) {
            return RiskEscalationResult.builder()
                    .riskLevel(RiskLevel.LOW)
                    .actions(List.of())
                    .build();
        }

        List<ChargebackCase> chargebacks = chargebackCaseRepository.findAllByCreatorId(creatorId);
        
        if (chargebacks.isEmpty()) {
            return RiskEscalationResult.builder()
                    .riskLevel(RiskLevel.LOW)
                    .actions(List.of())
                    .build();
        }

        Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);
        long recentCount = chargebacks.stream()
                .filter(cb -> cb.getCreatedAt().isAfter(thirtyDaysAgo))
                .count();

        if (recentCount >= 2) {
            log.warn("HIGH risk detected for creator {} due to {} chargebacks in the last 30 days", creatorId, recentCount);
            return RiskEscalationResult.builder()
                    .riskLevel(RiskLevel.HIGH)
                    .actions(List.of("PAYOUT_HOLD", "MANUAL_REVIEW"))
                    .build();
        }

        log.info("MEDIUM risk detected for creator {} due to {} total chargebacks", creatorId, chargebacks.size());
        return RiskEscalationResult.builder()
                .riskLevel(RiskLevel.MEDIUM)
                .actions(List.of())
                .build();
    }
}
