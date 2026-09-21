-- Session timelines group event_logs by session_id (column added in V4,
-- first populated by post view/dwell logging). This index backs queries like:
--
-- event_logs WHERE session_id = ? ORDER BY created_at
--
-- (pairing impression rows with dwell rows of the same usage session,
-- per-session aggregates for analytics).

CREATE INDEX idx_event_logs_session_created_at ON event_logs (session_id, created_at);
