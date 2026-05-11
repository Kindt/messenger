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
| **`CHANGELOG.md`** | Журнал изменений |
| **`docs/CI_AND_REPO_HYGIENE.md`** | CI, Dependabot, `.gitattributes`, локаль HTTP API (**`app.locale`** / **`APP_LOCALE`**: `ru`, `en`) |
| **`docs/RETENTION_AND_DEEP_ARCHIVE.md`** | Ретенция, политики, воркер hot-body, аудит |
| **`docs/db/FLYWAY_AND_SCHEMA.md`** | Миграции PostgreSQL |
| **`docs/ROADMAP_EPICS.md`** | Дорожная карта эпиков после базовой реализации |
| **`docs/PARALLEL_DEVELOPMENT.md`** | Параллельные потоки, контракты, миграции БД |
| **`docker/docker-compose.dev-min.yml`** | Минимальный стенд; default Docker-сеть **`korus_messenger_dev_min`** (для **`korus-web`** с **`docker-compose.attach.yml`**). Профиль **`web`** — **ws-gateway** (**8082** на хосте) и **message-pipeline** (fan-out в WS; см. **`docker/Dockerfile.message-pipeline`**) |
| **`docker/docker-compose.full-server.yml`** | Полный стенд без профилей: **dev-min**-инфраструктура + **core-api** + **ws-gateway** (**8082**) + **message-pipeline** + **retention-worker** (**9192** metrics/health). Та же сеть **`korus_messenger_dev_min`**. Запуск: **`.\scripts\full-stack-up.ps1`** (**`-Build`**, **`-SkipEnsure`**, переменные **`KORUS_*`**, встроенная проверка окружения, **2** попытки **`docker compose`**) или **`./scripts/full-stack-up.sh`** (**`--build`**, **`SKIP_KORUS_ENSURE=1`**). Остановка: **`.\scripts\full-stack-down.ps1`** / **`./scripts/full-stack-down.sh`**. Админка: **`http://localhost:8080/admin/`** (**csadmin**/**csadmin** или **admin**/**admin**, realm **avandocmsg**). |
| **`scripts/TEST_SERVER_READY.md`** | Когда стенд готов к ручным проверкам |
| **`scripts/dev-web-stack-up.ps1`** / **`scripts/dev-web-stack-up.sh`** | Профиль **`web`** в **`docker-compose.dev-min.yml`**; **`KORUS_*`**, встроенная проверка окружения, **2** попытки **`docker compose`**; **`-SkipEnsure`** / **`SKIP_KORUS_ENSURE=1`**; **`-Build` / `--build`**. |
| **`scripts/korus-web-up.ps1`** / **`scripts/korus-web-up.sh`** | Стек **`korus-web/`**; **`KORUS_KORUS_WEB_*`**, проверка окружения, **2** попытки compose; **`-Attach` / `--attach`**, **`-SkipEnsure`** / **`SKIP_KORUS_ENSURE=1`**, **`-Build` / `--build`**. |
| **`scripts/smoke-korus-web.ps1`** / **`scripts/smoke-korus-web.sh`** | Смок **`korus-web`** (health, UI, **`web-client-env.js`**); **`-CheckApi` / `--check-api`** — **`GET …/api/v1/health`** через прокси; в **`.sh`**: **`--url`** / **`WEB_BASE_URL`** |
| **`scripts/dev-ui-hints.ps1`** / **`scripts/dev-ui-hints.sh`** | Вывести URL веб-клиента (порт из **`korus-web/.env`**), админку, Keycloak, тестовые логины realm (**`admin`/`csadmin`**) |
| **`scripts/install-env-silent.ps1`** / **`.sh`** (+ **`install-env-silent.cmd`**) | Тихая установка: **Windows** — **winget** (`--silent`, **`--disable-interactivity`**, вывод **winget** в **`-Quiet`** подавляется); **Linux** — **apt** + **Adoptium**, **get.docker.com**; **`--quiet`** / **`QUIET=1`** — минимум логов. Полный цикл: **`.\scripts\install-environment.ps1 -SilentInstall -Quiet`** или **`./scripts/install-environment.sh --silent-install --quiet`**. |
| **`scripts/start.ps1`** / **`start.sh`** | Подъём **min**/**full**; **`scripts/lib/korus-env.*`**: **`KORUS_REPO_ROOT`**, **`KORUS_DOCKER_DIR`**, **`KORUS_COMPOSE_DEV_MIN`**, **`KORUS_COMPOSE_FULL_SERVER`**, **`KORUS_SCRIPTS_DIR`**, **`KORUS_KORUS_WEB_DIR`**; встроенные **`install-environment`** / при сбое **`install-env-silent`**; **2** попытки **`docker compose up`**. **`-SkipEnsure`** / **`SKIP_KORUS_ENSURE=1`** — без шага установки. |
| **`scripts/clean.ps1`**, **`create-stand.ps1`**, **`install-environment.ps1`** (+ **`.cmd`**) | **Windows**: **`clean`**, **`create-stand`**, **`install-environment`** (как **`.sh`**). Из корня: **`.\scripts\clean.ps1 all`**, **`.\scripts\create-stand.ps1 min`**. **`cmd`**: **`scripts\clean.cmd`**, … |
| **`/admin/`** (встроенная консоль) | После запуска **`core-api`**: **`http://<host>:<port>/admin/`** — статика; быстрый вход с API: **`GET /api/v1/admin/console`** → **303** на **`/admin/`** (без JWT). Далее: **`GET /api/v1/admin/ui/manifest`** (разделы + **`api_version`**: статистика, **сессия admin**, организации, назначение пользователю org, аудит, ретенция), панели — **`GET`/`POST`/`PATCH`/`DELETE`** к **`/api/v1/admin/...`** (JWT + realm-роль **`admin`**); для списков — таблица + сырой JSON; организации — создание по имени; аудит — фильтры **action** / **resource_type** / **resource_id**. **«Выйти»** — **`POST /api/v1/auth/logout`**. Новые разделы — SPI **`AdminUiContributor`**, тип **`json_panel`**. Дополнительно: панель **ретенции** (**GET**/**PATCH** org/chat по UUID), форма **`PATCH .../users/{id}/organization`**, настраиваемый **limit** для аудита и **удаление организации** в UI; **«Обновить»** на панелях со **GET**-данными (статистика, аудит, организации, сессия; в **ретенции** — после первого **GET**). |

Корневой **`.editorconfig`** задаёт кодировку и отступы; для **`gradlew`** и **`gradlew.bat`** см. **`.gitattributes`**.

Модуль воркера ретенции: **`modules/workers/retention/README.md`**.
