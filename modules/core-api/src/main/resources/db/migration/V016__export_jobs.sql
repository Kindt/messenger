CREATE TABLE export_jobs (
    id              UUID PRIMARY KEY,
    chat_id         UUID NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    requested_by    UUID NOT NULL REFERENCES users(id),
    status          VARCHAR(32) NOT NULL DEFAULT 'queued',
    output_path     TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ,
    CONSTRAINT export_jobs_status_check CHECK (
        status IN ('queued', 'processing', 'export_v1', 'stub_written', 'export_failed')
    )
);

CREATE INDEX idx_export_jobs_chat_created ON export_jobs (chat_id, created_at DESC);
