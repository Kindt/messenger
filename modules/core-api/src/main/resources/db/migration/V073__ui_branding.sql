-- Wave 3 UI branding: platform + org overrides.
CREATE TABLE platform_ui_branding (
    id BIGINT PRIMARY KEY,
    palette VARCHAR(32) NOT NULL,
    token_overrides JSONB NOT NULL DEFAULT '{}'::jsonb,
    custom_css TEXT,
    brand_title VARCHAR(256),
    demo_skins_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    revision BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE org_ui_branding (
    org_id UUID PRIMARY KEY REFERENCES organizations(id) ON DELETE CASCADE,
    palette VARCHAR(32),
    token_overrides JSONB NOT NULL DEFAULT '{}'::jsonb,
    custom_css TEXT,
    brand_title VARCHAR(256),
    revision BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_org_ui_branding_updated_at ON org_ui_branding (updated_at DESC);

INSERT INTO platform_ui_branding (id, palette, token_overrides, custom_css, brand_title, demo_skins_enabled, revision)
VALUES (1, 'korus', '{}'::jsonb, NULL, NULL, FALSE, 1)
ON CONFLICT (id) DO NOTHING;
