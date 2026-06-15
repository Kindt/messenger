# Contract: TLS Deploy (US1)

## Inventory variables

| Var | Required when TLS | Description |
|-----|-------------------|-------------|
| korus_tls_enabled | yes | Enables tls role |
| korus_tls_domain | yes | Certificate CN |
| korus_tls_use_letsencrypt | stage | Certbot vs BYO |
| korus_cors_allowed_origins | prod/stage | HTTPS origins for API |

## Vault secrets (vault.yml)

- korus_db_password
- korus_minio_access_key / korus_minio_secret_key
- korus_keycloak_admin_password
- korus_jwt_secret
- korus_coturn_secret (optional)

## Env template fields

**korus-server.env.j2**: `CORS_ALLOWED_ORIGINS`, DB passwords from vault, coturn secret.

**korus-web.env.j2**: `WEB_CLIENT_WS_PUBLIC_URL=wss://{{ domain }}/ws` when TLS enabled.

## Smoke assertions (smoke-tls-redirect.ps1)

1. HTTP GET returns 301/302/308 to HTTPS
2. HTTPS GET returns 200 on web root or health
3. Certificate subject or SAN contains expected domain (or -SkipTls for dev)
