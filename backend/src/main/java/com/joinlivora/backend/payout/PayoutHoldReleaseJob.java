package com.joinlivora.backend.payout;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component("payoutHoldReleaseJob")
@RequiredArgsConstructor
@Slf4j
public class PayoutHoldReleaseJob {

    private final CreatorEarningsService creatorEarningsService;
    private final PayoutHoldService payoutHoldService;

    @Scheduled(fixedDelayString = "${livora.payout.unlock-check-interval-ms:600000}")
    public void checkAndUnlock() {
        log.info("PAYOUT_HOLD_JOB: Starting expired payout holds and earnings check...");
        try {
            int releasedHolds = payoutHoldService.releaseExpiredHolds();
            int unlockedEarnings = creatorEarningsService.unlockExpiredEarnings();

            if (releasedHolds > 0 || unlockedEarnings > 0) {
                log.info("PAYOUT_HOLD_JOB: Successfully released {} holds and unlocked {} earnings records",
                        releasedHolds, unlockedEarnings);
            } else {
                log.debug("PAYOUT_HOLD_JOB: No expired holds or earnings found");
            }
        } catch (Exception e) {
            log.error("PAYOUT_HOLD_JOB: Error during hold release/unlock process", e);
        }
    }
}
