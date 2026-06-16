# QEMU: VM `korus-integrations` (bots / plugins)

**Spec:** 014-bot-plugin-platform  
**Status:** `approved` (topology); **orchestration** (`qemu-up` 3-VM) — Phase P0 eng  
**Date:** 2026-06-15

---

## Role

Отдельная ВМ для **всего исполнения** ботов-плагинов (spec 014).  
**Не** на `korus-server` (ядро) и **не** на `korus-web` (UI).

| На integrations VM | Не на integrations VM |
|--------------------|------------------------|
| connector-runtime (C) | core-api, PG, Redis, NATS |
| *-bridge (B): exchange, storage, 1c, naumen, ocr, ai | bot-delivery-worker (остаётся на server) |
| demo sidecars (D): echo-*, bitrix-php, … | web-client / nginx |
| `_mock-servers`, vitrine compose | integration-router **logic** в core-api (только HTTP client) |

**korus-server** хранит: `plugin_instances`, admin, **router** (маршрутизация HTTP → `192.168.76.30`).

---

## Network (LAN 192.168.76.0/24)

| VM | IP | Host ports (debug) |
|----|-----|-------------------|
| `korus-server` | 192.168.76.10 | 18080, 18082, … (unchanged) |
| `korus-web` | 192.168.76.20 | 19088 |
| **`korus-integrations`** | **192.168.76.30** | **18190** → integrations lb/gateway :8090 (optional) |

```
Browser → 19088 (web) → 18080 (server API)
              │
              ▼
         core-api router ──HTTP──► 192.168.76.30:8xxx (bridges/sidecars)
              │
              ▼
         bot-delivery (webhooks to external bots / customer sidecars on LAN)
```

**Customer sidecar (D)** на сервере заказчика: router на **server** вызывает их URL по HTTPS (не обязательно integrations VM).

---

## Sizing (initial)

| Resource | Value | Notes |
|----------|-------|-------|
| RAM | **8192 MB** | vitrine-light; **12288 MB** для vitrine-heavy |
| vCPU | **2** | bridges + 3–5 sidecars |
| Disk | **32 GB** | images, mock data, Bitrix heavy optional |

См. [`deploy/qemu/RESOURCES.md`](../../../deploy/qemu/RESOURCES.md) §korus-integrations (after update).

**Host RAM:** +8–12 ГБ к текущим ~13 ГБ для трёх ВМ одновременно.

---

## Compose profiles (guest path)

```
/mnt/korus/integrations/
  docker-compose.integrations.yml      # base bridges + gateway
  docker-compose.vitrine.yml           # profile vitrine-light
  docker-compose.vitrine-heavy.yml     # + Bitrix box, Jira
```

Ansible (planned): `playbooks/qemu-integrations-local.yml`, inventory `inventory/qemu/integrations.yml`.

---

## Hot-plug on integrations VM

1. `docker compose ... up -d naumen-bridge` **на integrations guest** — без рестарта server.
2. Bridge heartbeat → NATS на **server** (`$SVC.heartbeat.naumen-bridge`).
3. Router на server: если heartbeat stale → degraded message в чат.
4. Admin **disable instance** — мгновенно на server (без stop контейнера на integrations).

Stop integrations VM целиком → все плагины degraded; **чаты и API server работают**.

---

## Prod parity (spec 011 Cells)

| QEMU | Production |
|------|------------|
| korus-integrations VM | **integrations node** или dedicated Cell worker host |
| LAN .30 | private subnet / security group server→integrations |
| compose profiles | Ansible `cell-integrations.yml` per tenant optional |

---

## Implementation checklist (P0)

- [ ] `config.ps1`: `KorusQemuIntegrationsIp`, memory constants
- [ ] `images/integrations-dev.qcow2`, cloud-init `integrations/`
- [ ] `qemu-up.ps1` optional 3rd VM (`-WithIntegrations` or default on for spec 014 dev)
- [ ] `smoke-plugin-*.ps1` target `.30` via guest SSH or host tunnel `18190`
- [ ] Firewall: server → integrations :8000–8100 allow

---

*Decision D12 — separate QEMU VM for plugins (2026-06-15).*
