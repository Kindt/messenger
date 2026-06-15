-- Bot API v2: long-poll updates queue, token rotation timestamp.
CREATE TABLE IF NOT EXISTS bot_updates (
    id          BIGSERIAL PRIMARY KEY,
    bot_id      UUID NOT NULL REFERENCES bots(id) ON DELETE CASCADE,
    event_type  VARCHAR(32) NOT NULL,
    payload     JSONB NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_bot_updates_bot_id_id ON bot_updates (bot_id, id);

ALTER TABLE bots ADD COLUMN IF NOT EXISTS token_rotated_at TIMESTAMPTZ;
