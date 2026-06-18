# LDAP / AD customer handoff (IT)

Checklist for customer IdP team when enabling org auth via Korus admin (`PATCH /api/v1/admin/orgs/{orgId}/auth-policy`).

## LDAP / Active Directory

1. Provide **connection URL** (`ldap://` or `ldaps://`), **users DN**, **bind DN**.
2. Store bind password in vault; pass **env name** as `secret_ref` on provider (e.g. `LDAP_BIND_PASSWORD`).
3. After apply: Keycloak Admin → User federation → **Sync users**.
4. Assign realm role `admin` manually for org admins (v1).

## OIDC / SAML

1. Register Keycloak broker redirect URLs from runbook `docs/runbooks/sso-keycloak-federation.md`.
2. Provide discovery URL (OIDC) or SSO URL + entity ID (SAML).
3. Store `client_secret` in vault; reference via `secret_ref`.

## Verification

- `GET /api/v1/auth/login-options?org_slug=<slug>` — methods visible to users.
- `scripts/smoke-ldap-auth.sh` on QEMU stack (`127.0.0.1:18080`).
