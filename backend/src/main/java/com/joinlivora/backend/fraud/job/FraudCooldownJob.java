package com.joinlivora.backend.fraud.job;

import com.joinlivora.backend.fraud.model.UserRiskState;
import com.joinlivora.backend.fraud.repository.UserRiskStateRepository;
import com.joinlivora.backend.fraud.service.FraudDetectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component("fraudCooldownJob")
@RequiredArgsConstructor
public class FraudCooldownJob {

    private final UserRiskStateRepository userRiskStateRepository;
    private final FraudDetectionService fraudDetectionService;

    @Scheduled(fixedRate = 600000) // 10 minutes
    public void run() {
        log.info("Starting FraudCooldownJob...");
        Instant now = Instant.now();
        List<UserRiskState> expiredBlocks = userRiskStateRepository.findAllByBlockedUntilBefore(now);
        
        log.info("Found {} users with expired fraud blocks", expiredBlocks.size());
        
        for (UserRiskState state : expiredBlocks) {
            try {
                fraudDetectionService.processCooldown(state);
            } catch (Exception e) {
                log.error("Failed to process fraud cooldown for creator ID: {}", state.getUserId(), e);
            }
        }
        log.info("FraudCooldownJob completed.");
    }
}
