-- Pre-migration hardening: store EUR value at write time to prevent recalculation errors
-- when TOKEN_TO_EUR_RATE changes in the future.
-- Nullable: existing rows will have NULL (legacy); new rows will always be populated.
ALTER TABLE creator_earnings_history
    ADD COLUMN IF NOT EXISTS net_amount_eur NUMERIC(19, 4);
