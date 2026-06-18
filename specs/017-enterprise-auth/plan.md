# Plan: Spec 017 — Enterprise Auth

## Architecture

Korus Admin → `AuthPolicyService` → Keycloak Admin API (LDAP federation + OIDC/SAML brokers).  
Public `GET /api/v1/auth/login-options` drives web UI. JWT pipeline unchanged.

## Deliverables

- Flyway `V041__org_auth_policy.sql`, `organizations.slug`
- `AuthPolicyRepository`, `KeycloakAuthSyncClient`, `AuthPolicyService`
- `GET /v1/auth/login-options`, `GET/PATCH /v1/admin/orgs/{orgId}/auth-policy`
- Admin manifest section «Вход / Identity»
- Web UI: dynamic login + SSO redirect
- `docker/docker-compose.openldap-dev.yml`, `scripts/smoke-ldap-auth.sh`
- Playwright `auth-login-options.spec.ts` in tier `ui-auth`

## Org resolution

1. `?org_slug=`
2. subdomain `{slug}.host`
3. `KORUS_DEFAULT_ORG_ID`
4. single org in DB
