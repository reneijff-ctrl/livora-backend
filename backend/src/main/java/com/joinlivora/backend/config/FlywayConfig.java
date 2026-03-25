package com.joinlivora.backend.config;

import org.springframework.context.annotation.Configuration;

/**
 * Flyway uses Spring Boot defaults with strict validation.
 * No custom migration strategy, no out-of-order, no repair.
 */
@Configuration
public class FlywayConfig {
    // Intentionally empty: rely on Spring Boot's default Flyway auto-configuration
}
