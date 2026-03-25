package com.joinlivora.backend.fraud.service.rules;

import com.joinlivora.backend.fraud.service.FraudRiskRule;
import com.joinlivora.backend.payment.PaymentRepository;
import com.joinlivora.backend.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component("locationMismatchRule")
@RequiredArgsConstructor
public class LocationMismatchRule implements FraudRiskRule {

    private final PaymentRepository paymentRepository;

    @Override
    public int evaluate(User user, Map<String, Object> context) {
        String currentCountry = (String) context.get("country");
        if (currentCountry == null) return 0;

        List<String> lastCountries = paymentRepository.findLastSuccessfulCountriesByUserId(user.getId(), PageRequest.of(0, 5));
        
        if (lastCountries.isEmpty()) return 0; // New creator, nothing to compare against

        // If the current country is not in the last 5 successful countries, flag it
        if (!lastCountries.contains(currentCountry)) {
            return 40;
        }

        return 0;
    }

    @Override
    public String getName() {
        return "LOCATION_MISMATCH";
    }
}
