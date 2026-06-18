ALTER TABLE organizations ADD COLUMN IF NOT EXISTS slug VARCHAR(64);

CREATE UNIQUE INDEX IF NOT EXISTS idx_organizations_slug ON organizations (slug) WHERE slug IS NOT NULL;

CREATE TABLE org_auth_policy (
    org_id                  UUID PRIMARY KEY REFERENCES organizations(id) ON DELETE CASCADE,
    allow_local_password    BOOLEAN NOT NULL DEFAULT true,
    allow_self_registration BOOLEAN NOT NULL DEFAULT false,
    providers_json          TEXT NOT NULL DEFAULT '[]',
    last_apply_status       VARCHAR(32),
    last_apply_error        TEXT,
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by              UUID REFERENCES users(id)
);
