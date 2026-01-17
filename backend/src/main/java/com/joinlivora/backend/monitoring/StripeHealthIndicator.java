package com.joinlivora.backend.monitoring;

import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StripeHealthIndicator implements HealthIndicator {

    private final StripeClient stripeClient;

    @Override
    public Health health() {
        try {
            // Check connectivity by fetching a simple object from Stripe
            // This ensures our secret key is valid and we can reach Stripe API
            stripeClient.balance().retrieve();
            return Health.up()
                    .withDetail("provider", "Stripe")
                    .withDetail("status", "Reachable")
                    .build();
        } catch (StripeException e) {
            return Health.down()
                    .withDetail("provider", "Stripe")
                    .withDetail("error", e.getMessage())
                    .build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("provider", "Stripe")
                    .withDetail("error", "Unexpected error: " + e.getMessage())
                    .build();
        }
    }
}
