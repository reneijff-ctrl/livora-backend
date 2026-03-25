package com.joinlivora.backend.monetization;

import com.joinlivora.backend.monetization.dto.CollusionResult;
import com.joinlivora.backend.user.User;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service("collusionAuditService")
@RequiredArgsConstructor
@Slf4j
public class CollusionAuditService {

    private final MeterRegistry meterRegistry;

    public void audit(User user, CollusionResult result) {
        log.info("COLLUSION AUDIT: Creator {}. Score: {}. Patterns: {}.",
                user.getId(),
                result.getCollusionScore(),
                String.join(", ", result.getPatternTypes()));

        if (result.getCollusionScore() > 0) {
            meterRegistry.counter("collusion_detected_total",
                    "score_range", getScoreRange(result.getCollusionScore())
            ).increment();
        }
    }

    public void recordRestriction(User user, String restrictionType, int score) {
        log.warn("SECURITY ALERT: Creator {} restricted. Type: {}. Score: {}.",
                user.getId(), restrictionType, score);

        meterRegistry.counter("creators_restricted_total",
                "restriction_type", restrictionType
        ).increment();
    }

    private String getScoreRange(int score) {
        if (score >= 90) return "90-100";
        if (score >= 70) return "70-89";
        if (score >= 40) return "40-69";
        return "1-39";
    }
}
