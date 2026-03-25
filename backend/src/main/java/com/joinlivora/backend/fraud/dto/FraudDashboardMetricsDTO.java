package com.joinlivora.backend.fraud.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FraudDashboardMetricsDTO {
    private long unresolvedSignals;
    private long criticalSignals;
    private long highSignals;
    private long enforcementLast24h;
    
    // Fraud Signals (Last Hour)
    private long newAccountTippingHigh;
    private long newAccountTippingMedium;
    private long newAccountTipCluster;
    private long rapidTipRepeats;
}
