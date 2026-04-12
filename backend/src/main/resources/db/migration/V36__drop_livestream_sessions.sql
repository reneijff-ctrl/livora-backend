-- V36: Remove the legacy V1 livestream_sessions table.
-- All streaming state is now managed exclusively via the unified `streams` table (V2 UUID-based entity).
-- All application code references to LivestreamSession, LivestreamSessionRepository, and LivestreamStatus
-- have been removed in the accompanying Java refactor.

DROP TABLE IF EXISTS livestream_sessions;
