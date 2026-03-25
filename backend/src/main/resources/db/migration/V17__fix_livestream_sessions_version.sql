UPDATE livestream_sessions
SET version = 0
WHERE version IS NULL;

ALTER TABLE livestream_sessions
ALTER COLUMN version SET DEFAULT 0;
