-- Directory sync run history (LDAP → users)
CREATE TABLE directory_sync_runs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id          UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    status          VARCHAR(32) NOT NULL,
    users_upserted  INT NOT NULL DEFAULT 0,
    error           VARCHAR(2000),
    started_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at     TIMESTAMPTZ
);

CREATE INDEX idx_directory_sync_runs_org_started ON directory_sync_runs (org_id, started_at DESC);

ALTER TABLE users ADD COLUMN IF NOT EXISTS external_id VARCHAR(256);

CREATE UNIQUE INDEX IF NOT EXISTS uq_users_org_external_id
    ON users (org_id, external_id) WHERE external_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_users_email_lower ON users (lower(email)) WHERE email IS NOT NULL;
