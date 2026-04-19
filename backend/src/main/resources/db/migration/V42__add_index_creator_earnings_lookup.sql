-- Index to support future SUM queries for ledger-derived balance projections
-- Covers: creator_id, currency, locked, dry_run — the four columns used in Phase B reconciliation queries
CREATE INDEX IF NOT EXISTS idx_creator_earnings_lookup
    ON creator_earnings_history (creator_id, currency, locked, dry_run);
