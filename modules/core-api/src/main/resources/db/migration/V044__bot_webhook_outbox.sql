-- Spec 019 US7: persisted bot webhook delivery retries
CREATE TABLE IF NOT EXISTS bot_webhook_outbox (
    id              UUID PRIMARY KEY,
    bot_id          UUID,
    chat_id         UUID NOT NULL,
    event_id        VARCHAR(128) NOT NULL,
    webhook_url     VARCHAR(2048) NOT NULL,
    payload_json    JSONB NOT NULL,
    attempts        INT NOT NULL DEFAULT 0,
    next_retry_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    status          VARCHAR(32) NOT NULL DEFAULT 'pending',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_bot_webhook_outbox_retry
    ON bot_webhook_outbox (status, next_retry_at)
    WHERE status = 'pending';

CREATE UNIQUE INDEX IF NOT EXISTS uq_bot_webhook_outbox_dedup
    ON bot_webhook_outbox (event_id, webhook_url);
