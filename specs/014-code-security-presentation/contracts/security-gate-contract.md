# Contract: Security gate (spec 014)

## buildIntegrity (CI / local)

MUST pass:

| Step | Command / task |
|------|----------------|
| Bundle parity | `checkBundleParity` |
| Python registry | `checkCompetitorRegistry` |
| Code style | `spotlessCheck` (ratchet `origin/main`) |
| npm audit | `checkNpmAudit` — 0 high/critical |
| Benchmark | `:modules:core-api:benchmark` |
| Build all modules | subproject `build` |

Exit code **0** required for merge.

## security-gate.ps1 (QEMU optional)

When API `http://127.0.0.1:18080/health` OK:

| Smoke | Required |
|-------|----------|
| `smoke-security-headers.ps1` | yes |
| `smoke-rate-limit.ps1` | yes |
| `audit-timing.ps1` | yes, delta ≤ 5% |

`-SkipQemuSmokes`: buildIntegrity only.

## Presentation (PRES phase)

- Korus radar `reg` score MUST NOT exceed **3** until certificate exists.
- §24 engineering controls MUST NOT be labeled «Частично» after PRES-3.
