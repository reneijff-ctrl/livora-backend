ALTER TABLE payments ADD CONSTRAINT uk_payments_stripe_session_id UNIQUE (stripe_session_id);
