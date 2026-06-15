-- Bot API MVP: bot accounts, access tokens, per-chat webhook subscriptions.
ALTER TABLE users ADD COLUMN IF NOT EXISTS is_bot BOOLEAN NOT NULL DEFAULT false;

CREATE TABLE IF NOT EXISTS bots (
    id                  UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    owner_id            UUID NOT NULL REFERENCES users(id),
    org_id              UUID REFERENCES organizations(id) ON DELETE SET NULL,
    bot_name            VARCHAR(64) NOT NULL,
    access_token_hash   VARCHAR(64) NOT NULL,
    listen_mode         VARCHAR(16) NOT NULL DEFAULT 'MENTIONS_ONLY',
    default_webhook_url TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_bots_bot_name UNIQUE (bot_name),
    CONSTRAINT chk_bots_listen_mode CHECK (listen_mode IN ('MENTIONS_ONLY', 'READ_ALL'))
);

CREATE INDEX IF NOT EXISTS idx_bots_owner_id ON bots (owner_id);

ALTER TABLE bot_webhook_subscriptions ADD COLUMN IF NOT EXISTS bot_id UUID REFERENCES bots(id) ON DELETE CASCADE;

CREATE UNIQUE INDEX IF NOT EXISTS uq_bot_webhook_sub_bot_chat
    ON bot_webhook_subscriptions (bot_id, chat_id)
    WHERE bot_id IS NOT NULL;
