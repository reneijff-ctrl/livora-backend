package com.joinlivora.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
@Slf4j
@org.springframework.context.annotation.Profile("!test")
public class StartupValidationService implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;
    private final Environment environment;
    private final Integer platformFeePercentage;
    private final String stripeApiKey;
    private final String stripeWebhookSecret;
    private final String jwtSecret;

    public StartupValidationService(
            JdbcTemplate jdbcTemplate,
            Environment environment,
            @Value("${livora.monetization.platform-fee-percentage:#{null}}") Integer platformFeePercentage,
            @Value("${stripe.api.key:#{null}}") String stripeApiKey,
            @Value("${stripe.webhook.secret:#{null}}") String stripeWebhookSecret,
            @Value("${security.jwt.secret:#{null}}") String jwtSecret) {
        this.jdbcTemplate = jdbcTemplate;
        this.environment = environment;
        this.platformFeePercentage = platformFeePercentage;
        this.stripeApiKey = stripeApiKey;
        this.stripeWebhookSecret = stripeWebhookSecret;
        this.jwtSecret = jwtSecret;
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("STARTUP CHECK: Beginning validation...");
        
        validateDatabaseConnectivity();
        validateConfig();
        validateTables();
        logHighRiskUsers();
        
        log.info("STARTUP CHECK: All validations passed successfully.");
    }

    private void validateDatabaseConnectivity() {
        log.info("STARTUP CHECK: Validating database connectivity...");
        try (java.sql.Connection conn = jdbcTemplate.getDataSource().getConnection()) {
            if (conn.isValid(5)) {
                log.info("STARTUP CHECK: Database connectivity verified.");
            } else {
                throw new StartupValidationException("Database connection is invalid");
            }
        } catch (Exception e) {
            log.error("STARTUP ERROR: Database is unreachable: {}", e.getMessage());
            throw new StartupValidationException("Database is unreachable: " + e.getMessage());
        }
    }

    private void validateConfig() {
        log.info("STARTUP CHECK: Validating critical configurations...");
        boolean isProd = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        
        // 1. Revenue Split
        if (platformFeePercentage == null) {
            log.error("STARTUP ERROR: Platform fee configuration 'livora.monetization.platform-fee-percentage' is missing.");
            throw new StartupValidationException("Platform fee configuration is missing");
        }
        if (platformFeePercentage < 0 || platformFeePercentage > 100) {
            log.error("STARTUP ERROR: Platform fee configuration '{}' must be between 0 and 100.", platformFeePercentage);
            throw new StartupValidationException("Platform fee configuration must be between 0 and 100");
        }
        log.info("STARTUP CHECK: Revenue split configuration is present ({}%).", platformFeePercentage);

        // 2. Stripe API Key
        if (stripeApiKey == null || stripeApiKey.isBlank() || "${STRIPE_SECRET_KEY}".equals(stripeApiKey)) {
            log.error("STARTUP ERROR: Stripe API key 'stripe.api.key' is not configured.");
            throw new StartupValidationException("Stripe API key is missing");
        }
        log.info("STARTUP CHECK: Stripe API key is configured.");

        // 3. Stripe Webhook Secret
        if (stripeWebhookSecret == null || stripeWebhookSecret.isBlank() || "${STRIPE_WEBHOOK_SECRET}".equals(stripeWebhookSecret)) {
            if (isProd) {
                log.error("STARTUP ERROR: Stripe webhook secret 'stripe.webhook.secret' is missing in PROD!");
                throw new StartupValidationException("Stripe webhook secret is missing in PROD");
            } else {
                log.warn("STARTUP WARNING: Stripe webhook secret is not configured. Webhooks may fail signature verification.");
            }
        } else {
            log.info("STARTUP CHECK: Stripe webhook secret is configured.");
        }

        // 4. JWT Secret
        if (jwtSecret == null || jwtSecret.isBlank() || "${JWT_SECRET}".equals(jwtSecret)) {
            log.error("STARTUP ERROR: JWT secret 'security.jwt.secret' is not configured.");
            throw new StartupValidationException("JWT secret is missing");
        }
        log.info("STARTUP CHECK: JWT secret is configured.");
    }

    private void validateTables() throws Exception {
        log.info("STARTUP CHECK: Validating required tables existence...");
        String[] requiredTables = {
                "streams",
                "livestream_sessions",
                "users",
                "payments"
        };

        try (java.sql.Connection connection = jdbcTemplate.getDataSource().getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            List<String> missingTables = new ArrayList<>();

            for (String table : requiredTables) {
                try (ResultSet rs = metaData.getTables(null, null, table, new String[]{"TABLE"})) {
                    if (!rs.next()) {
                        // Some DBs might use uppercase
                        try (ResultSet rsUpper = metaData.getTables(null, null, table.toUpperCase(), new String[]{"TABLE"})) {
                            if (!rsUpper.next()) {
                                missingTables.add(table);
                            }
                        }
                    }
                }
            }

            if (!missingTables.isEmpty()) {
                log.error("STARTUP ERROR: Required tables are missing: {}", missingTables);
                throw new StartupValidationException("Missing required tables: " + missingTables);
            }
        }
        log.info("STARTUP CHECK: All required tables exist.");
        log.info("STREAM_SCHEMA_VALIDATED");
    }

    private void logHighRiskUsers() {
        try {
            Integer highRiskCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM users WHERE fraud_risk_level = 'HIGH'", Integer.class);
            log.info("STARTUP CHECK: Found {} users with HIGH fraud risk level.", highRiskCount != null ? highRiskCount : 0);
        } catch (Exception e) {
            log.error("STARTUP ERROR: Could not retrieve high-risk creator count: {}", e.getMessage());
            // We don't necessarily want to fail startup if only logging fails, 
            // but the requirement says "Log number of high-risk users", so we should treat failure as an error.
            // Given the requirement "Verify fraud tables exist", if users table exists but query fails, 
            // something is very wrong (e.g. column missing).
            throw new StartupValidationException("Failed to check high-risk users: " + e.getMessage());
        }
    }

    public static class StartupValidationException extends RuntimeException {
        public StartupValidationException(String message) {
            super(message);
        }
    }
}
