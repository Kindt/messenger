# Pilot tier load tests (k6 skeleton)

Baseline scripts for **W2-A4** / validation gate (20% peak TBD). Run against QEMU forwarded API or stage.

## Prerequisites

Install [k6](https://k6.io/docs/get-started/installation/):

```powershell
choco install k6
# or: winget install k6 --source winget
```

## QEMU (Windows host)

```powershell
$env:K6_BASE_URL = "http://127.0.0.1:18080"
k6 run scripts/load/pilot-health.js
```

With auth smoke user (if seeded on stack):

```powershell
$env:K6_BASE_URL = "http://127.0.0.1:18080"
$env:K6_USER = "smoke_user_a"
$env:K6_PASS = "smokepass123"
k6 run scripts/load/pilot-rest.js
```

## Output

k6 prints `http_req_duration` p95. Save JSON for §10.2 baseline:

```powershell
k6 run --out json=deploy/qemu/run/k6-pilot-baseline.json scripts/load/pilot-health.js
```

Or use the host runner (k6 or PowerShell fallback):

```powershell
.\scripts\run-k6-qemu-baseline.ps1
```

Committed summary for presentations: `docs/benchmarks/qemu-nt-baseline-2026-06-15.json` (regenerate locally into `deploy/qemu/run/` when re-running).

Stage prep checklist (T601–T607 artifacts):

```powershell
.\scripts\stage-readiness-checklist.ps1
```

## Scripts

| File | Purpose |
|------|---------|
| `pilot-health.js` | `GET /api/v1/health` sustained load |
| `pilot-rest.js` | login + chat list (authenticated REST) |

Target peak for prod-full sizing: see product deck [`docs/index.html`](../docs/index.html) (Tech §4) — run at **20%** of documented peak when matrix is finalized.
