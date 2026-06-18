# Tasks: Spec 017 — Enterprise Auth

- [x] T01701 design `docs/plans/2026-06-17-enterprise-auth-admin-design.md`
- [x] T01702 Flyway `V041__org_auth_policy` + org `slug`
- [x] T01703 `AuthPolicyRepository` + H2 tests
- [x] T01704 `KeycloakAdminClient` LDAP/OIDC/SAML upsert
- [x] T01705 `AuthPolicyService` + apply + login-options resolver
- [x] T01706 `GET /v1/auth/login-options` (public)
- [x] T01707 `GET/PATCH /v1/admin/orgs/{orgId}/auth-policy`
- [x] T01708 Admin UI section «Вход / Identity»
- [x] T01709 Web UI dynamic login + OIDC redirect
- [x] T01710 OpenLDAP docker overlay + `smoke-ldap-auth.sh`
- [x] T01711 Playwright tier `ui-auth`
- [x] T01712 Runbooks + PRODUCT_PRESENTATION §12.3
