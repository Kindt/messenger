-- Spec 013 L5 scaffold: DVR recording URL + moderation flags for live sessions (§28.5 hooks)

ALTER TABLE live_sessions
    ADD COLUMN IF NOT EXISTS dvr_playlist_url VARCHAR(2048),
    ADD COLUMN IF NOT EXISTS dvr_started_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS moderation_state VARCHAR(32) NOT NULL DEFAULT 'open';

CREATE TABLE IF NOT EXISTS live_session_moderation_events (
    id              UUID PRIMARY KEY,
    session_id      UUID NOT NULL REFERENCES live_sessions(id) ON DELETE CASCADE,
    actor_user_id   UUID NOT NULL,
    action          VARCHAR(64) NOT NULL,
    reason          VARCHAR(512),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_live_session_moderation_session
    ON live_session_moderation_events(session_id, created_at DESC);
