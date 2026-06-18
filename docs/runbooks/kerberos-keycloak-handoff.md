# Keycloak Kerberos / SPNEGO handoff (spec 019)

Engineering scaffold: `scripts/keycloak-enable-kerberos.sh`. Kerberos runs in **Keycloak**, not core-api.

## Steps

1. Provision KDC/keytab for service principal `HTTP/keycloak.example.com@REALM`.
2. Keycloak Admin → Realm → Authentication → Browser flow: add **Kerberos** execution.
3. User federation → Kerberos: realm, KDC, keytab path.
4. Map AD groups to Keycloak roles (manual; auto mapper — spec 017 v2).
5. QEMU: use mock KDC lab or skip until customer AD (spec 015).

## Out of scope

- Desktop client SPNEGO (no desktop client in repo)
- Direct Kerberos in core-api
