# Параллельная разработка сервера

Чтобы несколько людей (или несколько параллельных задач) не блокировали друг друга, используйте **разделение по модулям и контрактам**.

## Потоки работ (типовое разбиение)

| Поток | Каталоги / модули | Избегать одновременно с другим потоком |
|--------|-------------------|----------------------------------------|
| **HTTP API** | `modules/core-api/src/main/java/.../api/**` | Один большой PR на все `*Resource.java` + общий `JerseyConfig` — дробите по ресурсам/фичам. |
| **Веб-клиент (Tomcat)** | `modules/web-client/**`, развёртывание **`korus-web/**`** | Код UI и Tomcat — **`modules/web-client`** (см. **`modules/web-client/README.md`**). Docker Compose с балансировщиком — **`korus-web/`**; опционально общая сеть с dev-min — **`korus-web/docker-compose.attach.yml`**. Скрипты стенда (**`web`** в **`docker-compose.dev-min.yml`**): **`scripts/dev-web-stack-up.ps1`** / **`.sh`** (**`KORUS_*`**, проверка окружения, повтор **`docker compose`**); полный compose: **`scripts/full-stack-up.ps1`** / **`.sh`**; UI: **`scripts/korus-web-up.ps1`** / **`.sh`**. Контракт с сервером — те же REST/WebSocket. |
| **Воркеры** | `modules/workers/**` | Конфликты редки, если не трогать общие миграции и не менять один NATS-subject в двух PR без согласования. Ретенция/deep-archive: **`docs/RETENTION_AND_DEEP_ARCHIVE.md`**. Образ **message-pipeline**: **`docker/Dockerfile.message-pipeline`** (профиль **`web`** в **`docker-compose.dev-min.yml`**). |
| **Общие DTO и события** | `modules/common/src/main/java/**` | Любое изменение формата события — **совместимо назад** или отдельная версия поля (`schema_version`). |
| **БД** | `modules/core-api/src/main/resources/db/migration/**` | **Один владелец миграций на спринт** или очередь PR: миграции мержить первыми, затем код. См. **`docs/db/FLYWAY_AND_SCHEMA.md`**. |
| **Локализация API** | `**/i18n/messages_*.properties` | Новые ключи добавлять **в паре `*_ru` / `*_en`**; CI ловит расхождение (**`Messages*BundleParityTest`**). |

## Контракты между потоками

1. **NATS:** имена subject’ов и полезная нагрузка — DTO в **`modules/common`**; не ломать JSON без необходимости.
2. **REST:** поля JSON — **snake_case** (как в остальном API).
3. **Ошибки клиенту:** только через ключи bundle (**`UserMessageSource`**), не голые строки в **`ApiError`**.
4. **Встроенная админ-консоль (`/admin/`):** разделы меню регистрируются через SPI **`com.avandocmsg.messenger.common.admin.ui.AdminUiContributor`** и файл **`META-INF/services/…AdminUiContributor`** в JAR модуля; дубликаты **`id`** игнорируются (остаётся первый). Тип панели **`json_panel`**: **`data_path`** — GET от **`/api/v1`** (как у **`core_stats`**).

## Git и CI

- Короткие ветки, маленькие PR.
- Перед merge: **`./gradlew buildIntegrity`** (как в **`.github/workflows/ci.yml`**).
- При конфликте в общих файлах (**`MessengerApplication`**, **`JerseyConfig`**) — сначала мерж инфраструктурный PR, затем фичи.

## Связанные документы

- **`CHANGELOG.md`**, **`docs/CI_AND_REPO_HYGIENE.md`**, **`scripts/TEST_SERVER_READY.md`**, **`docs/ROADMAP_EPICS.md`**, **`docs/RETENTION_AND_DEEP_ARCHIVE.md`**, **`docs/db/FLYWAY_AND_SCHEMA.md`**.
