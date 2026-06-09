# Playwright E2E (spec 003)

Browser tests for web-client critical paths and parity-matrix domains.

## Prerequisites

- Full server on `:8080`, korus-web on `:9088` (attach mode or two-host)
- Smoke users: `./scripts/keycloak-ensure-smoke-users.sh`

## Run

```bash
cd tests/e2e-web
npm ci
npx playwright install chromium
KORUS_WEB_URL=http://127.0.0.1:9088 KORUS_API_URL=http://127.0.0.1:8080 npm test
```

## Specs (parity-matrix coverage)

| File | Domains |
|------|---------|
| `auth-session.spec.ts` | auth |
| `messaging-critical.spec.ts` | chats, messages |
| `messaging-group.spec.ts` | chats (3-user group) |
| `messaging-actions.spec.ts` | messages (send, reply) |
| `files-export.spec.ts` | files, export |
| `contacts-search.spec.ts` | contacts, search |
| `profile-settings.spec.ts` | users, blocks, devices |
| `conference-rtc.spec.ts` | conference, media |
| `e2ee-capabilities.spec.ts` | crypto/e2ee |

Selectors use stable `data-testid` (`auth-submit`, `message-composer`) and `#u` / `#p` — not locale-specific button labels.

## CI

Optional job in `.github/workflows/deploy-messaging-smoke.yml` (nightly / manual); does not block PR `buildIntegrity`.
