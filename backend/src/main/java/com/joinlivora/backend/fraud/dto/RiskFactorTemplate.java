package com.joinlivora.backend.fraud.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskFactorTemplate {
    private String description;
    private String severity; // e.g., LOW, MEDIUM, HIGH, CRITICAL
    private String timeWindow; // e.g., "last 14 days"
    private String dataSource; // e.g., "Payments", "Reputation System"

    public String format() {
        return String.format("[%s] [%s] %s (%s)", 
                severity, dataSource, description, timeWindow);
    }
}
