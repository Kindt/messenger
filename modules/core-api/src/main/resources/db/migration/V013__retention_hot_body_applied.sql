-- Лог успешного выноса тела сообщения из Hot DB воркером ретенции (идемпотентность скана; см. docs/RETENTION_AND_DEEP_ARCHIVE.md).

CREATE TABLE retention_hot_body_applied (
    message_id          UUID PRIMARY KEY REFERENCES messages(id) ON DELETE CASCADE,
    applied_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    storage_object_key  TEXT
);

CREATE INDEX idx_retention_hot_body_applied_applied ON retention_hot_body_applied (applied_at DESC);
