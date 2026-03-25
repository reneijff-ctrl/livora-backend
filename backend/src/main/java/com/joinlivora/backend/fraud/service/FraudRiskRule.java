package com.joinlivora.backend.fraud.service;

import com.joinlivora.backend.user.User;
import java.util.Map;

public interface FraudRiskRule {
    /**
     * Evaluates a specific fraud factor for the creator.
     * @param user The creator to evaluate
     * @param context Additional context (current IP, country, etc.)
     * @return a score contribution (0-100)
     */
    int evaluate(User user, Map<String, Object> context);

    /**
     * Returns the name of the rule for factor breakdown.
     */
    String getName();
}
