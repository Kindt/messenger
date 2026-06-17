-- PostgreSQL-only: apply after Flyway V039 on stage/prod (spec 011 T01130).
-- Enable per session: SET app.tenant_rls_enabled = 'on'; SET app.current_org_id = '<uuid>';
-- Default (flag off): policies allow all rows — no behaviour change until ops enables RLS.

CREATE OR REPLACE FUNCTION korus_tenant_rls_pass(row_org_id UUID)
RETURNS BOOLEAN
LANGUAGE plpgsql
STABLE
AS $$
BEGIN
    IF coalesce(current_setting('app.tenant_rls_enabled', true), 'off') <> 'on' THEN
        RETURN TRUE;
    END IF;
    IF row_org_id IS NULL THEN
        RETURN TRUE;
    END IF;
    RETURN row_org_id::text = current_setting('app.current_org_id', true);
END;
$$;

ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE users FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS users_tenant_rls ON users;
CREATE POLICY users_tenant_rls ON users
    USING (korus_tenant_rls_pass(org_id))
    WITH CHECK (korus_tenant_rls_pass(org_id));

ALTER TABLE plugin_instances ENABLE ROW LEVEL SECURITY;
ALTER TABLE plugin_instances FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS plugin_instances_tenant_rls ON plugin_instances;
CREATE POLICY plugin_instances_tenant_rls ON plugin_instances
    USING (korus_tenant_rls_pass(org_id))
    WITH CHECK (korus_tenant_rls_pass(org_id));

ALTER TABLE org_plugin_policies ENABLE ROW LEVEL SECURITY;
ALTER TABLE org_plugin_policies FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS org_plugin_policies_tenant_rls ON org_plugin_policies;
CREATE POLICY org_plugin_policies_tenant_rls ON org_plugin_policies
    USING (korus_tenant_rls_pass(org_id))
    WITH CHECK (korus_tenant_rls_pass(org_id));

ALTER TABLE bots ENABLE ROW LEVEL SECURITY;
ALTER TABLE bots FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS bots_tenant_rls ON bots;
CREATE POLICY bots_tenant_rls ON bots
    USING (korus_tenant_rls_pass(org_id))
    WITH CHECK (korus_tenant_rls_pass(org_id));
