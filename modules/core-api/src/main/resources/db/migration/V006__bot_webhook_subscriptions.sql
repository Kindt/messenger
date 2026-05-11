-- Optional per-chat bot webhook routing for BotDeliveryWorker (detected at runtime if migrated).
CREATE TABLE IF NOT EXISTS bot_webhook_subscriptions (
    id              BIGSERIAL PRIMARY KEY,
    chat_id         UUID NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    webhook_url     TEXT NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_bot_webhook_subscriptions_chat_id ON bot_webhook_subscriptions (chat_id);
