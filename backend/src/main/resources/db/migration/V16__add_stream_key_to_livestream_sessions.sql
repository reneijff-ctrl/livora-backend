ALTER TABLE livestream_sessions
    ADD COLUMN IF NOT EXISTS stream_key VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_livestream_sessions_stream_key
    ON livestream_sessions(stream_key);