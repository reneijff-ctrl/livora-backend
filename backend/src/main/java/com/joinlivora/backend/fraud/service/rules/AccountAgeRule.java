package com.joinlivora.backend.fraud.service.rules;

import com.joinlivora.backend.fraud.service.FraudRiskRule;
import com.joinlivora.backend.user.User;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component("accountAgeRule")
public class AccountAgeRule implements FraudRiskRule {

    @Override
    public int evaluate(User user, java.util.Map<String, Object> context) {
        if (user.getCreatedAt() == null) return 50;

        long days = ChronoUnit.DAYS.between(user.getCreatedAt(), Instant.now());
        
        if (days < 1) return 80;
        if (days < 7) return 50;
        if (days < 30) return 20;
        return 0;
    }

    @Override
    public String getName() {
        return "ACCOUNT_AGE";
    }
}
