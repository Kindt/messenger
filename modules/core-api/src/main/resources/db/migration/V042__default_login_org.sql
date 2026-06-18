-- Default dev/login org for QEMU and single-tenant stacks (spec 017).
-- Id is stable so KORUS_DEFAULT_ORG_ID in compose can reference it.

INSERT INTO organizations (id, name, slug, created_at)
SELECT '11111111-1111-4111-8111-111111111111', 'Korus Dev', 'dev', now()
WHERE NOT EXISTS (SELECT 1 FROM organizations WHERE slug = 'dev');

INSERT INTO org_auth_policy (org_id, allow_local_password, allow_self_registration, providers_json)
SELECT '11111111-1111-4111-8111-111111111111', true, true, '[]'
WHERE EXISTS (SELECT 1 FROM organizations WHERE id = '11111111-1111-4111-8111-111111111111')
  AND NOT EXISTS (SELECT 1 FROM org_auth_policy WHERE org_id = '11111111-1111-4111-8111-111111111111');
