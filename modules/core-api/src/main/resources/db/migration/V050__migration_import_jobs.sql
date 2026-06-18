-- Spec 022 US9: migration import job registry (Telegram export JSON v1 scaffold).

CREATE TABLE migration_import_jobs (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id       UUID NOT NULL,
    source       VARCHAR(32) NOT NULL,
    status       VARCHAR(16) NOT NULL DEFAULT 'pending',
    config_json  JSONB,
    result_json  JSONB,
    created_by   UUID REFERENCES users (id),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT migration_import_jobs_status_check CHECK (
        status IN ('pending', 'running', 'completed', 'failed', 'cancelled')
    )
);

CREATE INDEX idx_migration_import_jobs_org ON migration_import_jobs (org_id, created_at DESC);
