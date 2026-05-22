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
| Keycloak | keycloak | 640 | Quarkus `start-dev` |
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

Для уменьшения server-ВМ потребуется отдельный compose-профиль (без Solr/Zoo/archive/части workers) — в штатном `full-server` все перечисленные сервисы поднимаются.
