package com.joinlivora.backend.payout;

import com.joinlivora.backend.payout.dto.AmlResult;
import com.joinlivora.backend.user.User;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service("payoutAmlAuditService")
@RequiredArgsConstructor
@Slf4j
public class AmlAuditService {

    private final MeterRegistry meterRegistry;

    public void audit(User user, BigDecimal amount, AmlResult result, boolean blocked) {
        log.info("AML AUDIT: Risk assessment for creator {}. Score: {}, Rules: {}, Amount: {}, Blocked: {}",
                user.getId(),
                result.getRiskScore(),
                result.getTriggeredRules(),
                amount,
                blocked);

        if (result.getRiskScore() > 0) {
            meterRegistry.counter("aml_risk_detected_total",
                    "risk_score_range", getScoreRange(result.getRiskScore()),
                    "blocked", String.valueOf(blocked)
            ).increment();
        }

        if (blocked) {
            log.warn("SECURITY ALERT: Payout blocked due to AML risk for creator {}. Score: {}", user.getId(), result.getRiskScore());
            meterRegistry.counter("payouts_blocked_total",
                    "type", "AML_RISK"
            ).increment();
        }
    }

    private String getScoreRange(int score) {
        if (score >= 90) return "90-100";
        if (score >= 70) return "70-89";
        if (score >= 40) return "40-69";
        return "1-39";
    }
}
