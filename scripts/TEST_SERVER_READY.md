# Когда готов «тестовый запуск сервера» (core-api)

## Минимально работоспособный режим (health без авторизации)

Готов, когда одновременно:

1. Подняты **PostgreSQL (hot)**, **NATS**, **Redis**, **MinIO** — например  
   `.\scripts\dev-infra-up.ps1`
2. Применены миграции (при первом старте **core-api** сам запускает Flyway).
3. Процесс **core-api** слушает порт (по умолчанию **8080**).
4. Ответ успешный:  
   `GET http://localhost:8080/api/v1/health` → JSON со статусом и версией.
5. (Опционально) Готовность БД: **`GET http://localhost:8080/api/v1/health/ready`** → **200** и **`databaseOk: true`** при доступном PostgreSQL; **503**, если БД недоступна.

В этом режиме **логин/защищённые методы** могут не работать без Keycloak.

**Локаль текстов ошибок API** (`ApiError.message`, подписи параметров UUID): по умолчанию русская (**`app.locale=ru`** в **`application.properties`**). Для английских сообщений задайте **`APP_LOCALE=en`** (или **`en-US`**) в окружении процесса **core-api**.

Спецификация **OpenAPI** без авторизации: **`GET .../openapi.json`** (или **`.yaml`**) — см. **`JwtAuthFilter`** (публичные пути).

## Полный тест с JWT (login, защищённые API)

Готов, когда к пунктам выше добавлены:

6. Запущен **Keycloak** с импортированным realm (см. `keycloak/`, compose-сервис `keycloak`):  
   `.\scripts\dev-keycloak-up.ps1`  
   либо `docker compose -f docker/docker-compose.dev-min.yml up -d keycloak`
7. Переменные `KEYCLOAK_ISSUER` / `KEYCLOAK_JWKS_URL` указывают на **localhost:8081** (как в `run-core-api-local.ps1` по умолчанию). Опционально **`KEYCLOAK_AUDIENCE`** / **`keycloak.audience`** — если заданы, **`TokenValidator`** отклоняет токены без совпадающего claim **`aud`**.
8. Для **регистрации в Keycloak** через Admin API задайте при необходимости **`KEYCLOAK_MASTER_USER`** / **`KEYCLOAK_MASTER_PASSWORD`** (realm **master**, клиент **admin-cli**); по умолчанию в скрипте локального запуска — **`admin`** / **`admin`**, как у консоли Keycloak в compose.

Автоматическая проверка цепочки **логин → refresh → `GET /api/v1/admin/ui/manifest` и `GET .../stats` → (опционально logout) → админ-сессия**: **`.\scripts\smoke-auth.ps1`** (после запуска core-api и Keycloak; ответы **`/login`** и **`/refresh`** — поля **`access_token`** / **`refresh_token`** в JSON). По умолчанию после login/refresh вызывается **`POST /api/v1/auth/logout`** с последним **`refresh_token`** и ожидается **204**; отключить: **`-SkipLogout`**. Отзыв идёт в Keycloak (**RFC 7009**); клиент в Keycloak должен разрешать revocation для **`messenger-web`**.

**CORS:** для UI с другого origin задайте **`CORS_ALLOWED_ORIGINS`** (или **`cors.allowed.origins`**) списком через запятую; по умолчанию **`*`**. Preflight **`OPTIONS`** обрабатывается до JWT.

Во всём REST API составные поля в JSON — **snake_case** (например **`user_id`**, **`display_name`**, **`reply_to_msg_id`**, **`database_ok`**). Внутренний обмен по NATS (**JetStream / subjects**) использует отдельные DTO без этого переименования.

**Чаты и группы:** уже есть **`POST /api/v1/chats`** с типами **`p2p`** и **`group`**, участники, баны, сообщения. **Фото/видео:** загрузка **`POST /api/v1/files/upload`**, лимиты и STUN — **`GET /api/v1/media/capabilities`**. **Видеозвонки:** **`POST /api/v1/chats/{chat_id}/conferences`** → в ответе **`join_url`** (Jitsi по умолчанию); для своего сервера задайте **`JITSI_MEET_BASE_URL`**. **Активность:** **`PATCH .../users/me/presence`**, **`POST .../users/me/heartbeat`**.

**Тестовые учётные записи realm `avandocmsg`** (после импорта `keycloak/avandocmsg-realm.json`):

| Логин | Пароль | Назначение |
|-------|--------|------------|
| `csadmin` | `csadmin` | Суперпользователь для проверок API и первичной настройки из UI мессенджера (realm-роль **admin**) |
| `admin` | `admin` | Доп. тестовый администратор realm |

Учётная запись **консоли Keycloak** (не путать с realm): по умолчанию из compose — `admin` / `admin` на порту **8081**.

