# Минимальные ресурсы QEMU-ВМ

Оценка по `docker/docker-compose.full-server.yml` (14 сервисов) и `korus-web/docker-compose.yml` (web-a, web-b, lb). Рекомендуемые **mem_limit** — overlay [`docker/docker-compose.resource-limits.yml`](../../docker/docker-compose.resource-limits.yml) (PS-0.1); pilot-stack подключает его автоматически.

## Docker resource limits (PS-0.1)

| Сервис | mem_limit | cpus | JVM |
|--------|-----------|------|-----|
| postgres-hot | 512m | 1.0 | — |
| solr | 896m | 1.0 | — |
| core-api | 768m | 2.0 | `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport` |
| ws-gateway | 384m | 1.0 | same |
| message-pipeline | 384m | 1.0 | same |
| keycloak (prod) | 512m | — | `-Xmx256m` in keycloak-prod overlay |

Проверка на guest: `docker stats --no-stream`. OOM одного Java-сервиса не должен убивать postgres.

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

## korus-integrations (spec 014 — bots/plugins)

| Компонент | Контейнеры (типично) | RAM min, МБ |
|-----------|----------------------|-------------|
| integrations gateway | lb / router nginx | 64 |
| connector-runtime | 1 | 512 |
| bridges (subset) | 2–4 × 384 | 768–1536 |
| demo sidecars | 3–5 × 128 | 384–640 |
| mocks (vitrine-light) | wiremock ×2 | 256 |
| Ubuntu + Docker | | 600 |
| Запас | | 512 |
| **Итого RAM (vitrine-light)** | | **~3–4 ГБ** |
| **Рекомендуется RAM** | | **8 ГБ** (`config.ps1`) |
| **vitrine-heavy** (+ Bitrix, Jira) | +2–4 ГБ | **12 ГБ** |

| Ресурс | Минимум | Рекомендуется |
|--------|---------|---------------|
| vCPU | 2 | 2 |
| Диск | 24 ГБ | 32 ГБ |

> IP: **192.168.76.30**; host debug port **18190**. См. [`specs/014-bot-plugin-platform/design/qemu-integrations-vm.md`](../../specs/014-bot-plugin-platform/design/qemu-integrations-vm.md).

## Хост Windows (три ВМ + QEMU + WHPX)

| | 2 VM (server+web) | 3 VM full sizing | 3 VM `KORUS_QEMU_THREE_VM=1` |
|---|-------------------|------------------|-------------------------------|
| RAM гостей | 10 + 3 = **13 ГБ** | 10 + 3 + 8 = **21 ГБ** | 8 + 2.5 + 4 = **14.5 ГБ** |
| RAM хоста (guest + ~2 ГБ reserve) | **16 ГБ** OK | **24 ГБ+** | **16–20 ГБ** OK |
| vCPU хоста | 6 | 8–10 | 8 |

**WHPX и 3-я ВМ:** если `korus-integrations` падает сразу после старта (stderr `WHPX: Failed to get performance monitoring features` — часто **не** fatal), чаще виновата **нехватка RAM хоста**, а не сломанный WHPX. QEMU не может выделить `-m 8192` при уже запущенных server (10240) + web (3072).

**Решение (16–20 ГБ хост):**

```powershell
.\scripts\qemu-down.ps1
$env:KORUS_QEMU_THREE_VM = "1"
.\scripts\qemu-up.ps1 -KeepDisks -WithIntegrations
```

Профиль: server **8192** + web **2560** + integrations **4096** MB. Автоматически включается при `qemu-up -WithIntegrations`.

**Только integrations** (server/web уже up): `.\scripts\qemu-integrations-up.ps1` — preflight RAM; при fail — перезапуск стека с `KORUS_QEMU_THREE_VM=1`.

**Не использовать** `$env:KORUS_QEMU_FORCE_TCG=1` для integrations (сборка Docker ~×10 медленнее). Только если WHPX реально недоступен на хосте.

Override: `$env:KORUS_QEMU_INTEGRATIONS_MEMORY_MB=4096`, `$env:KORUS_QEMU_SERVER_MEMORY_MB=8192`.

## Текущие значения в `config.ps1`

| ВМ | RAM (solo / 2-VM) | RAM (3-VM profile) | vCPU | Диск |
|----|-------------------|--------------------|------|------|
| server | 10240 МБ | **8192** (`KORUS_QEMU_THREE_VM=1`) | 4 | 40 ГБ |
| web | 3072 МБ | **2560** | 1 | 24 ГБ |
| integrations | 8192 МБ solo; **4096** с peers | **4096** | 2 | 32 ГБ |
| integrations heavy | 12288 МБ (`KORUS_QEMU_INTEGRATIONS_HEAVY=1`) | — | 2 | 32 ГБ |
| integrations min | 3584 МБ (retry floor) | — | 2 | 32 ГБ |

Ранее **2048 МБ на ВМ** недостаточно для server (14 контейнеров + сборка): отсюда `no space left on device`, зависший `docker ps`, connection reset на API.

## Сокращение (не в минимальном full-server)

Для уменьшения server-ВМ используйте **pilot profile** (`pilot-stack-up.sh` → full-server + pilot-overrides, Ansible `korus_deploy_profile: pilot`) — см. раздел «korus-server (pilot profile)» выше и [`docs/DEV_STACK_PROFILES.md`](../../docs/DEV_STACK_PROFILES.md). В штатном `full-server` все 14 сервисов поднимаются с `--profile full`.
