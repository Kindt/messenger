-- Политика ретенции на организацию (проект см. docs/RETENTION_AND_DEEP_ARCHIVE.md).
-- NULL в числовых полях = при GET подставляются значения из AppConfig (платформенные дефолты).

CREATE TABLE org_retention_policy (
    org_id                      UUID PRIMARY KEY REFERENCES organizations(id) ON DELETE CASCADE,
    hot_message_body_max_age_days INT,
    hot_metadata_min_age_days   INT,
    archive_metadata_enabled    BOOLEAN NOT NULL DEFAULT true,
    deep_archive_enabled        BOOLEAN NOT NULL DEFAULT true,
    legal_hold                  BOOLEAN NOT NULL DEFAULT false,
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by                  UUID REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX idx_org_retention_policy_updated ON org_retention_policy (updated_at DESC);
