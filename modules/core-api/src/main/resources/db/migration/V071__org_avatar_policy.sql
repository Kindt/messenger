-- Spec 068 W9: org avatar policy + per-user hide flag.

ALTER TABLE organizations
    ADD COLUMN IF NOT EXISTS avatar_policy VARCHAR(32) NOT NULL DEFAULT 'visible';

ALTER TABLE organizations
    ADD CONSTRAINT chk_organizations_avatar_policy
    CHECK (avatar_policy IN ('visible', 'org_hidden'));

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS avatar_hidden BOOLEAN NOT NULL DEFAULT false;
