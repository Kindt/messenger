# Quickstart: Spec 010 — QEMU Verification

**Prerequisite:** QEMU stack up (`.\scripts\qemu-dev-mode.ps1 -Mode warm`)

---

## US1 — Calls / TURN

```powershell
.\scripts\smoke-turn-qemu.ps1
cd tests/e2e-web
$env:PLAYWRIGHT_BASE_URL="http://127.0.0.1:19088"
$env:KORUS_API_URL="http://127.0.0.1:18080"
npx playwright test specs/conference-rtc.spec.ts
```

## US2 — E2EE

```powershell
.\gradlew.bat :modules:core-api:test --tests "*Mls*"
cd tests/e2e-web
npx playwright test specs/e2ee-capabilities.spec.ts specs/e2ee-browser-roundtrip.spec.ts
```

## US3 — Push

```powershell
.\scripts\smoke-push-worker-qemu.ps1
```

## US4 — Bot API

```powershell
.\scripts\smoke-bot-api.ps1 -BaseUrl http://127.0.0.1:18080
```

## US5 — TLS (QEMU = HTTP only, engineering partial)

```powershell
.\scripts\smoke-tls-redirect.ps1 -SkipTls
# Full US5: stage-tls-smoke.ps1 on real FQDN (Sep 2026+)
```

## Outer gate

```powershell
.\scripts\playwright-dev-loop.ps1 -Tier all-inner
.\scripts\qemu-plan-orchestrator.ps1 -SkipVmUp
```

---

Staging/prod verification: [`docs/review/stage-prod-deploy-runbook.md`](../../docs/review/stage-prod-deploy-runbook.md)