После логина access token содержит **`realm_access.roles`** (в т.ч. `admin` для учёток выше). В core-api метод **`SecurityContext.isUserInRole("admin")`** проверяет эти realm-роли.

Проверка «админ с JWT»: **`GET /api/v1/admin/session`** с заголовком **`Authorization: Bearer <access_token>`** — ожидается **200** и JSON с ролями; без роли **admin** — **403**.

Встроенная админ-консоль (статика): **`GET http://localhost:8080/admin/`** — **200**, HTML (вход и боковое меню из SPI). Без JWT: **`GET http://localhost:8080/api/v1/admin/console`** — **303** на **`/admin/`**. С Bearer (роль **admin**): **`GET /api/v1/admin/ui/manifest`** — список разделов и поле **`api_version`**; **`GET /api/v1/admin/ui/stats`** — сводка JVM и зависимостей (см. **`README`**).

События аудита: **`GET /api/v1/admin/audit-events?limit=100`**; опционально **`action`**, **`resource_type`** и/или **`resource_id`** (точное совпадение, AND); для ретенции: **`action=message.retention.hot_body_cleared`**, **`resource_type=message`**; сводка прохода по UUID: **`action=message.retention.bulk_cleared`**, **`resource_type=retention_pass`**, **`resource_id=<pass_id>`**.

Опционально (ретенция, см. **`docs/RETENTION_AND_DEEP_ARCHIVE.md`** §9): для «сухого» прогона воркера без мутаций Hot DB/MinIO/NATS/аудита задайте **`RETENTION_DRY_RUN=true`** (по умолчанию **`false`**). Чтобы снизить нагрузку на БД/NATS/MinIO между сообщениями в одном пакетном проходе, задайте **`RETENTION_INTER_MESSAGE_DELAY_MS`** (**`0…60000`**, по умолчанию **`0`** — без паузы). При наличии организации в БД — **`GET /api/v1/admin/organizations/{orgId}/retention`** с тем же Bearer — **200** и JSON эффективной политики (после миграции **`V011`**). Изменение: **`PATCH`** на тот же путь с телом **`UpdateRetentionPolicyRequest`** (в коде) — **200** и обновлённый JSON; аудит **`organization.retention.set`**. Аналогично для чата (миграция **`V012`**): **`GET`/`PATCH /api/v1/admin/chats/{chatId}/retention`**; аудит **`chat.retention.set`**. Отдельный процесс **`RetentionWorker`** (`modules/workers/retention`, **`.\gradlew.bat :modules:workers:retention:run`**) по умолчанию **выключен** (**`RETENTION_WORKER_ENABLED=false`**). При **`RETENTION_WORKER_ENABLED=true`** нужны **`DB_*`**, **`NATS_URL`** и (по умолчанию) MinIO (**`MINIO_*`**); снимки тел можно направить в отдельный бакет (**`RETENTION_MINIO_BUCKET`**) и с префиксом ключей (**`RETENTION_MINIO_OBJECT_PREFIX`**). По умолчанию воркер пытается создать бакет ретенции (**`RETENTION_ENSURE_MINIO_BUCKET`**); отключите, если бакет создаётся заранее. Иначе задайте **`RETENTION_REQUIRE_MINIO=false`** только на тестовом стенде — тогда очистка **`messages.content`** возможна **без** снимка в бакете. После миграции **`V013`** по умолчанию ведётся лог **`retention_hot_body_applied`** (**`RETENTION_USE_APPLIED_LOG`**); до применения миграции можно **`RETENTION_USE_APPLIED_LOG=false`**. По умолчанию пишется **`audit_events`** (**`RETENTION_AUDIT_ENABLED`**, действие **`message.retention.hot_body_cleared`**). Дополнительно к построчному аудиту: **`RETENTION_BULK_AUDIT_MIN_CLEARED`** (порог для одной строки **`message.retention.bulk_cleared`**). Пропуск дублирующего **`putObject`**, если объект уже в MinIO: **`RETENTION_SKIP_SNAPSHOT_IF_DEEP_EXISTS`**. Метрики процесса: **`RETENTION_METRICS_PORT`** (**`1…65535`** → **`/metrics`**). Кандидаты на очистку тела не выбираются при **`legal_hold=true`** в эффективной политике org/чата (см. документ §9). Пакетный проход только для **`jdbc:postgresql:`**; см. **`docs/RETENTION_AND_DEEP_ARCHIVE.md`** §9.

## Полная готовность (автоматический контроль)

Считаем стек «готовым к работе клиента», когда проходят все проверки скрипта **`.\scripts\smoke-ready.ps1`** (по умолчанию достаточно **`database_ok`** в **`health/ready`**):

