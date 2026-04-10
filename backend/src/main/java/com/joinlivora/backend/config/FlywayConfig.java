package com.joinlivora.backend.config;

import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayConfig {

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
            // Repair the schema history table to fix checksum mismatches
            flyway.repair();
            // Then run the migrations
            flyway.migrate();
        };
    }
}
