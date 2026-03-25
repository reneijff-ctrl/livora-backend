package com.joinlivora.backend.payout;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component("payoutHoldProperties")
@ConfigurationProperties(prefix = "livora.payout.hold-rules")
public class PayoutHoldProperties {
    
    private Thresholds none = new Thresholds(20, 0.5, 0);
    private Thresholds shortHold = new Thresholds(40, 0.5, 3);
    private Thresholds mediumHold = new Thresholds(60, 0.5, 7);
    private Thresholds longHold = new Thresholds(100, 1.0, 14);

    @Data
    public static class Thresholds {
        private int riskScore;
        private double chargebackRate;
        private int days;

        public Thresholds() {}

        public Thresholds(int riskScore, double chargebackRate, int days) {
            this.riskScore = riskScore;
            this.chargebackRate = chargebackRate;
            this.days = days;
        }
    }
}
