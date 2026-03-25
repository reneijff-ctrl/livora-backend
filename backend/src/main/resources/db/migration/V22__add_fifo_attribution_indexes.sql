-- Migration to add indexes required for efficient FIFO token attribution for chargeback reversals

-- Index on creator_earnings_history to optimize retrieval of earnings by spender (user_id) chronologically
CREATE INDEX idx_creator_earnings_user_created
ON creator_earnings_history(user_id, created_at);

-- Optimized index on wallet_transactions to improve chronological sorting for users
CREATE INDEX idx_wallet_transactions_user_created
ON wallet_transactions(user_id, created_at);
