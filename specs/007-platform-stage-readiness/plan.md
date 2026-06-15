# Plan: Platform & Stage Readiness (007)

**Date:** 2026-06-15  
**Design source:** [`docs/plans/2026-06-15-unfinished-development-plan.md`](../../docs/plans/2026-06-15-unfinished-development-plan.md) §9 (гибрид D)

## Approach

Single spec tracks engineering deliverables (W1–W3 T-P2 + T-P1 prep). Ops deploy/sign-off: [`docs/review/ops-signoff-log.md`](../../docs/review/ops-signoff-log.md).

## Waves executed

| Wave | Track | Status |
|------|-------|--------|
| W1 | T-P2 lock/SSH/ops-signoff 30/30 | ✅ |
| W1 | T-P1 stage prep kit | ✅ |
| W2 | T-P2 wsUrl/remediate/hex edit/guest smoke | ✅ |
| W2 | T-P1 TLS runbook, k6, E2EE packet | ✅ |
| W3 | T-P2 replica lab, register port, push i18n | ✅ |
| W3 | T-P1 stage deploy | ⏳ blocked (no stage host) |
| W4 | Ops sign-offs | ⏳ human gates |

## Verification

Host (no live stack required):

```powershell
.\gradlew.bat buildIntegrity --no-daemon
```

QEMU (when stack up):

```powershell
.\scripts\test-korus-wsurl.ps1
.\scripts\playwright-dev-loop.ps1 -Tier all-inner
# optional outer: .\scripts\qemu-plan-orchestrator.ps1 -SkipVmUp
```

Guest (`korus-server`):

```bash
bash scripts/guest-smoke-platform-w2.sh
```

Load skeleton:

```powershell
$env:K6_BASE_URL = 'http://127.0.0.1:18080'
k6 run scripts/load/pilot-health.js --out json=deploy/qemu/run/k6-pilot-baseline.json
```

## Next ops trigger

When stage host is assigned → `deploy/ansible/inventory/stage/README.md` steps 1–5.
