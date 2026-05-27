# Playwright E2E (spec 003)

Browser tests for web-client critical paths.

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

## Specs

| File | Coverage |
|------|----------|
| `messaging-critical.spec.ts` | UI login, group chat, send message |
| `messaging-group.spec.ts` | 3-user group visible after API setup |