1. **`GET /api/v1/health`**
2. **`GET /api/v1/health/ready`** → **`database_ok`**: **true** (поля **`redis_ok`** / **`nats_ok`** смотрите в JSON; при необходимости жёсткой проверки: **`.\scripts\smoke-ready.ps1 -StrictDependencies`** — скрипт завершится ошибкой, если Redis или NATS не в порядке)
3. **`GET /api/v1/media/capabilities`** (без JWT)
4. **`GET /api/v1/admin/console`** — **303** на **`/admin/`** (без JWT; см. **`scripts/lib/SmokeAdminUi.ps1`**)
5. Логин **`POST /api/v1/auth/login`** и **`GET /api/v1/admin/session`** с Bearer (учётка с realm-ролью **admin**, например **csadmin**); скрипт **`smoke-ready.ps1`** дополнительно проверяет **`GET /admin/`**, **`GET /api/v1/admin/ui/manifest`** и **`GET /api/v1/admin/ui/stats`**

Дополнительно (ручной или отдельный сценарий): загрузка файла **`POST /api/v1/files/upload`** с **`Content-Type: multipart/form-data`**, поле **`file`** — для медиа в UI; сырой поток (**`application/octet-stream`**) по-прежнему поддерживается. Лимит загрузки один для обоих режимов: **`media.max.upload.bytes`** и поле **`max_upload_bytes`** в capabilities.

**Файлы — общий доступ в чате (не E2EE):** второй пользователь в том же чате может открыть **`GET /api/v1/files/{file_id}`** и **`GET .../download`**, если в истории есть **не-E2EE** сообщение, в **`content`** которого (после trim) передан UUID файла; при **взаимной блокировке** с отправителем такого сообщения доступа нет. Удаление файла — только **владелец** (`DELETE`).

**Метрики:** на **`GET /api/v1/metrics/prometheus`** (как в готовности стенда, если смотрите observability) должны появляться рост **`api_denied_file_access_total`** / **`api_denied_message_send_total`** при соответствующих **403** (например запрет отправки в P2P при блоке), а также **`api_invalid_uuid_parameter_total`** при **400** из-за неверного или пустого UUID в path/query (см. **`UuidParams`** / **`InvalidUuidParameterExceptionMapper`**).

## Полностью в Docker

Готов, когда образ собран и сервис `core-api` в `docker/docker-compose.dev-min.yml` в статусе **Up**, порт **8080** проброшен, зависимости (postgres, redis, nats, **minio**, **keycloak**) подняты; у **core-api** заданы переменные **MinIO** и при необходимости **MEDIA_** / **JITSI_** / **WEBRTC_** (см. блок `environment` в compose).

### Полный стек одной командой (`docker-compose.full-server.yml`)

Файл **`docker/docker-compose.full-server.yml`** поднимает ту же инфраструктуру, что и **dev-min**, плюс **ws-gateway**, **message-pipeline** и **retention-worker** **без** флагов **`--profile web`** / **`--profile retention`**. Сеть по умолчанию та же (**`korus_messenger_dev_min`**), что у **dev-min** — **`korus-web`** с **`docker-compose.attach.yml`** подключается так же.

- Запуск: **`.\scripts\full-stack-up.ps1 -Build`** или **`./scripts/full-stack-up.sh --build`**
- Остановка: **`.\scripts\full-stack-down.ps1`** или **`./scripts/full-stack-down.sh`**
- Админка: **`http://localhost:8080/admin/`**; тестовые пользователи realm **avandocmsg** с ролью **admin**: **`csadmin`** / **`csadmin`**, **`admin`** / **`admin`**

### Веб-клиент (`korus-web/`) и WebSocket

- Стек **UI + балансировщик**: каталог **`korus-web/`**, **`docker compose up --build`** (см. **`korus-web/README.md`**). **core-api** с хоста или из того же compose — через **`WEB_CLIENT_API_UPSTREAM`** (по умолчанию **`http://host.docker.internal:8080`**). Если **core-api** и **ws-gateway** уже в сети **`docker-compose.dev-min.yml`** (имя сети по умолчанию **`korus_messenger_dev_min`**), можно подключить **korus-web** к той же сети: **`korus-web/docker-compose.attach.yml`** (при необходимости **`KORUS_DEV_MIN_NETWORK`** в **`.env`**); с хоста: **`.\scripts\korus-web-up.ps1 -Attach`** или **`./scripts/korus-web-up.sh --attach`** (**`-Build` / `--build`** при необходимости). Подробности: **`korus-web/README.md`**.
- **Профиль `web`** в **`docker/docker-compose.dev-min.yml`**: **ws-gateway** (публикация **`8082:8081`**, чтобы не пересекаться с Keycloak на хосте **8081**) и воркер **message-pipeline** (образ **`docker/Dockerfile.message-pipeline`**, **`NATS_JETSTREAM=false`** в compose — согласовано с **core-api** по умолчанию). Команда:  
  `docker compose -f docker/docker-compose.dev-min.yml --profile web up -d`  
  Для **`korus-web`** в **`.env`**: **`KORUS_WS_GATEWAY_PORT=8082`** (см. **`korus-web/.env.example`**). Если для **core-api** включён **JetStream** (**`NATS_JETSTREAM=true`**), задайте то же для **message-pipeline**.

