DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='refresh_tokens' AND column_name='token'
    ) THEN
ALTER TABLE refresh_tokens RENAME COLUMN token TO token_hash;
END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS idx_refresh_token_hash
    ON refresh_tokens(token_hash);

DELETE FROM refresh_tokens;