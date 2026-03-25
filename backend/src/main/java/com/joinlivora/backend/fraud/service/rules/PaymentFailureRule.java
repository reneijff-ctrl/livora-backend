package com.joinlivora.backend.fraud.service.rules;

import com.joinlivora.backend.fraud.service.FraudRiskRule;
import com.joinlivora.backend.payment.PaymentRepository;
import com.joinlivora.backend.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component("paymentFailureRule")
@RequiredArgsConstructor
public class PaymentFailureRule implements FraudRiskRule {

    private final PaymentRepository paymentRepository;

    @Override
    public int evaluate(User user, java.util.Map<String, Object> context) {
        Instant since = Instant.now().minus(24, ChronoUnit.HOURS);
        long failures = paymentRepository.countByUserIdAndSuccessAndCreatedAtAfter(user.getId(), false, since);
        
        if (failures >= 5) return 100;
        if (failures >= 3) return 60;
        if (failures >= 1) return 20;
        
        return 0;
    }

    @Override
    public String getName() {
        return "PAYMENT_FAILURES";
    }
}
