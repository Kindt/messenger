-- Политика ретенции на чат (override поверх org + дефолтов платформы; см. docs/RETENTION_AND_DEEP_ARCHIVE.md).

CREATE TABLE chat_retention_policy (
    chat_id                     UUID PRIMARY KEY REFERENCES chats(id) ON DELETE CASCADE,
    hot_message_body_max_age_days INT,
    hot_metadata_min_age_days   INT,
    archive_metadata_enabled    BOOLEAN NOT NULL DEFAULT true,
    deep_archive_enabled        BOOLEAN NOT NULL DEFAULT true,
    legal_hold                  BOOLEAN NOT NULL DEFAULT false,
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by                  UUID REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX idx_chat_retention_policy_updated ON chat_retention_policy (updated_at DESC);
