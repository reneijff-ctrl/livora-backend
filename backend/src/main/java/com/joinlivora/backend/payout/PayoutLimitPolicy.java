package com.joinlivora.backend.payout;

import com.joinlivora.backend.payout.dto.PayoutFrequency;
import com.joinlivora.backend.payout.dto.PayoutLimit;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service("payoutLimitPolicy")
public class PayoutLimitPolicy {

    public static final BigDecimal DAILY_CAP = new BigDecimal("100.00");
    public static final BigDecimal WEEKLY_CAP = new BigDecimal("500.00");

    public PayoutLimit getLimit(int riskScore) {
        if (riskScore < 30) {
            return PayoutLimit.builder()
                    .maxPayoutAmount(null)
                    .payoutFrequency(PayoutFrequency.NO_LIMIT)
                    .reason("Risk score < 30: No payout limits applied.")
                    .build();
        } else if (riskScore < 60) {
            return PayoutLimit.builder()
                    .maxPayoutAmount(DAILY_CAP)
                    .payoutFrequency(PayoutFrequency.DAILY)
                    .reason("Risk score 30-59: Daily payout cap applied.")
                    .build();
        } else if (riskScore < 80) {
            return PayoutLimit.builder()
                    .maxPayoutAmount(WEEKLY_CAP)
                    .payoutFrequency(PayoutFrequency.WEEKLY)
                    .reason("Risk score 60-79: Weekly payout cap applied.")
                    .build();
        } else {
            return PayoutLimit.builder()
                    .maxPayoutAmount(BigDecimal.ZERO)
                    .payoutFrequency(PayoutFrequency.PAUSED)
                    .reason("Risk score >= 80: Payouts paused.")
                    .build();
        }
    }
}
