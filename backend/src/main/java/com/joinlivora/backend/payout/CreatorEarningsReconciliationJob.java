package com.joinlivora.backend.payout;

import com.joinlivora.backend.token.CreatorEarningsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class CreatorEarningsReconciliationJob {

    private static final BigDecimal TOKEN_MISMATCH_THRESHOLD = BigDecimal.ONE;
    private static final BigDecimal EUR_MISMATCH_THRESHOLD = new BigDecimal("0.01");

    private final CreatorEarningRepository creatorEarningRepository;
    private final CreatorEarningsRepository tokenEarningsRepository;
    private final PayoutCreatorEarningsRepository payoutCreatorEarningsRepository;

    @Scheduled(cron = "0 */15 * * * *")
    public void reconcile() {
        Instant since = Instant.now().minus(24, ChronoUnit.HOURS);
        List<Long> creatorIds;
        try {
            creatorIds = creatorEarningRepository.findRecentlyActiveCreatorIds(since);
        } catch (Exception e) {
            log.error("RECONCILE_JOB: Failed to fetch recently active creator IDs", e);
            return;
        }

        if (creatorIds.isEmpty()) {
            log.debug("RECONCILE_JOB: No recently active creators to reconcile");
            return;
        }

        log.debug("RECONCILE_JOB: Reconciling {} recently active creators", creatorIds.size());

        int mismatches = 0;
        for (Long creatorId : creatorIds) {
            try {
                mismatches += reconcileCreator(creatorId);
            } catch (Exception e) {
                log.error("RECONCILE_JOB: Error reconciling creatorId={}", creatorId, e);
            }
        }

        if (mismatches > 0) {
            log.warn("RECONCILE_JOB: Completed with {} mismatch(es) across {} creators", mismatches, creatorIds.size());
        } else {
            log.debug("RECONCILE_JOB: Completed cleanly for {} creators — no mismatches", creatorIds.size());
        }
    }

    private int reconcileCreator(Long creatorId) {
        int mismatches = 0;

        // --- Token balance reconciliation ---
        BigDecimal tokensLedger = creatorEarningRepository.sumAvailableTokens(creatorId);
        if (tokensLedger == null) tokensLedger = BigDecimal.ZERO;

        long tokensStored = tokenEarningsRepository.findByUserId(creatorId)
                .map(com.joinlivora.backend.token.CreatorEarnings::getAvailableTokens)
                .orElse(0L);

        BigDecimal tokensStoredDecimal = BigDecimal.valueOf(tokensStored);
        BigDecimal tokenDiff = tokensLedger.subtract(tokensStoredDecimal).abs();
        if (tokenDiff.compareTo(TOKEN_MISMATCH_THRESHOLD) > 0) {
            log.error("RECONCILE_MISMATCH_TOKENS: creatorId={}, ledger={}, stored={}, diff={}",
                    creatorId, tokensLedger, tokensStored, tokenDiff);
            mismatches++;
        }

        // --- EUR balance reconciliation ---
        BigDecimal eurLedger = creatorEarningRepository.sumAvailableEur(creatorId);
        if (eurLedger == null) eurLedger = BigDecimal.ZERO;

        BigDecimal eurStored = payoutCreatorEarningsRepository.findByCreatorId(creatorId)
                .map(CreatorEarnings::getAvailableBalance)
                .orElse(BigDecimal.ZERO);

        BigDecimal eurDiff = eurLedger.subtract(eurStored).abs();
        if (eurDiff.compareTo(EUR_MISMATCH_THRESHOLD) > 0) {
            log.error("RECONCILE_MISMATCH_EUR: creatorId={}, ledger={}, stored={}, diff={}",
                    creatorId, eurLedger, eurStored, eurDiff);
            mismatches++;
        }

        return mismatches;
    }
}
