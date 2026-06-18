# Runtime Gate Report - Automated snapshot

**Date**: 2026-06-18 (QEMU outer gate, Windows dev host)

## Health

| URL | Status |
|-----|--------|
| http://127.0.0.1:19088/ | 200 |
| http://127.0.0.1:18080/api/v1/health | 200 |

## Playwright

- Inner tiers: **all pass** (`playwright-dev-loop.ps1 -Tier all-inner`, 2026-06-18)
- **Outer gate**: **50 passed**, 1 skipped — `playwright-dev-loop.ps1 -Tier full` (2026-06-18)
- Env: `PLAYWRIGHT_BASE_URL=http://127.0.0.1:19088`, `KORUS_API_URL=http://127.0.0.1:18080`

## QEMU smokes (spec 010 Phase A)

| Smoke | Result |
|-------|--------|
| `smoke-turn-qemu.ps1 -GuestOnly` | OK |
| `smoke-push-worker-qemu.ps1` | OK |
| `smoke-bot-api.ps1` | OK |
| `smoke-export-gdpr-fulfillment.ps1` | OK |

## Operator sign-off

- **Engineering gate**: green on QEMU (2026-06-18)
- **Stage/prod sign-off**: blocked until Sep 2026+ (no host)
- **RTC waiver**: mesh controls tested with mocked RTCPeerConnection (no live TURN relay in Playwright)
