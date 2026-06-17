# Plugin lifecycle ops (spec 014)

Production model vs QEMU dev vitrine. Status: eng scaffolding 2026-06-17.

## L0 at scale (5000+ FAQ bots per org)

| Concern | Design | Status |
|---------|--------|--------|
| Containers | **None** — L0 runs inline in core-api (`L0MenuHandler`) | Done |
| Storage | 1 row per bot in `plugin_instances.config_json` (JSONB) | Done |
| Lookup | `UNIQUE (org_id, bot_name)` + `findInstanceByOrgAndBotName` | Done |
| Invoke path | O(1) by `instance_id` or org+bot_name, not full table scan | Done |
| Admin list | `GET /instances?limit=&offset=` + `total_count` | Done |
| Chat @mention | Wire message pipeline -> `invokeByOrgBot` | **Backlog** |
| Config size | Keep menu JSON small; large assets via links/files | Policy |
| Cross-org | 5000 per org OK; millions total = PG sizing + indexes | Ops |

5000 L0 bots = 5000 DB rows, not 5000 Docker processes.

## Hot-plug one bridge (integrations VM)

| Action | Command | Status |
|--------|---------|--------|
| Build one bridge | `.\scripts\qemu-guest-compose.ps1 -Guest integrations -Action build -Services onec-bridge` | Done |
| Start one bridge | `... -Action up -Services onec-bridge` | Done |
| Stop one bridge | `... -Action down -Services onec-bridge` | Done |
| Sync selective | `.\scripts\qemu-sync-integrations.ps1 -BuildOnly -OneAtATime -Services onec-bridge` | Done |
| Admin disable (no container stop) | `PATCH /v1/admin/plugins/instances/{id}` `{"enabled":false}` | Done |
| Admin UI button -> compose | Ansible/cell agent on integrations node | **Backlog** |
| Heartbeat stale -> degraded | NATS `$SVC.heartbeat.*` on server | Partial |

## Per-org / tenant compose sets

| Mode | When | Status |
|------|------|--------|
| `vitrine-light` / `vitrine-heavy` | Presales profile overlays | Done (compose files) |
| Full dev vitrine | QEMU first bootstrap | Dev only |
| Per-tenant bridge set | Cells / Ansible `cell-integrations.yml` | **Backlog Sep 2026+** |
| Env `KORUS_INTEGRATIONS_PROFILE` | Select overlay on guest | **Backlog** |

## Guest task lock (no parallel plink)

One long SSH task per VM role. Lock file: `deploy/qemu/run/guest-task-{integrations|server|web}.lock`

| Script | Guest lock |
|--------|------------|
| `qemu-sync-integrations.ps1` | integrations |
| `qemu-sync-api-core.ps1` | server |
| `qemu-sync-workers.ps1` | server |
| `qemu-guest-compose.ps1` | integrations or server |

Second run while lock active -> **error**. Stale lock cleared when PID dead or max age (integrations 120m, server 90m). Override: `-ForceLock`.

Same pattern as `Korus-QemuRedeployLock.ps1` for server/web redeploy.

## Typical ops flows

### Enable L0 FAQ bot (no integrations VM)

1. Admin `POST /v1/admin/plugins/instances/l0` with menu JSON
2. Users @bot in chat (when mention routing wired)

### Enable 1C bridge

1. `qemu-guest-compose.ps1 -Action build -Services onec-bridge`
2. `qemu-guest-compose.ps1 -Action up -Services onec-bridge,mock-apis,connector-runtime`
3. Admin create L2 instance with `runtime_endpoint` -> bridge URL
4. Disable: `PATCH enabled:false` (instant) or `compose down onec-bridge`

### Update one bridge image

1. `qemu-sync-integrations.ps1 -BuildOnly -OneAtATime -Services exchange-bridge`
2. Or guest-compose build + restart
