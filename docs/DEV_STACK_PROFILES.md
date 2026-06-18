# Профили стендов и развёртывания

**Назначение:** deploy-профили (pilot/standard/enterprise), compose-стеки (full-server, pilot, dev-min) и режимы Keycloak.

Связанные документы: [`deploy/ansible/DEPLOY_QUICKSTART.md`](../deploy/ansible/DEPLOY_QUICKSTART.md), [`docs/index.html`](index.html) (product deck, sizing), [`plans/2026-06-15-infra-optimization-design.md`](plans/2026-06-15-infra-optimization-design.md).

> **QEMU (Windows):** локальный dev-стек (`deploy/qemu/`, `scripts/qemu-*.ps1`) **не входит в Git** — только `.gitignore`, файлы могут оставаться на диске разработчика.

---

## 1. Deploy profile (Ansible, server host)

Переменная: `korus_deploy_profile` в Ansible (`deploy/ansible/group_vars/korus_server.yml`, override в `inventory/*/group_vars/`).

| `korus_deploy_profile` | Скрипт | Compose |
|------------------------|--------|---------|
| **`standard`** (default) | `scripts/full-stack-up.sh` | `docker/docker-compose.full-server.yml` |
| **`pilot`** | `scripts/pilot-stack-up.sh` | `full-server.yml` + `pilot-overrides.yml` + `keycloak-prod.yml` |
| **`enterprise`** | `scripts/enterprise-stack-up.sh` | `full-server.yml` + `docker-compose.scale.yml` (+ optional `replica`) |

Compose overlays:

| Overlay | Назначение |
|---------|------------|
| `docker/docker-compose.scale.yml` | 2× message-pipeline, 2× ws-gateway, `API_REPLICAS=2` |
| `docker/docker-compose.replica.yml` | lab read-replica URL (smoke routing) |

---

## 2. Product tier (ТЗ / sizing)

Pilot / Standard / Enterprise — целевой масштаб в production. В **product deck** для TCO используется только **prod full** (`full-server.yml`); см. [`scripts/presentation/METRIC_POLICY.md`](../scripts/presentation/METRIC_POLICY.md).

---

## 3. Compose-стеки (кратко)

| Стек | Файл | Назначение |
|------|------|------------|
| **dev-min** | `docker/docker-compose.dev-min.yml` | Минимальная инфра + опциональный profile `web` |
| **full-server** | `docker/docker-compose.full-server.yml` | Полный продуктовый стек |
| **pilot** | full-server + `pilot-overrides.yml` | Lean pilot без Solr и части workers |

Порты: [`PORTS_MATRIX.md`](PORTS_MATRIX.md).

---

## 4. Keycloak

| Режим | Где |
|-------|-----|
| Dev / compose | `docker-compose.dev-min.yml` — embedded Keycloak |
| Pilot/prod-like | `keycloak-prod.yml` overlay |

Runbook SSO: [`runbooks/sso-keycloak-federation.md`](runbooks/sso-keycloak-federation.md).