Проверка lb/UI: **`.\scripts\smoke-korus-web.ps1`**, **`scripts\smoke-korus-web.cmd`** или **`./scripts/smoke-korus-web.sh`** (по умолчанию **`http://localhost:9088`**; **`-WebBaseUrl`** / **`--url`** при другом порте; **`-Help`** / **`--help`**; **`-CheckApi`** / **`--check-api`** — **`GET …/api/v1/health`** через прокси web-client). Поднять профиль **`web`**: **`.\scripts\dev-web-stack-up.ps1`** или **`./scripts/dev-web-stack-up.sh`** (**`-Build`** / **`--build`**; **`-SkipEnsure`** / **`SKIP_KORUS_ENSURE=1`** — без проверки/установки окружения; см. **`README.md`**).


Опционально воркер ретенции (**`RetentionWorker`**, образ **`docker/Dockerfile.retention-worker`**):  
`docker compose -f docker/docker-compose.dev-min.yml --profile retention up -d retention-worker`  
(профиль **`retention`**, иначе сервис не поднимается). Нужны уже запущенные **postgres-hot**, **nats**, **minio**; переменные **`RETENTION_*`** при необходимости задайте в compose или override-файлом. В compose для сервиса задана пауза **`RETENTION_INITIAL_DELAY_SECONDS=30`** перед первым сканом.

После поднятия профиля **`retention`** и заданного в контейнере **`RETENTION_METRICS_PORT`** (как в compose: **`/health`** и **`/metrics`** на проброшенном порту) можно проверить готовность HTTP с хоста одной командой: **`.\scripts\smoke-retention-worker.ps1`** (по умолчанию **`http://localhost:9192`**, переопределение: **`-BaseUrl`**). Статус **`healthy`** у сервиса **`retention-worker`** в Compose зависит от того же порта метрик (**`RETENTION_METRICS_PORT` > 0** внутри контейнера).

**Планирование (укрупнённо):** текущий фокус по ретенции в репозитории — **фаза A**; граница «завершения A» и фазы **B/C** — **`docs/RETENTION_AND_DEEP_ARCHIVE.md` §10 и §13**. Индексы **Flyway `V014`/`V015`** (аудит и кандидаты hot-body) — **`docs/db/FLYWAY_AND_SCHEMA.md`**.

## Автоматические тесты в CI (GitHub)

В репозитории включён workflow **`.github/workflows/ci.yml`**: на push в **`main`** / **`master`** / **`develop`**, на pull request и вручную (**Actions → CI → Run workflow**) выполняется **`./gradlew buildIntegrity`** (все модули: компиляция, тесты, **`jar`**; Ubuntu, JDK **25**, проверка целостности Gradle Wrapper). Dependabot (**`.github/dependabot.yml`**) — зависимости Gradle и экшены. Подробности: **`docs/CI_AND_REPO_HYGIENE.md`**.

---

Запуск на хосте одной командой после инфраструктуры:  
`.\scripts\run-core-api-local.ps1`

Docker-стенд из **корня репозитория**: **`.\scripts\start.ps1 min`** (встроены проверка окружения, переменные **`KORUS_*`**, **2** попытки **`docker compose`**), **`.\scripts\clean.ps1 all`**, **`.\scripts\create-stand.ps1 min`**, **`.\scripts\install-environment.ps1`** — см. **`README.md`**.

Тихая установка **JDK / Git / Docker**, если чего-то нет: **Windows** — **`.\scripts\install-env-silent.ps1`** (**`-Quiet`**, **`install-env-silent.cmd`**) или **`.\scripts\install-environment.ps1 -SilentInstall -Quiet`**; **Debian/Ubuntu** — **`./scripts/install-env-silent.sh --quiet`** или **`./scripts/install-environment.sh --silent-install --quiet`**.

Общие проверки админ-консоли для PowerShell-скриптов: **`scripts\lib\SmokeAdminUi.ps1`** (функции **`Test-SmokeAdminConsoleRedirect`**, **`Test-SmokeAdminStaticPage`**, **`Test-SmokeAdminUiApi`**) — подключается из **`smoke-ready.ps1`** и **`smoke-auth.ps1`**.
