CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL
);

CREATE INDEX idx_user_email ON users(email);

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    token VARCHAR(255) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL REFERENCES users(id),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    replaced_by_token VARCHAR(255)
);

CREATE INDEX idx_refresh_token ON refresh_tokens(token);

CREATE TABLE subscriptions (
    id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    status VARCHAR(50) NOT NULL,
    stripe_subscription_id VARCHAR(255),
    current_period_start TIMESTAMP WITH TIME ZONE,
    current_period_end TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_subscription_user ON subscriptions(user_id);

CREATE TABLE payments (
    id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    amount DECIMAL(19, 2),
    currency VARCHAR(10),
    stripe_payment_intent_id VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_payment_user ON payments(user_id);

CREATE TABLE webhook_events (
    id VARCHAR(255) PRIMARY KEY,
    type VARCHAR(255) NOT NULL,
    payload TEXT,
    processed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
