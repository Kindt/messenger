# Performance baselines (spec 025, FR-169)

Снимки метрик до/после waves. Формат — [`specs/025-resource-network-performance-optimization/contracts/baseline-metrics-contract.md`](../specs/025-resource-network-performance-optimization/contracts/baseline-metrics-contract.md).

Quickstart: [`specs/025-resource-network-performance-optimization/quickstart.md`](../specs/025-resource-network-performance-optimization/quickstart.md).

## CI static gate (FR-175 / SC-024)

Без live stack — в `.github/workflows/ci.yml`:

```bash
cd modules/web-client/webui-build
npm run build:assets
npm run test:first-load   # budget: critical HTML+CSS+app.bundle.js (default 450 KB)
```

Override budget: `KORUS_FIRST_LOAD_MAX_KB=500 npm run test:first-load`.

## k6 API load (FR-067 / FR-148)

Pilot scripts: [`scripts/load/README.md`](../load/README.md). Baseline capture on QEMU:

```powershell
$env:K6_BASE_URL = "http://127.0.0.1:18080"
k6 run --out json=deploy/qemu/run/k6-pilot-baseline.json scripts/load/pilot-health.js
```

## Lab gate (spec 025 T128 / Gate-0)

One-shot on host (QEMU forwards `:18080` / `:19088`):

```powershell
.\scripts\perf\run-vp00-static.ps1          # VP-00-01…08 static only
.\scripts\perf\run-qemu-lab-gate.ps1 -WriteBaseline   # VP-00 + buildIntegrity + health + smoke + first-load
```

Optional k6: omit `-SkipK6` when `k6` is on PATH.

## Quick capture (QEMU lab)

```powershell
# From repo root (QEMU stack up: 18080 / 19088)
$sha = git rev-parse --short HEAD
$dir = "scripts/perf/baselines"
New-Item -ItemType Directory -Force -Path $dir | Out-Null
$webBytes = (curl -s -o NUL -w "%{size_download}" http://127.0.0.1:19088/)
$healthSec = (curl -s -o NUL -w "%{time_total}" http://127.0.0.1:18080/api/v1/health)
@"
{"wave":"A","phase":"pre","git_sha":"$sha","environment":"qemu-lab","metrics":{"web_first_load_kb":$([math]::Round($webBytes/1kb,1)),"api_health_p99_ms":$([math]::Round([double]$healthSec*1000,1))}}
"@ | Set-Content "$dir/$(Get-Date -Format yyyy-MM-dd)_wave-A_pre.json" -Encoding UTF8
```

Расширяйте `metrics` по мере появления Prometheus/NATS tooling в Wave D.

## Committed baselines

| File | Notes |
|------|-------|
| `baselines/2026-06-25_wave-*_*.json` | Wave A–E snapshots |
| `baselines/2026-06-25_wave-E_qemu-lab.json` | QEMU lab gate capture (health, first-load bundle) |

PR checklist: `.github/PULL_REQUEST_TEMPLATE.md`.
