# Security baseline

## Headers

`SecurityHeadersFilter` adds when `SECURITY_HEADERS_ENABLED=true` (default):

- `Strict-Transport-Security`
- `X-Content-Type-Options: nosniff`
- `X-Frame-Options: DENY`
- `Referrer-Policy: no-referrer`
- Optional `Content-Security-Policy` via `CSP_POLICY`

## Rate limiting

- Auth: `AuthRateLimiter` (`RATE_LIMIT_AUTH_*`)
- General: `RATE_LIMITER_ENABLED`, `RATE_LIMITER_DEFAULT_CAPACITY`

## CORS

- `CORS_ALLOWED_ORIGINS` (comma-separated; avoid `*` with credentials in production)

## WebSocket

- `WS_ALLOWED_ORIGINS` on ws-gateway; rejected origins close with code `4001`

## Smoke

- `scripts/smoke-security-headers.ps1`
- `scripts/smoke-rate-limit.ps1`
- `scripts/audit-timing.ps1`

See also `docs/SECURITY_AUDIT.md` for timing audit notes.
