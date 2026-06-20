-- Platform module admin overrides (spec 021)
CREATE TABLE platform_module_overrides (
    module_id VARCHAR(64) PRIMARY KEY,
    disabled BOOLEAN NOT NULL DEFAULT FALSE,
    override_reason VARCHAR(32),
    force_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by UUID
);

CREATE INDEX idx_platform_module_overrides_updated ON platform_module_overrides (updated_at DESC);
