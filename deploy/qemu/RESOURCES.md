# Минимальные ресурсы QEMU-ВМ

Оценка по `docker/docker-compose.full-server.yml` (14 сервисов) и `korus-web/docker-compose.yml` (web-a, web-b, lb). В репозитории **нет** `deploy.resources.limits` — цифры по типичным idle/dev-минимумам и наблюдениям на стенде.

## korus-server (full-server)

| Компонент | Контейнер | RAM min, МБ | Примечание |
|-----------|-----------|-------------|------------|
| PostgreSQL hot | postgres-hot | 256 | `shared_buffers` по умолчанию |
| PostgreSQL archive | postgres-archive | 256 | отдельная БД |
| Redis | redis | 64 | alpine |
| NATS JetStream | nats | 192 | растёт с данными |
| MinIO | minio | 256 | объекты, экспорт |
| ZooKeeper | zoo | 256 | для Solr |
| Solr | solr | 768 | JVM, самый тяжёлый инфра-сервис |
| Keycloak | keycloak | 640 | Quarkus `start-dev` (QEMU/CI) |
| core-api | core-api | 512 | JRE 25, Jetty |
| ws-gateway | ws-gateway | 256 | JRE |
| message-pipeline | message-pipeline | 256 | JRE |
| push-worker | push-worker | 256 | JRE |
| export-replay-worker | export-replay-worker | 256 | JRE |
| retention-worker | retention-worker | 256 | JRE |
| **Сумма контейнеров** | | **~4 680** | |
| Ubuntu + Docker Engine | | 800 | |
| Запас 15–20 % (пики JVM/GC) | | 900 | |
| **Итого RAM (runtime)** | | **~6,4 ГБ** | жёсткий минимум |
| **Рекомендуется RAM** | | **10 ГБ** | стабильный dev |
| **Пик `docker compose build`** | | **+2–4 ГБ** | параллельный Gradle (`-Xmx1024m` в `gradle.properties`, несколько модулей) |

| Ресурс | Минимум | Рекомендуется |
|--------|---------|---------------|
| vCPU | 2 | 4 (сборка) / 2 (только runtime) |
| Диск | 20 ГБ | 40 ГБ (образы + тома + однократная сборка) |

## korus-server (pilot profile, FR-OPT-01)

Compose: `docker/docker-compose.full-server.yml` + `docker-compose.pilot-overrides.yml` + `docker-compose.keycloak-prod.yml`. Без Solr/ZK; поиск — SQL fallback.

| Компонент | Контейнер | RAM min, МБ | Примечание |
|-----------|-----------|-------------|------------|
| PostgreSQL hot | postgres-hot | 256 | единственная БД |
| Redis | redis | 64 | |
| NATS | nats | 128 | JetStream optional |
| MinIO | minio | 256 | |
| Keycloak | keycloak | **256–384** | `start --optimized`, `-Xmx256m`, `mem_limit=512m` |
| core-api | core-api | 512 | `DB_POOL_SIZE=15`, `SEARCH_MODE=sql` |
| ws-gateway | ws-gateway | 256 | |
| message-pipeline | message-pipeline | 256 | |
| **Сумма контейнеров (core)** | **8** | **~2 200** | + optional profiles |
| Ubuntu + Docker Engine | | 800 | |
| Запас 15–20 % | | 500 | |
| **Итого RAM (runtime, idle)** | | **~3,5 ГБ** | контейнеры |
| **Рекомендуется RAM (guest)** | | **12–16 ГБ** | §10.2.1 Pilot target; запас под пики и сборку |

| Ресурс | Минимум | Рекомендуется (Pilot) |
|--------|---------|------------------------|
| vCPU | 4 | **8** |
| Диск | 20 ГБ | 40 ГБ |

**Wave 1 gate (2026-06-15):** pilot stack на server guest — RAM guest **~1.6 GiB used / 9.7 GiB** при idle; Keycloak prod RSS **~335 MiB** (`mem_limit=512m`, warm-up 120s); smokes + Playwright `api` green.

Optional compose profiles: `push`, `retention`, `compliance` (archiver/deep/indexer/export-replay), `archive` (postgres-archive).

- **QEMU deploy profile:** по умолчанию `standard` (full-server); pilot — явный override `korus_deploy_profile: pilot` в inventory. См. [`docs/DEV_STACK_PROFILES.md`](../../docs/DEV_STACK_PROFILES.md).

## Keycloak: dev vs prod mode (FR-OPT-02)

| Режим | Command | RAM target | Когда |
|-------|---------|------------|-------|
| **Dev** | `start-dev --import-realm` | ~640 MB | QEMU full-server, CI |
| **Pilot prod** | `start --optimized` + import | **256–512 MB** (heap `-Xmx256m`, `mem_limit=512m`) | `docker-compose.keycloak-prod.yml`, ≤10k RU |
| **Standard HA** | 2× `start` + external DB | 2×512 MB | ≥50k RU (вне scope Wave 1) |

> Строка §10.2 ТЗ «Keycloak 8 GB» — sizing production HA, не dev/pilot. См. сноску в `docs/PRODUCT_PRESENTATION.md` §10.2.1.

## korus-web

| Компонент | Контейнер | RAM min, МБ |
|-----------|-----------|-------------|
| web-client | web-a, web-b | 384 × 2 = 768 |
| nginx lb | lb | 64 |
| Ubuntu + Docker | | 600 |
| Запас | | 400 |
| **Итого RAM (runtime)** | | **~1,8 ГБ** |
| **Рекомендуется RAM** | | **3 ГБ** |

| Ресурс | Минимум | Рекомендуется |
|--------|---------|---------------|
| vCPU | 1 | 2 |
| Диск | 10 ГБ | 24 ГБ |

## Хост Windows (обе ВМ + QEMU)

| | Минимум | Рекомендуется |
|---|---------|---------------|
| RAM гостей | 6 + 2 ≈ **8 ГБ** | 10 + 3 = **13 ГБ** |
| RAM хоста (гости + ~10 % QEMU) | **9 ГБ** | **16 ГБ** |
| vCPU хоста | 4 | 6–8 |
| Диск (образы qcow2 + repo.tgz) | 50 ГБ | 80 ГБ |

## Текущие значения в `config.ps1`

| ВМ | RAM | vCPU | Диск |
|----|-----|------|------|
| server | 10240 МБ | 2 | 40 ГБ |
| web | 3072 МБ | 1 | 24 ГБ |

Ранее **2048 МБ на ВМ** недостаточно для server (14 контейнеров + сборка): отсюда `no space left on device`, зависший `docker ps`, connection reset на API.

## Сокращение (не в минимальном full-server)

Для уменьшения server-ВМ используйте **pilot profile** (`pilot-stack-up.sh` → full-server + pilot-overrides, Ansible `korus_deploy_profile: pilot`) — см. раздел «korus-server (pilot profile)» выше и [`docs/DEV_STACK_PROFILES.md`](../../docs/DEV_STACK_PROFILES.md). В штатном `full-server` все 14 сервисов поднимаются с `--profile full`.
