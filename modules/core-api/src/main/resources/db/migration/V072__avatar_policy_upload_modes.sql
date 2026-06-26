-- Spec 068 W9: org upload policy modes (disabled, ldap_only).

ALTER TABLE organizations DROP CONSTRAINT IF EXISTS chk_organizations_avatar_policy;

ALTER TABLE organizations
    ADD CONSTRAINT chk_organizations_avatar_policy
    CHECK (avatar_policy IN ('visible', 'org_hidden', 'disabled', 'ldap_only'));
