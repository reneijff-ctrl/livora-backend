ALTER TABLE livestream_sessions
ALTER COLUMN viewer_count DROP NOT NULL;

ALTER TABLE livestream_sessions
ALTER COLUMN viewer_count SET DEFAULT 0;
