package com.joinlivora.backend.payment.config;

import com.stripe.Stripe;
import com.stripe.StripeClient;
import jakarta.annotation.PostConstruct;
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

    @Value("${stripe.api.key}")
    private String stripeApiKey;

    @PostConstruct
    public void validate() {
        if (stripeApiKey == null || stripeApiKey.trim().isEmpty() || stripeApiKey.equals("${STRIPE_SECRET_KEY}")) {
            log.error("FATAL: Stripe API key is missing. Please set the STRIPE_SECRET_KEY environment variable.");
            throw new IllegalStateException("Stripe API key is not configured. Application cannot start.");
        }

        if (!stripeApiKey.startsWith("sk_")) {
            log.error("FATAL: Invalid Stripe API key. Key must start with 'sk_'.");
            throw new IllegalStateException("Invalid Stripe API key. Key must start with 'sk_'.");
        }
    }

    @Bean
    public StripeClient stripeClient() {
        // Validation is now also in @PostConstruct, but we keep it here for extra safety 
        // in case this bean is used before PostConstruct is triggered (unlikely for Config)
        validate();
        
        // Initialize the static Stripe API key for older SDK usage
        Stripe.apiKey = stripeApiKey;
        log.info("Stripe API initialized successfully.");
        
        return new StripeClient(stripeApiKey);
    }
}
