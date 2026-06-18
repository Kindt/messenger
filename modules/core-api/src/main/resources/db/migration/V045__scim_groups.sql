-- SCIM 2.0 Groups provisioning (org-scoped)
CREATE TABLE scim_groups (
    id              UUID PRIMARY KEY,
    org_id          UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    display_name    VARCHAR(256) NOT NULL,
    external_id     VARCHAR(256),
    members_json    TEXT NOT NULL DEFAULT '[]',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_scim_groups_org ON scim_groups (org_id);

CREATE UNIQUE INDEX uq_scim_groups_org_external_id
    ON scim_groups (org_id, external_id) WHERE external_id IS NOT NULL;
