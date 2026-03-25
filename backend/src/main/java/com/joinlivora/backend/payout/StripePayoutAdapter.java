package com.joinlivora.backend.payout;

import com.joinlivora.backend.exception.PayoutRestrictedException;
import com.joinlivora.backend.fraud.model.RiskSubjectType;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.param.AccountUpdateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service("stripePayoutAdapter")
@RequiredArgsConstructor
@Slf4j
public class StripePayoutAdapter {

    private final StripeClient stripeClient;
    private final PayoutHoldService payoutHoldService;

    /**
     * Maps holdDays to Stripe payout delay.
     * Log payout hold enforcement.
     */
    public void enforceHold(String stripeAccountId, int holdDays) throws StripeException {
        log.info("STRIPE: Enforcing payout hold of {} days for account {}", holdDays, stripeAccountId);
        
        AccountUpdateParams params = AccountUpdateParams.builder()
                .setSettings(AccountUpdateParams.Settings.builder()
                        .setPayouts(AccountUpdateParams.Settings.Payouts.builder()
                                .setSchedule(AccountUpdateParams.Settings.Payouts.Schedule.builder()
                                        .setDelayDays((long) holdDays)
                                        .build())
                                .build())
                        .build())
                .build();

        stripeClient.accounts().update(stripeAccountId, params);
    }

    /**
     * Prevents payout release if there's an active hold.
     */
    public void validateNoActiveHold(UUID subjectId, RiskSubjectType subjectType) {
        // We currently only support User/Creator based holds in the new system
        // subjectId is expected to be the creator UUID
        
        Instant now = Instant.now();
        
        // Use PayoutHoldService to get the combined status
        com.joinlivora.backend.payout.dto.PayoutHoldStatusDTO status = payoutHoldService.getPayoutHoldStatus(subjectId);
        
        if (status.getHoldLevel() != HoldLevel.NONE) {
            log.warn("PAYOUT ENFORCEMENT: Active hold found for {} {}. Expires at: {}. Reason: {}", 
                    subjectType, subjectId, status.getUnlockDate(), status.getReason());
            throw new PayoutRestrictedException("Payout is held until " + status.getUnlockDate() + ". Reason: " + status.getReason());
        }
    }
}
