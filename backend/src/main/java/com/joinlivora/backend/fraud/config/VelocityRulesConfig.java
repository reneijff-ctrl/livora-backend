package com.joinlivora.backend.fraud.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Data
@Configuration
@ConfigurationProperties(prefix = "livora.fraud.velocity")
public class VelocityRulesConfig {

    private Map<String, Rule> login;
    private Map<String, Rule> tip;
    private Map<String, Rule> payment;
    private Map<String, Rule> message;

    @Data
    public static class Rule {
        private int limit;
        private String action; // suspicious, critical, spam
    }
}
