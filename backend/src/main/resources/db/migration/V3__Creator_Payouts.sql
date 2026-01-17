CREATE TABLE stripe_accounts (
    id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    stripe_account_id VARCHAR(255) UNIQUE,
    onboarding_completed BOOLEAN NOT NULL DEFAULT FALSE,
    charges_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    payouts_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_stripe_account_user ON stripe_accounts(user_id);
CREATE INDEX idx_stripe_account_id ON stripe_accounts(stripe_account_id);

CREATE TABLE payouts (
    id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    token_amount BIGINT NOT NULL,
    eur_amount DECIMAL(19, 2) NOT NULL,
    status VARCHAR(50) NOT NULL,
    stripe_transfer_id VARCHAR(255),
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_payout_user ON payouts(user_id);
CREATE INDEX idx_payout_status ON payouts(status);
