-- Spec 022: org IP allowlist (app-layer enforcement scaffold).

CREATE TABLE org_ip_allowlist (
    org_id        UUID PRIMARY KEY,
    enabled       BOOLEAN NOT NULL DEFAULT false,
    allowed_cidrs TEXT NOT NULL DEFAULT '',
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
