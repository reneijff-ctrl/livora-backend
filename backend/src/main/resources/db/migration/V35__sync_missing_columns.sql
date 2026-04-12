-- V35: Sync missing columns detected by full entity vs schema analysis
-- Missing: creator_profiles.birth_date (java.time.LocalDate -> DATE)
ALTER TABLE creator_profiles ADD COLUMN IF NOT EXISTS birth_date DATE;

-- Missing: rule_fraud_signals.creator_id (Long nullable -> BIGINT)
ALTER TABLE rule_fraud_signals ADD COLUMN IF NOT EXISTS creator_id BIGINT;
