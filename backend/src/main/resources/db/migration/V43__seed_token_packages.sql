-- Restore token package seed data lost when V3__Creator_Payouts.sql was deleted.
-- Uses real Stripe Price IDs from the configured test account (sk_test_51SweUZ...).
-- Idempotent: each INSERT is guarded by a NOT EXISTS check on stripe_price_id.

INSERT INTO token_packages (id, name, token_amount, price, currency, stripe_price_id, active)
SELECT gen_random_uuid(), '100 Tokens', 100, 4.99, 'EUR', 'price_1TEA1yFPvuqGTnFG3JBursjE', TRUE
WHERE NOT EXISTS (SELECT 1 FROM token_packages WHERE stripe_price_id = 'price_1TEA1yFPvuqGTnFG3JBursjE');

INSERT INTO token_packages (id, name, token_amount, price, currency, stripe_price_id, active)
SELECT gen_random_uuid(), '500 Tokens', 500, 19.99, 'EUR', 'price_1TEA3nFPvuqGTnFGyUebMm3q', TRUE
WHERE NOT EXISTS (SELECT 1 FROM token_packages WHERE stripe_price_id = 'price_1TEA3nFPvuqGTnFGyUebMm3q');

INSERT INTO token_packages (id, name, token_amount, price, currency, stripe_price_id, active)
SELECT gen_random_uuid(), '1000 Tokens', 1000, 34.99, 'EUR', 'price_1TEA4TFPvuqGTnFGpsIhGaOh', TRUE
WHERE NOT EXISTS (SELECT 1 FROM token_packages WHERE stripe_price_id = 'price_1TEA4TFPvuqGTnFGpsIhGaOh');

INSERT INTO token_packages (id, name, token_amount, price, currency, stripe_price_id, active)
SELECT gen_random_uuid(), '5000 Tokens', 5000, 149.99, 'EUR', 'price_1TEA5HFPvuqGTnFGEfJ6PKd7', TRUE
WHERE NOT EXISTS (SELECT 1 FROM token_packages WHERE stripe_price_id = 'price_1TEA5HFPvuqGTnFGEfJ6PKd7');
