CREATE TABLE totp_backup_codes (
    id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    user_id     BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    code_hash   VARCHAR(255) NOT NULL,
    used        BOOLEAN     NOT NULL DEFAULT FALSE,
    used_at     TIMESTAMP,
    created_at  TIMESTAMP   NOT NULL DEFAULT now(),
    PRIMARY KEY (id)
);

CREATE INDEX idx_totp_backup_user_id ON totp_backup_codes (user_id);
