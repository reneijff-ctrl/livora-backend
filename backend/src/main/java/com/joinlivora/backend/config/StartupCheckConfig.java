package com.joinlivora.backend.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;

@Configuration
@Slf4j
public class StartupCheckConfig {

    private final Environment env;
    private final DataSource dataSource;

    @Value("${security.jwt.secret}")
    private String jwtSecret;

    @Value("${stripe.secret-key}")
    private String stripeSecretKey;

    @Value("${stripe.webhook-secret}")
    private String stripeWebhookSecret;

    public StartupCheckConfig(Environment env, DataSource dataSource) {
        this.env = env;
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void check() {
        log.info("Starting production readiness checks...");
        boolean isProd = Arrays.asList(env.getActiveProfiles()).contains("prod");

        // 1. JWT Secret check
        if (jwtSecret == null || jwtSecret.isBlank() || "${JWT_SECRET}".equals(jwtSecret)) {
            log.error("FATAL: JWT_SECRET is not configured!");
            throw new IllegalStateException("JWT_SECRET must be provided");
        }

        // 2. Stripe Key check
        if (stripeSecretKey == null || stripeSecretKey.isBlank() || "${STRIPE_SECRET_KEY}".equals(stripeSecretKey)) {
            log.error("FATAL: STRIPE_SECRET_KEY is not configured!");
            throw new IllegalStateException("STRIPE_SECRET_KEY must be provided");
        }
        
        if (stripeWebhookSecret == null || stripeWebhookSecret.isBlank() || "${STRIPE_WEBHOOK_SECRET}".equals(stripeWebhookSecret)) {
             log.warn("WARNING: STRIPE_WEBHOOK_SECRET is not configured. Webhooks will fail signature verification.");
             if (isProd) {
                 log.error("FATAL: STRIPE_WEBHOOK_SECRET must be provided in production!");
                 throw new IllegalStateException("STRIPE_WEBHOOK_SECRET must be provided in production");
             }
        }

        // 3. DB Connectivity check
        try (Connection conn = dataSource.getConnection()) {
            if (conn.isValid(5)) {
                log.info("Database connectivity verified.");
            } else {
                throw new SQLException("Database connection is invalid");
            }
        } catch (SQLException e) {
            log.error("FATAL: Database is unreachable: {}", e.getMessage());
            throw new IllegalStateException("Database is unreachable", e);
        }

        log.info("Production readiness checks passed successfully.");
    }
}
