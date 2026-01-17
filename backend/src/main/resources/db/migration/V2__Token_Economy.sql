-- V2: Token Economy & Tipping System

CREATE TABLE token_balances (
    id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) UNIQUE,
    balance BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_token_balance_user ON token_balances(user_id);

CREATE TABLE token_packages (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    token_amount BIGINT NOT NULL,
    price DECIMAL(19, 2) NOT NULL,
    currency VARCHAR(10) NOT NULL,
    stripe_price_id VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE creator_earnings (
    id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) UNIQUE,
    total_earned_tokens BIGINT NOT NULL DEFAULT 0,
    available_tokens BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_creator_earning_user ON creator_earnings(user_id);

CREATE TABLE tip_records (
    id UUID PRIMARY KEY,
    viewer_id BIGINT NOT NULL REFERENCES users(id),
    creator_id BIGINT NOT NULL REFERENCES users(id),
    room_id UUID,
    amount BIGINT NOT NULL,
    creator_earning_tokens BIGINT NOT NULL,
    platform_fee_tokens BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_tip_room ON tip_records(room_id);
CREATE INDEX idx_tip_viewer ON tip_records(viewer_id);
CREATE INDEX idx_tip_creator ON tip_records(creator_id);

-- Initial Token Packages
INSERT INTO token_packages (id, name, token_amount, price, currency, stripe_price_id, active)
VALUES 
    ('68415792-508b-4b1d-847e-88481483863a', 'Bronze Pack', 100, 4.99, 'EUR', 'price_bronze_100', TRUE),
    ('7a61d842-834c-4a3d-a417-64973347895b', 'Silver Pack', 250, 9.99, 'EUR', 'price_silver_250', TRUE),
    ('1048b251-4043-4a3c-843c-354315483154', 'Gold Pack', 1000, 34.99, 'EUR', 'price_gold_1000', TRUE);
