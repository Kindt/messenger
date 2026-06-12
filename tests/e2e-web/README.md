# Playwright E2E (spec 003 / US9 tiers)

Browser tests for web-client critical paths and parity-matrix domains.

## Prerequisites

- QEMU stack up: API `http://127.0.0.1:18080`, UI `http://127.0.0.1:19088`
- Smoke users seeded in server guest (`keycloak-ensure-smoke-users.sh`)

## Inner loop (US9 — fast acceptance)

Stack once, then iterate by tier (host browser against forwarded ports):

```powershell
.\scripts\qemu-up.ps1 -KeepDisks
.\scripts\qemu-stack-wait.ps1

.\scripts\playwright-dev-loop.ps1 -Tier api
.\scripts\playwright-dev-loop.ps1 -Tier ui-messaging
.\scripts\playwright-dev-loop.ps1 -Tier all-inner
```

Tier manifest: `playwright-tiers.json`. Status: `deploy/qemu/run/inner-tier-status.json`.

| Tier | Specs | Typical time |
|------|-------|--------------|
| `api` | profile-settings, e2ee-capabilities, contacts API | &lt; 90s |
| `ui-auth` | auth-session | &lt; 60s |
| `ui-messaging` | messaging-critical, group, actions | &lt; 3m |
| `ui-files` | files-export | &lt; 3m |
| `ui-conference` | conference-rtc | &lt; 2m |
| `ui-e2ee` | e2ee-browser-roundtrip | &lt; 2m |
| `all-inner` | all inner tiers sequentially | &lt; 10m |
| `full` | outer gate only (`qemu-plan-orchestrator`) | ~5m |

On failure read `deploy/qemu/run/plan-failure-analysis.json`.

## Outer gate (once per fix batch)

```powershell
.\scripts\qemu-plan-orchestrator.ps1 -SkipVmUp -MaxAcceptanceMinutes 60
```

Requires all inner tiers green unless `-SkipInnerTierCheck`.

## Manual full suite

```powershell
cd tests/e2e-web
npm ci
$env:PLAYWRIGHT_BASE_URL = "http://127.0.0.1:19088"
$env:KORUS_API_URL = "http://127.0.0.1:18080"
npx playwright test
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
| `e2ee-browser-roundtrip.spec.ts` | browser MLS send when `mls_status=active` |

Server guest needs `MLS_STATUS=active` for MLS specs.

Selectors use stable `data-testid` (`auth-submit`, `message-composer`, `call-panel-toggle`, `chat-export-button`) and `#u` / `#p` — not locale-specific button labels.

## CI

Optional job in `.github/workflows/deploy-messaging-smoke.yml` (nightly / manual); does not block PR `buildIntegrity`.
