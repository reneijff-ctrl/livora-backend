package com.joinlivora.backend.payout;

import com.joinlivora.backend.payout.dto.CreatorEarningsDTO;

/**
 * Projection interface for deriving creator earnings balances from the
 * {@code creator_earnings_history} ledger (single source of truth).
 *
 * <p>This interface is prepared for Phase C of the ledger migration.
 * DO NOT implement yet — implementation will replace reads from
 * {@code creator_earnings} and {@code creator_earnings_balances} tables
 * once Phase B shadow validation confirms ledger correctness.
 */
public interface CreatorEarningsProjectionService {

    /**
     * Returns a creator's earnings summary derived purely from the immutable
     * history ledger, without reading the balance summary tables.
     *
     * @param creatorId the {@code users.id} of the creator
     * @return a fully populated {@link CreatorEarningsDTO} derived from ledger SUM queries
     */
    CreatorEarningsDTO getProjection(Long creatorId);
}
