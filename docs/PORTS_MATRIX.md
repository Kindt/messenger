# Ports Matrix

Single source of truth for commonly used host ports across local run modes.

## Local Docker (host)

| Mode | Service | Host port | Notes |
|---|---|---:|---|
| `full-server` | core-api | `8080` | Health: `/api/v1/health`, admin UI: `/admin/` |
| `full-server` | keycloak | `8081` | Realm `avandocmsg` |
| `full-server` | ws-gateway | `8082` | WebSocket endpoint `/ws` |
| `full-server` | retention-worker | `9192` | Metrics/health (`/metrics`, `/health`) |
| `full-server` | export-replay-worker | `9193` | Metrics/health (`/metrics`, `/health`) |
| `full-server` | push-worker | `9194` | Health (`/health`) |
| `dev-min --profile web` | ws-gateway | `8082` | WebSocket endpoint `/ws` |
| `dev-min --profile web` | push-worker | `9193` | Health (`/health`) |

## QEMU two-VM (host forwards)

| VM role | Service (inside VM) | Host port | Notes |
|---|---|---:|---|
| `korus-server` | core-api | `18080` | Forward to guest `8080` |
| `korus-server` | ws-gateway | `18082` | Forward to guest `8081` |
| `korus-server` | keycloak | `18081` | Forward to guest `8080` |
| `korus-web` | web LB | `19088` | Forward to guest `9088` |
| `korus-server` | SSH | `12221` | `ssh korus@127.0.0.1 -p 12221` |
| `korus-web` | SSH | `12222` | `ssh korus@127.0.0.1 -p 12222` |

## Quick checks

- Full-server API: `curl http://127.0.0.1:8080/api/v1/health`
- Full-server push-worker: `curl http://127.0.0.1:9194/health`
- QEMU API: `curl http://127.0.0.1:18080/api/v1/health`
- QEMU UI: `curl http://127.0.0.1:19088/`
