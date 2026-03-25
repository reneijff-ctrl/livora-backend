package com.joinlivora.backend.analytics.adaptive;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class RevenueRiskCalculator {

    public double calculateSupporterConcentration(
            BigDecimal topSupporterRevenue,
            BigDecimal totalRevenue,
            int uniqueSupporters
    ) {
        if (totalRevenue == null || totalRevenue.compareTo(BigDecimal.ZERO) == 0) {
            return 0.0;
        }

        double concentration = topSupporterRevenue.doubleValue() / totalRevenue.doubleValue();
        double adjustedSCS = concentration * Math.log(uniqueSupporters + 1);

        return BigDecimal.valueOf(adjustedSCS)
                .setScale(4, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
