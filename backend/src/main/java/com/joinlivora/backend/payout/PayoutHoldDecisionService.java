package com.joinlivora.backend.payout;

import com.joinlivora.backend.payout.dto.PayoutHoldDecision;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service("payoutHoldDecisionService")
@RequiredArgsConstructor
@Slf4j
public class PayoutHoldDecisionService {

    private final PayoutHoldProperties properties;

    /**
     * Determines the payout hold policy based on creator risk metrics.
     *
     * @param riskScore       Risk score from fraud detection (0-100)
     * @param chargebackRate  Recent chargeback rate as percentage (0-100)
     * @param accountAgeDays  Age of the account in days
     * @param totalEarnings   Total lifetime earnings in USD or platform currency
     * @return PayoutHoldDecision containing hold level, days, and type
     */
    public PayoutHoldDecision decide(int riskScore, double chargebackRate, int accountAgeDays, BigDecimal totalEarnings) {
        log.info("Deciding payout hold: riskScore={}, chargebackRate={}, accountAgeDays={}, totalEarnings={}",
                riskScore, chargebackRate, accountAgeDays, totalEarnings);

        // Rules according to requirements:
        // IF riskScore > 60 OR chargebackRate > 1% → LONG (14-30 days)
        if (riskScore > properties.getMediumHold().getRiskScore() || chargebackRate > properties.getLongHold().getChargebackRate()) {
            return PayoutHoldDecision.builder()
                    .holdLevel(HoldLevel.LONG)
                    .holdDays(properties.getLongHold().getDays())
                    .reason("High risk score or elevated chargeback rate")
                    .build();
        }

        // IF riskScore 40–60 → MEDIUM (7 days)
        if (riskScore >= properties.getShortHold().getRiskScore()) {
            return PayoutHoldDecision.builder()
                    .holdLevel(HoldLevel.MEDIUM)
                    .holdDays(properties.getMediumHold().getDays())
                    .reason("Moderate risk score")
                    .build();
        }

        // IF riskScore 20–40 → SHORT (3 days)
        if (riskScore >= properties.getNone().getRiskScore()) {
            return PayoutHoldDecision.builder()
                    .holdLevel(HoldLevel.SHORT)
                    .holdDays(properties.getShortHold().getDays())
                    .reason("Minor risk detected")
                    .build();
        }

        // IF riskScore < 20 AND chargebackRate < 0.5% → NONE
        return PayoutHoldDecision.builder()
                .holdLevel(HoldLevel.NONE)
                .holdDays(properties.getNone().getDays())
                .reason("Low risk profile")
                .build();
    }
}
