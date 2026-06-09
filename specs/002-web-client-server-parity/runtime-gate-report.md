# Runtime Gate Report — Operator Template

Use this template when running optional browser / Playwright gates after engineering closure (spec 002 + spec 004 US5).

## Execution Context

- **Date**: YYYY-MM-DD
- **Operator**: _full name_
- **Environment**: local Docker / QEMU / stage (describe stack)
- **Build/Commit**: _branch + short SHA_
- **Playwright base URL**: `KORUS_WEB_URL` (default `http://127.0.0.1:9088`)
- **API URL**: `KORUS_API_URL` (default `http://127.0.0.1:8080`)

## Pre-check

- [ ] `./scripts/full-stack-up.sh --build` (or QEMU `scripts/qemu-up.ps1`) green
- [ ] `./scripts/korus-web-up.sh --attach --build` (if UI not in stack)
- [ ] `./scripts/keycloak-ensure-smoke-users.sh` (smoke users for Playwright)
- [ ] `./scripts/wait-stack-ready.sh` passes

## US5 Playwright parity gate (T110–T115)

Run from repo root:

```bash
cd tests/e2e-web
npm ci
npx playwright install chromium
KORUS_WEB_URL=http://127.0.0.1:9088 KORUS_API_URL=http://127.0.0.1:8080 npx playwright test
```

| Spec | Parity domain | Status | Notes |
|------|---------------|--------|-------|
| auth-session.spec.ts | auth (login/logout) | pass / fail / waiver | logout via `[data-testid=logout]` |
| messaging-critical.spec.ts | chats, messages | pass / fail | |
| messaging-group.spec.ts | groups | pass / fail | |
| messaging-actions.spec.ts | messages actions | pass / fail | |
| files-export.spec.ts | files, export | pass / fail | API upload + DOM `[data-testid=file-attach-input]` |
| contacts-search.spec.ts | contacts, search | pass / fail | |
| profile-settings.spec.ts | users, blocks, devices | pass / fail | `/me/devices` register |
| conference-rtc.spec.ts | conference, media | pass / waiver | full RTC UI waiver without TURN — see HANDOFF.md T022 |
| e2ee-capabilities.spec.ts | crypto | pass / skip | browser MLS after US7 |

- **Overall Playwright status**: `passed` / `failed` / `waived`
- **Evidence**: attach `playwright-report/` or CI artifact link
- **CI reference**: `.github/workflows/deploy-messaging-smoke.yml` (optional job, `continue-on-error`)

## T010 — Messaging parity smoke

- **Status**: `passed` / `failed`
- **Scenarios covered**: send/edit/reply/reaction/pin/forward/delete
- **Evidence**: `scripts/smoke-web-parity-api.ps1`, Playwright messaging specs

## T016 — File/export parity smoke

- **Status**: `passed` / `failed`
- **Scenarios covered**: API upload/download/export; DOM file attach when stack has export worker
- **Evidence**: `scripts/smoke-web-parity-api.ps1`, `files-export.spec.ts`

## T022 — Realtime/call parity smoke

- **Status**: `passed` / `waived`
- **Scenarios covered**: WS `rtc_signal`; conference create API; call panel toggle UI
- **Waiver**: accept/hangup/mic/cam/participants without local TURN — document in Notes
- **Evidence**: `scripts/smoke-web-parity-ws.ps1`, `conference-rtc.spec.ts`

## Final Decision

- **Operational sign-off**: `approved` / `blocked`
- **Blocking items**: _list or none_
- **Follow-up tasks**: _link spec/task IDs if any_

---

## Historical closure (2026-05-24, engineering)

Tasks: `T010`, `T016`, `T022` (spec 002). Engineering closure without live stack: unit/static tests + smoke script readiness. Optional operator re-run documented above.
