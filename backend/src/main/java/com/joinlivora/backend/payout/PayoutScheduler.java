package com.joinlivora.backend.payout;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component("payoutScheduler")
@RequiredArgsConstructor
@Slf4j
public class PayoutScheduler {

    private final PayoutService payoutService;
    private final com.joinlivora.backend.payouts.service.PayoutExecutionService payoutExecutionService;
    private final CreatorPayoutSettingsRepository creatorPayoutSettingsRepository;

    /**
     * Runs daily at 03:00 to execute payouts for eligible creators.
     */
    @Scheduled(cron = "0 0 3 * * *")
    public void executeDailyPayouts() {
        log.info("PAYOUT_JOB: Starting daily payout batch execution");
        
        List<CreatorPayoutSettings> enabledSettings = creatorPayoutSettingsRepository.findAllByEnabledTrue();
        int processedCount = 0;
        int successCount = 0;
        int failureCount = 0;

        for (CreatorPayoutSettings settings : enabledSettings) {
            try {
                processedCount++;
                BigDecimal availableAmount = payoutService.calculateAvailablePayout(settings.getCreatorId());
                
                BigDecimal minAmount = settings.getMinimumPayoutAmount();
                if (minAmount == null) {
                    minAmount = BigDecimal.ZERO;
                }

                if (availableAmount.compareTo(minAmount) >= 0 && availableAmount.compareTo(BigDecimal.ZERO) > 0) {
                    log.info("PAYOUT_JOB: Executing payout for creator {}: Amount={}", settings.getCreatorId(), availableAmount);
                    payoutExecutionService.executePayout(settings.getCreatorId(), availableAmount, "EUR");
                    successCount++;
                } else {
                    log.debug("PAYOUT_JOB: Creator {} not eligible for payout. Available={}, Min={}", 
                            settings.getCreatorId(), availableAmount, minAmount);
                }
            } catch (Exception e) {
                failureCount++;
                log.error("PAYOUT_JOB: Failed to execute payout for creator {}", settings.getCreatorId(), e);
            }
        }

        log.info("PAYOUT_JOB: Finished daily payout batch. Processed: {}, Succeeded: {}, Failed: {}", 
                processedCount, successCount, failureCount);
    }
}
