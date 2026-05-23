# Code Health Backlog — small PR series

**Статус:** `completed`  
**Теги:** `[refactoring]` `[core-api]` `[workers]` `[web-client]` `[tests]`

## Цель

Подготовить безопасную серию малых PR для оздоровления ключевых hotspot-зон без массового рефакторинга в одном изменении.

## PR-цепочка (предлагаемый порядок)

### PR-1: Export replay split (start)

- **Фокус:** `modules/workers/export-replay/.../ExportReplayWorker.java`
- **Шаг:** выделить из worker отдельный компонент SQL-чтения сообщений (например `ExportMessageLoader`) без изменения API/формата выходного JSON.
- **Прогресс:** `completed` (выделен `ExportMessageLoader`, `ExportReplayWorker` оставляет совместимые фасады для SQL/helper API).
- **Safety tests (обязательные):**
  - `ExportReplayWorkerTest`
  - `ExportReplayWorkerTest#buildMessagesSql_appliesTtlWhenEnabled`
  - `ExportReplayWorkerTest#collectFileIdsFromText`

### PR-2: Admin export surface split

- **Фокус:** `modules/core-api/.../admin/AdminResource.java`
- **Шаг:** выделить export-related обработчики в отдельный helper/facade класс, сохранив URL/контракты.
- **Прогресс:** `completed` (добавлен `AdminExportFacade`, export endpoints в `AdminResource` делегируют в facade без изменения маршрутов/DTO).
- **Safety tests (обязательные):**
  - `AdminExportComplianceSeedH2Test`
  - `AdminExportComplianceOpenApiTest`
  - `ExportResourceTest`

### PR-3: Retention hot-body extraction

- **Фокус:** `modules/workers/retention/.../RetentionHotBodyJanitor.java`
- **Шаг:** вынести chunk/snapshot-ветви в отдельные внутренние helper-методы/классы (`snapshot writer`, `chunk writer`) без смены SQL-контракта.
- **Прогресс:** `completed` (ветви snapshot/chunk выделены во внутренние helper-классы `RetentionSnapshotWriter` и `RetentionChunkWriter`).
- **Safety tests (обязательные):**
  - `RetentionHotBodyPassGaugesTest`
  - `RetentionHotBodyCandidateSqlTest`
  - `RetentionMinioSnapshotPayloadTest`

### PR-4: Web client module split (incremental)

- **Фокус:** `modules/web-client/src/main/resources/webui/app.js`
- **Шаг:** начать мягкую декомпозицию через выделение утилитного модуля (например форматирование/TTL UI helpers) без изменения пользовательского поведения.
- **Прогресс:** `completed` (добавлен модуль `ui-format-utils.js`; форматирование времени/TTL в `app.js` делегировано в вынесенный util с fallback).
- **Safety checks:**
  - ручной smoke через `scripts/smoke-korus-web.ps1` / `.sh`
  - проверка базового login/chat flow в dev-стенде

## Общий DoD для каждого PR

- Область изменений одна (один hotspot).
- `./gradlew buildIntegrity` зеленый (или docs-only с явной пометкой).
- Нет изменения внешних API/subject/DB-контрактов без отдельного RFC.
- Добавлен короткий rollback-план в PR description.
