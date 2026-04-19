-- Add forensic and Stripe reference fields to chargeback_cases so it can fully replace legacy_chargebacks.
-- All columns are nullable to remain backward-compatible with existing rows.

ALTER TABLE chargeback_cases
    ADD COLUMN IF NOT EXISTS stripe_charge_id            VARCHAR,
    ADD COLUMN IF NOT EXISTS stripe_dispute_id           VARCHAR,
    ADD COLUMN IF NOT EXISTS transaction_id              UUID,
    ADD COLUMN IF NOT EXISTS creator_id                  BIGINT,
    ADD COLUMN IF NOT EXISTS device_fingerprint          VARCHAR,
    ADD COLUMN IF NOT EXISTS ip_address                  VARCHAR,
    ADD COLUMN IF NOT EXISTS payment_method_fingerprint  VARCHAR,
    ADD COLUMN IF NOT EXISTS payment_method_brand        VARCHAR,
    ADD COLUMN IF NOT EXISTS payment_method_last4        VARCHAR;
