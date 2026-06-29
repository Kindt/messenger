# Pilot tier load tests (k6)

Baseline scripts for **spec 025 FR-067 / FR-148** and validation gate (20% peak TBD). Run against **QEMU forwarded API** (`127.0.0.1:18080`) or CI/guest stack — not stage FQDN until Sep 2026+.

См. также [`scripts/perf/README.md`](../perf/README.md) (baseline JSON) и [spec 025 quickstart](../specs/025-resource-network-performance-optimization/quickstart.md).

## Prerequisites

Install [k6](https://k6.io/docs/get-started/installation/):

```powershell
choco install k6
# or: winget install k6 --source winget
```

## Environment

| Variable | Default | Purpose |
|----------|---------|---------|
| `K6_BASE_URL` | `http://127.0.0.1:18080` | API root |
| `K6_VUS` | `10` (health) / `5` (rest) | Virtual users |
| `K6_DURATION` | `30s` | Test duration |
| `K6_USER` / `K6_PASS` | `smoke_user_a` / `smokepass123` | Auth for `pilot-rest.js` |

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

## Scripts

| File | Purpose | Default thresholds |
|------|---------|-------------------|
| `pilot-health.js` | `GET /api/v1/health` sustained load | `http_req_failed < 1%`, `p(95) < 500ms` |
| `pilot-rest.js` | login + `GET /chats?limit=20` | `http_req_failed < 5%`, `p(95) < 800ms` |

Override thresholds only with baseline note in `scripts/perf/baselines/` (PR checklist).

## Output

k6 prints `http_req_duration` p95. Save JSON for baseline contract §10.2:

```powershell
k6 run --out json=deploy/qemu/run/k6-pilot-baseline.json scripts/load/pilot-health.js
```

Or use the host runner (k6 or PowerShell fallback):

```powershell
.\scripts\run-k6-qemu-baseline.ps1
```

## L4 VM acceptance (spec 029)

L4 load wave (`smoke-vm-acceptance-matrix.ps1` W5):

| Step | Script | Evidence |
|------|--------|----------|
| k6 health 30s | `run-k6-qemu-baseline.ps1` | `deploy/qemu/run/k6-pilot-baseline.json` |
| WS soak | `load-ws-soak-qemu.ps1` | guest log |
| SFU scaffold | `run-sfu-participant-load-qemu.ps1` | `SCaffold` in VMA manifest only |

Threshold caps: `scripts/perf/baselines/qemu-l4-k6-thresholds.json` (p95 max 500ms lab; 2× regression vs stored baseline).

Committed summary for presentations: `docs/benchmarks/qemu-nt-baseline-2026-06-15.json` (regenerate locally into `deploy/qemu/run/` when re-running).

Stage prep checklist (T601–T607 artifacts — deferred ops):

```powershell
.\scripts\stage-readiness-checklist.ps1
```

Target peak for prod-full sizing: see product deck [`docs/index.html`](../docs/index.html) (Tech §4) — run at **20%** of documented peak when matrix is finalized.

## CI / PR

- Full k6 matrix — **not** in default `ci.yml` (needs live API). Use guest lab or manual QEMU.
- PR template: `.github/PULL_REQUEST_TEMPLATE.md` — perf/k6 checklist when touching API hot-path.
