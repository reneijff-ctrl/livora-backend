package com.joinlivora.backend.fraud.service.rules;

import com.joinlivora.backend.fraud.service.FraudRiskRule;
import com.joinlivora.backend.payment.PaymentRepository;
import com.joinlivora.backend.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component("spendVelocityRule")
@RequiredArgsConstructor
public class SpendVelocityRule implements FraudRiskRule {

    private final PaymentRepository paymentRepository;

    @Override
    public int evaluate(User user, java.util.Map<String, Object> context) {
        Instant oneHourAgo = Instant.now().minus(1, ChronoUnit.HOURS);
        BigDecimal hourlySpend = paymentRepository.sumAmountByUserIdAndSuccessAndCreatedAtAfter(user.getId(), oneHourAgo);
        
        if (hourlySpend == null) hourlySpend = BigDecimal.ZERO;

        if (hourlySpend.compareTo(new BigDecimal("500")) > 0) return 100;
        if (hourlySpend.compareTo(new BigDecimal("200")) > 0) return 60;
        if (hourlySpend.compareTo(new BigDecimal("100")) > 0) return 30;
        
        return 0;
    }

    @Override
    public String getName() {
        return "SPEND_VELOCITY";
    }
}
