# Security baseline



## Verification matrix (spec 014)



| Control | CI (`buildIntegrity`) | QEMU smokes | Prod ops |

|---------|----------------------|-------------|----------|

| Security headers | via unit tests | `smoke-security-headers.ps1` | TLS terminate + headers |

| Rate limit auth | unit tests | `smoke-rate-limit.ps1` | Redis + env |

| Timing normalization | `:core-api:benchmark` | `audit-timing.ps1` | `SECURITY_TIMING_NORMALIZATION_MIN_MS` |

| Spotless / npm audit | `spotlessCheck` ratchet, `checkNpmAudit` | — | — |

| WebSocket origin | unit tests | Playwright / manual | `WS_ALLOWED_ORIGINS` |

| E2EE MLS prod | unit + Playwright | `ui-e2ee` tier | sign-off packet 8/8 |

| Bot webhook HMAC | unit tests | optional header | `BOT_WEBHOOK_HMAC_SECRET` env |



Orchestrator: `scripts/security-gate.ps1` (build + optional QEMU).



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

- Bot API: `BotRateLimitFilter` (`BOT_RATE_LIMIT_PER_MIN`)



## CORS



- `CORS_ALLOWED_ORIGINS` (comma-separated; avoid `*` with credentials in production)



## WebSocket



- `WS_ALLOWED_ORIGINS` on ws-gateway; rejected origins close with code `4001`



## Smoke



- `scripts/security-gate.ps1` — buildIntegrity + optional QEMU smokes (spec 014)

- `scripts/smoke-security-headers.ps1`

- `scripts/smoke-rate-limit.ps1`

- `scripts/audit-timing.ps1` — chat + user probes (S2-1)



See also `docs/SECURITY_AUDIT.md` for timing audit notes.

