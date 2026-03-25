CREATE TABLE creator_applications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    stripe_account_id VARCHAR(255),
    terms_accepted BOOLEAN NOT NULL DEFAULT FALSE,
    age_verified BOOLEAN NOT NULL DEFAULT FALSE,
    submitted_at TIMESTAMP NOT NULL DEFAULT NOW(),
    approved_at TIMESTAMP,
    review_notes TEXT,
    CONSTRAINT fk_creator_applications_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_creator_applications_user_id ON creator_applications(user_id);
