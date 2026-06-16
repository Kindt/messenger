-- Spec 014: bot-plugin platform (presets, org policy, instances)

CREATE TABLE IF NOT EXISTS plugin_presets (
    id                  VARCHAR(64) PRIMARY KEY,
    plugin_class        VARCHAR(8) NOT NULL,
    runtime_kind        VARCHAR(16) NOT NULL,
    config_schema_version INT NOT NULL DEFAULT 1,
    capabilities        JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_plugin_presets_class CHECK (plugin_class IN ('L0', 'L1', 'L2', 'L3')),
    CONSTRAINT chk_plugin_presets_runtime CHECK (runtime_kind IN ('config', 'connector', 'bridge', 'sidecar'))
);

CREATE TABLE IF NOT EXISTS org_plugin_policies (
    org_id              UUID PRIMARY KEY REFERENCES organizations(id) ON DELETE CASCADE,
    allowed_preset_ids  JSONB NOT NULL DEFAULT '[]'::jsonb,
    llm_mode            VARCHAR(32) NOT NULL DEFAULT 'on_prem_only',
    ocr_on_prem_only    BOOLEAN NOT NULL DEFAULT true,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_org_plugin_llm_mode CHECK (llm_mode IN ('on_prem_only', 'cloud_allowed', 'hybrid'))
);

CREATE TABLE IF NOT EXISTS plugin_instances (
    id                  UUID PRIMARY KEY,
    org_id              UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    preset_id           VARCHAR(64) NOT NULL REFERENCES plugin_presets(id),
    bot_name            VARCHAR(64) NOT NULL,
    display_name        VARCHAR(128) NOT NULL,
    enabled             BOOLEAN NOT NULL DEFAULT true,
    plugin_class        VARCHAR(8) NOT NULL,
    runtime_endpoint    TEXT,
    config_json         JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_plugin_instances_org_bot UNIQUE (org_id, bot_name),
    CONSTRAINT chk_plugin_instances_class CHECK (plugin_class IN ('L0', 'L1', 'L2', 'L3'))
);

CREATE INDEX IF NOT EXISTS idx_plugin_instances_org ON plugin_instances (org_id);
CREATE INDEX IF NOT EXISTS idx_plugin_instances_enabled ON plugin_instances (org_id, enabled);

INSERT INTO plugin_presets (id, plugin_class, runtime_kind, capabilities)
VALUES
    ('l0-faq-menu', 'L0', 'config', '["inline_menu","links"]'::jsonb),
    ('echo-sidecar', 'L1', 'sidecar', '["echo","slash","buttons"]'::jsonb),
    ('connector-generic', 'L1', 'connector', '["rest_mapping"]'::jsonb)
ON CONFLICT (id) DO NOTHING;
