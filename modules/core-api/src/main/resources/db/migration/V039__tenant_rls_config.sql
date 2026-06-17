-- Spec 011 T01130: tenant RLS scaffold (application flag; PG policies in deploy/sql/tenant_rls_policies.sql).
CREATE TABLE IF NOT EXISTS tenant_rls_config (
    id      INT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    enabled BOOLEAN NOT NULL DEFAULT FALSE
);

INSERT INTO tenant_rls_config (id, enabled)
VALUES (1, FALSE)
ON CONFLICT (id) DO NOTHING;
