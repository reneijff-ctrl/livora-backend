package com.joinlivora.backend.payment.config;

import com.stripe.StripeClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for the Stripe SDK.
 */
@Configuration
@Slf4j
public class StripeConfig {

    @Value("${stripe.secret-key}")
    private String stripeApiKey;

    @Bean
    public StripeClient stripeClient() {
        if (stripeApiKey == null || stripeApiKey.trim().isEmpty() || stripeApiKey.equals("${STRIPE_SECRET_KEY}")) {
            log.error("FATAL: Stripe API key is missing. Please set the STRIPE_SECRET_KEY environment variable.");
            throw new IllegalStateException("Stripe API key is not configured. Application cannot start.");
        }
        return new StripeClient(stripeApiKey);
    }
}
