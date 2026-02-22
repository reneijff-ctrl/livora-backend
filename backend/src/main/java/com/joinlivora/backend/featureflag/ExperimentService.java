package com.joinlivora.backend.featureflag;

import com.joinlivora.backend.analytics.AnalyticsEventPublisher;
import com.joinlivora.backend.analytics.AnalyticsEventType;
import com.joinlivora.backend.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExperimentService {

    private final FeatureFlagService featureFlagService;
    private final AnalyticsEventPublisher analyticsEventPublisher;

    /**
     * Gets the assigned variant for a creator for a given experiment.
     * 
     * @param experimentKey The key of the experiment (must be a feature flag marked as experiment).
     * @param user The creator to assign a variant for.
     * @param funnelId Optional funnel ID for analytics.
     * @return "A", "B", or "CONTROL" if the experiment is disabled.
     */
    public String getVariant(String experimentKey, User user, String funnelId) {
        if (user == null) {
            return "CONTROL";
        }

        FeatureFlag flag = featureFlagService.getFlag(experimentKey);
        if (flag == null || !flag.isExperiment() || !flag.isEnabled()) {
            return "CONTROL";
        }

        // If creator is in rollout, assign A or B
        if (featureFlagService.isEnabled(experimentKey, user)) {
            String variant = assignVariant(user.getId().toString(), experimentKey);
            
            // Track experiment assignment
            analyticsEventPublisher.publishEvent(
                    AnalyticsEventType.EXPERIMENT_ASSIGNED,
                    user,
                    funnelId,
                    Map.of(
                            "experimentKey", experimentKey,
                            "variant", variant
                    )
            );
            
            return variant;
        }

        return "CONTROL";
    }

    private String assignVariant(String userId, String experimentKey) {
        try {
            String combined = userId + ":" + experimentKey + ":variant";
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(combined.getBytes(StandardCharsets.UTF_8));
            
            // Use the hash to split between A and B
            int value = hash[0] & 0xFF;
            return (value % 2 == 0) ? "A" : "B";
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 algorithm not found", e);
            return "A"; // Fallback
        }
    }
}
