-- Spec 068 W8: bot plugin + org logo avatar file references.

ALTER TABLE plugin_instances
    ADD COLUMN IF NOT EXISTS avatar_file_id UUID REFERENCES file_metadata(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_plugin_instances_avatar_file_id
    ON plugin_instances (avatar_file_id) WHERE avatar_file_id IS NOT NULL;

ALTER TABLE organizations
    ADD COLUMN IF NOT EXISTS logo_file_id UUID REFERENCES file_metadata(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_organizations_logo_file_id
    ON organizations (logo_file_id) WHERE logo_file_id IS NOT NULL;
