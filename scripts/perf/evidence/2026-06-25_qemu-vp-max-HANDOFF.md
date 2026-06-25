# QEMU VP-max handoff (2026-06-25)

**Статус:** остановлено, продолжить позже.

## Что сделано в сессии

- Скрипты `scripts/perf/run-qemu-vp-max.ps1`, `run-k6-docker.ps1`, `run-qemu-metrics-probe.ps1`, `run-qemu-pg-explain.ps1`, `run-qemu-observability-lab.ps1`, `run-qemu-fix-core-api.ps1`, `run-qemu-guest-diag.ps1`, `lib/Invoke-QemuServerGuest.ps1`
- `docker/docker-compose.qemu-lab.yml` — `KORUS_PRODUCT_ADDONS=` для lab
- Ansible `korus-server.env.j2` — явный пустой `KORUS_PRODUCT_ADDONS`
- Evidence: `scripts/perf/evidence/2026-06-25_qemu-vp-max.json` (FAIL на API health)

## Блокер на guest (server VM)

1. **core-api unhealthy** — HTTP 500, Jersey: `Resource configuration is not modifiable` при init `ServletContainer`
2. Ранее также был **fail-fast** `validateProductionSecrets` при непустом `KORUS_PRODUCT_ADDONS` + dev secrets (regression profile / stale `.env.korus-server`)

Guest diag: `.\scripts\perf\run-qemu-guest-diag.ps1`

## Следующий шаг (при возобновлении)

1. Синк репо на guest: `.\scripts\qemu-redeploy.ps1 -ServerOnly -Force` (подтянуть `docker-compose.qemu-lab.yml` + ansible env)
2. Починить core-api на guest: `.\scripts\perf\run-qemu-fix-core-api.ps1` (очистить addons + recreate)
3. Если 500 Jersey остаётся — код: Jersey/Tomcat init в `CoreApiComposition` (не `ApplicationHandler` — не компилируется в текущем Jersey API)
4. Rebuild image если нужен кодфикс: `.\scripts\qemu-sync-api-core.ps1 -NoCache`
5. Прогон: `.\scripts\perf\run-qemu-vp-max.ps1 -SkipBuildIntegrity -WriteEvidence`

## QEMU / host

- Push: только `.\scripts\git-push.ps1`
- Lab gate (когда API 200): `.\scripts\perf\run-qemu-lab-gate.ps1 -WriteBaseline`

## Не закоммичено (локально)

См. `git status` — perf scripts, ansible template, docker-compose.qemu-lab.yml, handoff.
