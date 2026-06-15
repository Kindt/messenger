# Data Model: Deploy & Smoke Entities

**Feature**: 003-docker-ansible-autotest

## Inventory groups

| Group | Hosts | Role |
|-------|-------|------|
| `korus_server` | 1+ | full-server + lan-publish |
| `korus_web` | 0-1 | korus-web nginx stack |
| `localhost` | 1 | CI single-node (`ansible_connection: local`) |

## DeployTarget

| Field | Type | Example |
|-------|------|---------|
| `korus_repo_root` | path | `/home/runner/work/korus_messenger/korus_messenger` |
| `korus_compose_files` | list | `[full-server.yml, lan-publish.yml]` |
| `korus_build_images` | bool | `true` in CI |
| `korus_api_base_url` | url | `http://127.0.0.1:8080` |
| `korus_ws_url` | url | `ws://127.0.0.1:8082/ws` |
| `korus_web_base_url` | url | `http://127.0.0.1:9088` |
| `korus_server_lan_ip` | string | `192.168.76.10` |
| `korus_web_lan_ip` | string | `192.168.76.20` |

## TestUser

| Field | Default |
|-------|---------|
| `username` | `smoke_user_a` / `_b` / `_c` |
| `password` | `smokepass123` |
| `display_name` | `Smoke User A` |

## SmokeScenario (messaging-e2e)

| Step ID | Actor | Action | Assert |
|---------|-------|--------|--------|
| S1 | system | ensure 3 users | login 200 |
| S2 | A | create p2p → B, send ×2 | B messages count ≥ 2 |
| S3 | A | create group [B,C], send ×3 | B,C see 3 |
| S4 | B | reply in group | A sees reply |
| S5 | B | WS connect; A sends | B WS or REST sees msg |
| S6 | B | read messages | A read_receipts includes B |

## SmokeScenario (deploy-acceptance)

Ordered pack: `wait-ready` → `smoke-ready` → `smoke-auth` → `messaging-e2e` → `web-parity-api` → optional `korus-web`.
