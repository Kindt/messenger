# Korus Messenger (AvandocMsg)

Мульти-модульный сервер и воркеры на **Java 25** / **Gradle**.

## Журнал изменений и описание проекта

- **`CHANGELOG.md`**: при **значимых** доработках добавляйте запись в секцию **`[Unreleased]`** с **датой и временем в UTC** и перечислением выполненной работы (файлы, модули, поведение). Перед релизом при необходимости сгруппируйте записи под версией.
- **`README.md`**: поддерживайте в актуальном виде обзор модулей, команды сборки/запуска и ссылки на смежные каталоги (в т. ч. **`korus-web/`** для Docker-стека веб-клиента).

## Сборка и тесты

```bash
./gradlew buildIntegrity
```

Сборка и тесты всех модулей (аналогично CI). Только тесты: **`./gradlew test`**. На Windows: **`.\gradlew.bat buildIntegrity`**. В GitHub — workflow **`.github/workflows/ci.yml`** (см. **`docs/CI_AND_REPO_HYGIENE.md`**).

## Документация

| Документ | Содержание |
|----------|------------|
| **`modules/web-client`** | Веб-клиент на **Java + встроенный Tomcat** (см. **`modules/web-client/README.md`**): статика **`webui/`**, прокси **`/api/*`** на **`WEB_CLIENT_API_UPSTREAM`**; URL WebSocket — **`WEB_CLIENT_WS_PUBLIC_URL`** (**`WebClientEnvServlet`**). Запуск: **`.\gradlew.bat :modules:web-client:run`**, порт **`WEB_CLIENT_PORT`** (9080). Образ: **`docker/Dockerfile.web-client`**. |
| **`korus-web/`** | **Отдельное развёртывание** в Docker: **`docker compose up`** из каталога — две реплики клиента + **nginx** (балансировка HTTP, прокси **`/ws`** на ws-gateway). Опционально **`docker-compose.attach.yml`** — та же Docker-сеть, что и **`docker-compose.dev-min.yml`** (**`core-api`** / **`ws-gateway`** по именам). Подробности: **`korus-web/README.md`**, переменные — **`korus-web/.env.example`**. |
| **`deploy/two-host/`** | **Два хоста в LAN:** машина 1 — **`scripts/server-host-up.*`** + **`docker/docker-compose.lan-publish.yml`**; машина 2 — **`scripts/web-host-up.*`** (без **`-Attach`**). Hot-swap UI: **`dev-overlay/`**, **`scripts/dev-overlay-init.*`**, **`scripts/dev-overlay-up.*`**, **`korus-web/docker-compose.hotswap.yml`**. Чеклист: **`deploy/two-host/README.md`**. |
| **`CHANGELOG.md`** | Журнал изменений |
| **`docs/CI_AND_REPO_HYGIENE.md`** | CI, Dependabot, `.gitattributes`, локаль HTTP API (**`app.locale`** / **`APP_LOCALE`**: `ru`, `en`) |
| **`docs/RETENTION_AND_DEEP_ARCHIVE.md`** | Ретенция, политики, воркер hot-body, аудит |
| **`docs/plans/README.md`** | Детальные эпики (статусы и freeze-правила cleanup) |
| **`specs/002-web-client-server-parity/`** | Полный Spec-Kit пакет parity web-client: scope/plan/tasks + baseline/report/runbook (вход: **`specs/002-web-client-server-parity/README.md`**) |
| **`docs/db/FLYWAY_AND_SCHEMA.md`** | Миграции PostgreSQL |
| **`docs/ROADMAP_EPICS.md`** | Дорожная карта эпиков после базовой реализации |
| **`docs/PARALLEL_DEVELOPMENT.md`** | Параллельные потоки, контракты, миграции БД |
| **`scripts/SMOKE_INDEX.md`** | Канонические smoke-сценарии и матрица оберток |
| **`docker/docker-compose.dev-min.yml`** | Минимальный стенд; default Docker-сеть **`korus_messenger_dev_min`** (для **`korus-web`** с **`docker-compose.attach.yml`**). Профиль **`web`** — **ws-gateway** (**8082** на хосте) и **message-pipeline** (fan-out в WS; см. **`docker/Dockerfile.message-pipeline`**) |
| **`docker/docker-compose.full-server.yml`** | Полный стенд без профилей: **dev-min**-инфраструктура + **core-api** + **ws-gateway** (**8082**) + **message-pipeline** + **retention-worker** (**9192** metrics/health). Та же сеть **`korus_messenger_dev_min`**. Запуск: **`.\scripts\full-stack-up.ps1`** (**`-Build`**, **`-SkipEnsure`** / **`$env:SKIP_KORUS_ENSURE='1'`**, **`-Help`**, …) или **`scripts\full-stack-up.cmd`**, либо **`./scripts/full-stack-up.sh`** (**`--build`**, **`--skip-ensure`** / **`-S`**, **`SKIP_KORUS_ENSURE=1`**). Остановка: **`.\scripts\full-stack-down.ps1`** (**`-Help`**) / **`scripts\full-stack-down.cmd`** / **`./scripts/full-stack-down.sh`** (**`--help`**, **2** попытки **`docker compose down`**). Админка: **`http://localhost:8080/admin/`** (**csadmin**/**csadmin** или **admin**/**admin**, realm **avandocmsg**). |
| **`scripts/TEST_SERVER_READY.md`** | Когда стенд готов к ручным проверкам |
| **`scripts/dev-web-stack-up.ps1`** / **`dev-web-stack-up.cmd`** / **`scripts/dev-web-stack-up.sh`** | Профиль **`web`** в **`docker-compose.dev-min.yml`**; **`KORUS_*`**, встроенная проверка окружения, **2** попытки **`docker compose`**; **`-Help`**; **`-SkipEnsure`** / **`$env:SKIP_KORUS_ENSURE='1'`** / **`SKIP_KORUS_ENSURE=1`** / **`--skip-ensure`** в **`.sh`**; **`-Build` / `--build`**. |
| **`scripts/dev-web-stack-down.ps1`** / **`dev-web-stack-down.cmd`** / **`scripts/dev-web-stack-down.sh`** | Остановка профиля **`web`** (**ws-gateway**, **message-pipeline**): **`docker compose --profile web down`**; **`-Volumes`** / **`--volumes` / `-V`**; **`-Help`**. |
| **`scripts/korus-web-up.ps1`** / **`korus-web-up.cmd`** / **`scripts/korus-web-up.sh`** | Стек **`korus-web/`**; **`KORUS_KORUS_WEB_*`**, проверка окружения, **2** попытки compose; **`-Help`**; **`-Attach` / `--attach`**, **`-Turn` / `--turn` / `-t`** ( **`docker-compose.turn.yml`**, coturn), **`-SkipEnsure`** / **`$env:SKIP_KORUS_ENSURE='1'`** / **`SKIP_KORUS_ENSURE=1`** / **`--skip-ensure`** в **`.sh`**, **`-Build` / `--build`**. |
| **`scripts/korus-web-down.ps1`** / **`korus-web-down.cmd`** / **`scripts/korus-web-down.sh`** | Остановка **`korus-web/`** с теми же **`-f`**, что у **`up`**: **`-Attach` / `--attach`**, **`-Turn` / `--turn` / `-t`**; **`-Volumes`** / **`--volumes` / `-V`** — **`docker compose down -v`**; **`-Help`**. |
| **`scripts/smoke-korus-web.ps1`** / **`smoke-korus-web.cmd`** / **`scripts/smoke-korus-web.sh`** | Смок **`korus-web`** (health, UI, **`web-client-env.js`** с **`wsUrl`** / **`iceServersJson`**); **`-Help`** / **`--help`**; **`-CheckApi` / `--check-api`** — **`GET …/api/v1/health`** через прокси; в **`.sh`**: **`--url`** / **`WEB_BASE_URL`** |
| **`scripts/dev-ui-hints.ps1`** / **`dev-ui-hints.cmd`** / **`scripts/dev-ui-hints.sh`** | Вывести URL веб-клиента (порт из **`korus-web/.env`**), админку, Keycloak, тестовые логины realm (**`admin`/`csadmin`**). **`-Help`** / **`--help`** (**`.sh`**: без лишних аргументов). |
| **`scripts/install-env-silent.ps1`** / **`.sh`** (+ **`install-env-silent.cmd`**) | Тихая установка: **Windows** — **winget** (`--silent`, **`--disable-interactivity`**, вывод **winget** в **`-Quiet`** подавляется); **Linux** — **apt** + **Adoptium**, **get.docker.com**; **`--quiet`** / **`QUIET=1`** — минимум логов. **`-Help`** / **`--help`**. Полный цикл: **`.\scripts\install-environment.ps1 -SilentInstall -Quiet`** или **`./scripts/install-environment.sh --silent-install --quiet`**. Проверка окружения: **`.\scripts\install-environment.ps1 -Help`** / **`./scripts/install-environment.sh --help`**. |
| **`scripts/start.ps1`** / **`start.cmd`** / **`start.sh`** | Подъём **min**/**full**; **`scripts/lib/korus-env.*`**: **`KORUS_REPO_ROOT`**, **`KORUS_DOCKER_DIR`**, **`KORUS_COMPOSE_DEV_MIN`**, **`KORUS_COMPOSE_FULL_SERVER`**, **`KORUS_SCRIPTS_DIR`**, **`KORUS_KORUS_WEB_DIR`**; встроенные **`install-environment`** / при сбое **`install-env-silent`**; **2** попытки **`docker compose up`**. **`-Help`**. **`-SkipEnsure`** / **`$env:SKIP_KORUS_ENSURE='1'`** / **`SKIP_KORUS_ENSURE=1`** / в **`.sh`**: **`--skip-ensure`** / **`-S`**, **`./scripts/start.sh --help`** — без шага установки. |
| **`scripts/clean.ps1`**, **`clean.sh`**, **`create-stand.ps1`**, **`create-stand.sh`**, **`install-environment.ps1`** (+ **`.cmd`**) | **`clean`** / **`create-stand`**: **`KORUS_*`** из **`scripts/lib/korus-env.*`**, **2** попытки **`docker compose`** (пауза между попытками **10** с, как в **`start`**). **Windows**: **`.\scripts\clean.ps1 all`** (**`-Help`**), **`.\scripts\create-stand.ps1 min`** (**`-Help`**, **`-SkipEnsure`** или **`$env:SKIP_KORUS_ENSURE='1'`** — без шага установки; **`scripts\create-stand.cmd`**). **Linux/macOS**: **`./scripts/clean.sh all`** (**`./scripts/clean.sh --help`**), **`./scripts/create-stand.sh min`** (**`--skip-ensure`** / **`-S`** или **`SKIP_KORUS_ENSURE=1`** — без **`korus_ensure_env`**; **`./scripts/create-stand.sh --help`**). **`install-environment`**: **`-Help`** / **`--help`**; без **`-Quiet`** после проверки — строка «дальше: **create-stand** + **start**». **`cmd`**: **`scripts\clean.cmd`**, **`create-stand.cmd`**, **`start.cmd`**, **`full-stack-up.cmd`**, **`full-stack-down.cmd`**, **`dev-web-stack-up.cmd`**, **`dev-web-stack-down.cmd`**, **`korus-web-up.cmd`**, **`korus-web-down.cmd`**, **`install-environment.cmd`**, **`install-env-silent.cmd`**, **`smoke-korus-web.cmd`**, **`dev-ui-hints.cmd`**. |
| **`/admin/`** (встроенная консоль) | После запуска **`core-api`**: **`http://<host>:<port>/admin/`** — статика; быстрый вход с API: **`GET /api/v1/admin/console`** → **303** на **`/admin/`** (без JWT). Далее: **`GET /api/v1/admin/ui/manifest`** (разделы + **`api_version`**: статистика, **сессия admin**, организации, назначение пользователю org, аудит, ретенция), панели — **`GET`/`POST`/`PATCH`/`DELETE`** к **`/api/v1/admin/...`** (JWT + realm-роль **`admin`**); для списков — таблица + сырой JSON; организации — создание по имени; аудит — фильтры **action** / **resource_type** / **resource_id**. **«Выйти»** — **`POST /api/v1/auth/logout`**. Новые разделы — SPI **`AdminUiContributor`**, тип **`json_panel`**. Дополнительно: панель **ретенции** (**GET**/**PATCH** org/chat по UUID), форма **`PATCH .../users/{id}/organization`**, настраиваемый **limit** для аудита и **удаление организации** в UI; **«Обновить»** на панелях со **GET**-данными (статистика, аудит, организации, сессия; в **ретенции** — после первого **GET**). |

Корневой **`.editorconfig`** задаёт кодировку и отступы; для **`gradlew`** и **`gradlew.bat`** см. **`.gitattributes`**.

Модуль воркера ретенции: **`modules/workers/retention/README.md`**.
