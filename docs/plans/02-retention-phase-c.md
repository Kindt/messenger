# Ретенция Фаза C — purge hot, файлы, legal hold

**Статус:** `completed`
**Теги:** `[ретенция]` `[БД]` `[воркер]` `[core-api]` `[безопасность]` `[admin-ui]`

---

## Цель

1. **HOT_ROW_PURGED:** удаление строки из hot DB при наличии полной цепочки Archive + Deep + экспорт.
2. **Ретенция `file_metadata` и бинарников:** очистка неиспользуемых файлов из MinIO.
3. **Legal hold:** расширение за пределы hot-body (файлы, deep-archive, Solr).
4. **Admin UI:** управление legal hold и просмотр статуса purge.
5. **OpenAPI:** документировать новые admin эндпоинты.
6. **Smoke-тесты:** end-to-end проверка purge + восстановление.

---

## Текущее состояние

- **Hot-body pass** — снимок тела в MinIO, `content = NULL`, `msg.event.retention`, `retention_hot_body_applied`.
- **Archive + Deep** — archiver пишет метаданные в Archive DB, deep-archiver — JSON в MinIO.
- **`file_metadata`** — нет автоматической очистки.
- **MinIO-объекты файлов** — не чистятся (хранятся в `avandocmsg` bucket).
- **Legal hold** — учитывается только в SQL воркера ретенции (`eff_legal = false`). Нет расширения на файлы и deep-archive.
- **Экспорт** — `ExportResource` может экспортировать чат, но не обязателен перед purge (runbook).
- **Solr** — при `content = NULL` публикуется `index_op=update` (пустой `content_txt`).
- **Admin UI:** нет панели legal hold, нет отображения статуса purge.
- **OpenAPI:** нет эндпоинтов для управления purge/legal hold.
- **Prometheus:** нет метрик purge.

---

## Зависимости

- **Фаза B** (01) — должна быть завершена (чанки, унификация формата).
- **Экспорт/комплаенс** (03) — до purge требуется проверка наличия экспорта.
- Блокирующих зависимостей нет, но рекомендуется порядок: Фаза B → C.

---

## Шаги реализации

Каждый шаг разбит на подзадачи с указанием файлов, классов, методов и тестов.

### 1. Проектирование HOT_ROW_PURGED

**1.1. Анализ FK и условий**
- [x] `docs/RETENTION_AND_DEEP_ARCHIVE.md` — обновить §3 (модель состояний): добавить `HOT_ROW_PURGED`.
- [x] Определить FK: `messages` → `chat_messages`, `message_versions`, `reactions`, `message_read_receipts`, `audit_events`.
- [x] Проверить, что каскадное удаление не сломает Archive DB (archiver уже скопировал метаданные).

**1.2. SQL для purge**
- [x] `DELETE FROM messages WHERE id = ? AND EXISTS (SELECT 1 FROM retention_hot_body_applied WHERE message_id = ?) AND ...`
- [x] Условия: есть `retention_hot_body_applied`, legal hold = false, экспорт выполнен (если `EXPORT_REQUIRED_BEFORE_PURGE=true`).
- **Тесты:**
  - [x] `RetentionPurgeSqlTest` — H2, проверить что SQL удаляет только строки, удовлетворяющие условиям.

**1.3. `RetentionHotBodyJanitor.purgeHotRows()`**
- [x] Новый метод в `RetentionHotBodyJanitor.java`:
  - [x] `SELECT messages.id, chat_id FROM messages WHERE ...` (те же условия).
  - [x] Для каждого `message_id`: `DELETE FROM messages WHERE id = ?`.
  - [x] Публикация `msg.event.index` с `index_op=delete`.
  - [x] Аудит `message.retention.hot_row_purged`.
- [x] Метод `runOnce()` вызывает `purgeHotRows()` после `processBatch()`.
- [x] Env: `RETENTION_HOT_ROW_PURGE_ENABLED` (default `false`), `RETENTION_PURGE_BATCH_LIMIT` (default `25`).
- **Тесты:**
  - [x] `RetentionPurgeH2Test` — mock NATS, проверить полный цикл.

### 2. Экспорт перед purge

**2.1. `ExportJobRepository.existsCompletedExport(chatId)`**
- [x] SQL: `SELECT 1 FROM export_jobs WHERE chat_id = ? AND status IN ('export_v1', 'stub_written') LIMIT 1`.
- **Тесты:**
  - [x] `ExportJobRepositoryH2Test` — `existsCompletedExport`.

**2.2. Интеграция в `RetentionHotBodyJanitor`**
- [x] Если `EXPORT_REQUIRED_BEFORE_PURGE=true` (default `false`):
  - [x] Перед purge каждого сообщения из чата: проверить `ExportJobRepository.existsCompletedExport(chatId)`.
  - [x] Если нет — пропустить все сообщения этого чата, аудит `export.required_before_purge_skipped`.
- **Тесты:**
  - [x] `RetentionPurgeExportRequiredTest` — mock exporter, проверить пропуск.

### 3. Ретенция `file_metadata`

**3.1. SQL для поиска неиспользуемых файлов**
- [x] `SELECT fm.id, fm.storage_key FROM file_metadata fm LEFT JOIN unnest(?::uuid[]) AS used_ids(id) ON fm.id = used_ids.id WHERE used_ids.id IS NULL AND fm.created_at < now() - INTERVAL '30 days'`.
- [x] Список используемых UUID собирается из `messages.content` (через `ContentAnalyzer.extractFileId`).
- **Тесты:**
  - [x] `FileRepositoryUnusedTest` — H2, проверить SQL.

**3.2. Миграция V024**
- [x] `V024__file_metadata_retention_index.sql`:
  ```sql
  CREATE INDEX IF NOT EXISTS idx_file_metadata_created_at ON file_metadata(created_at);
  ```
- [x] `docs/db/FLYWAY_AND_SCHEMA.md` — добавить V024.

**3.3. `RetentionWorker` — расширение или новый модуль**
- [x] `FileRetentionJanitor.java` (новый класс):
  - [x] `process()`: найти неиспользуемые `file_metadata`, удалить строки.
  - [x] Вызвать `MinioFileProxy.deleteFile(key)` для каждого.
  - [x] Аудит `file.retention.deleted`.
- [x] Подключить в `RetentionWorker.runOnce()`.
- **Тесты:**
  - [x] `FileRetentionH2Test` — mock MinIO, проверить удаление.

### 4. Ретенция MinIO-объектов

**4.1. `FileProxy.deleteFile()`**
- [x] `MinioFileProxy.java` — метод `deleteFile(String storageKey)`:
  - [x] `minioClient.removeObject(bucket, storageKey)`.
- **Тесты:**
  - [x] `MinioFileDeletionTest` — mock MinIOClient, проверить `removeObject`.

### 5. Legal hold: расширение

**5.1. Миграция V025**
- [x] `V025__legal_hold_extensions.sql`:
  ```sql
  ALTER TABLE org_retention_policy ADD COLUMN legal_hold_files BOOLEAN DEFAULT false;
  ALTER TABLE org_retention_policy ADD COLUMN legal_hold_deep_archive BOOLEAN DEFAULT false;
  ALTER TABLE chat_retention_policy ADD COLUMN legal_hold_files BOOLEAN DEFAULT false;
  ALTER TABLE chat_retention_policy ADD COLUMN legal_hold_deep_archive BOOLEAN DEFAULT false;
  ```
- [x] `docs/db/FLYWAY_AND_SCHEMA.md` — V025.

**5.2. `RetentionPolicyRepository` — расширение**
- [x] `EffectivePolicy` — поле `legalHoldFiles`, `legalHoldDeepArchive`.
- [x] SQL: при расчёте эффективной политики подтягивать новые поля.
- **Тесты:**
  - [x] `RetentionPolicyRepositoryH2Test` — проверить чтение новых флагов.

**5.3. `RetentionHotBodyJanitor` — учёт расширенного legal hold**
- [x] При `legal_hold_files = true` — не удалять `file_metadata`.
- [x] При `legal_hold_deep_archive = true` — не удалять deep-archive объекты.
- **Тесты:**
  - [x] `RetentionLegalHoldExtendedTest` — проверить, что purge пропускает файлы.

**5.4. Admin UI: панель Legal Hold**
- [x] `CoreAdminUiContributor.java` — новый раздел `core-legal-hold`.
- [x] `admin-ui/app.js`:
  - [x] Форма: выбор org/chat, флаги legal hold (message body, files, deep-archive).
  - [x] GET/PATCH `/admin/legal-hold/{orgId,chatId}`.
- **Файлы:**
  - [x] `AdminResource.java` — эндпоинты `GET /admin/legal-hold/*`, `PATCH /admin/legal-hold/*`.

### 6. Solr: delete при purge

**6.1. NATS публикация в `RetentionHotBodyJanitor`**
- [x] После `DELETE FROM messages WHERE id = ?`:
  - [x] Создать `MessageWorkerEvent(messageId, chatId, index_op=delete)`.
  - [x] Опубликовать в `msg.event.index`.
- **Тесты:**
  - [x] `RetentionPurgeNatsTest` — mock NATS, проверить что `msg.event.index` опубликован с `index_op=delete`.

### 7. Admin UI: статус purge

**7.1. `CoreAdminUiContributor.java` — раздел «Purge status»**
- [x] `core-purge-status` — `json_panel` с `data_path = /admin/purge/status`.
- [x] `AdminResource.java` — `GET /admin/purge/status`:
  - [x] Возвращает `PurgeStatusResponse`: `total_purged`, `last_pass_at`, `errors_count`, `pending_count`.
- [x] `admin-ui/app.js` — таблица со статусом.

### 8. OpenAPI: admin эндпоинты

**8.1. DTO с `@ExampleObject`**
- [x] `LegalHoldResponse.java` — `@Schema`.
- [x] `LegalHoldUpdateRequest.java` — `@ExampleObject`.
- [x] `PurgeStatusResponse.java` — `@Schema`.

### 9. Prometheus метрики purge

**9.1. Новые счётчики**
- [x] `retention_worker_hot_rows_purged_total` (Counter).
- [x] `retention_worker_file_metadata_deleted_total` (Counter).
- [x] `retention_worker_minio_objects_deleted_total` (Counter).
- [x] `retention_worker_purge_errors_total` (Counter с label `reason`).

### 10. Smoke-тесты

**10.1. `scripts/smoke-retention-purge.ps1`**
- [x] Seed: создать org, чат, сообщения, дождаться hot-body pass.
- [x] Активировать purge (`RETENTION_HOT_ROW_PURGE_ENABLED=true`).
- [x] Проверить, что сообщения удалены из hot.
- [x] Проверить, что они есть в Archive + Deep + MinIO.
- [x] Проверить audit events.

**10.2. `scripts/smoke-retention-file-cleanup.ps1`**
- [x] Загрузить файл, открепить от всех сообщений.
- [x] Дождаться pass очистки файлов.
- [x] Проверить, что `file_metadata` удалена, MinIO объект удалён.

### 11. Инструкция по восстановлению

- [x] `docs/RECOVERY_FROM_PURGE.md`:
  - [x] SQL: вставить строку в `messages` из Archive + Deep данных.
  - [x] Проверить, что FK constraints не нарушены.
  - [x] Восстановить Solr индекс (переиндексация).

---

## Критерии завершения

- [x] Unit-тесты: `RetentionPurgeH2Test`, `FileRetentionH2Test`, `RetentionLegalHoldExtendedTest`.
- [x] Admin UI: разделы «Legal Hold» и «Purge» доступны.
- [x] OpenAPI: новые эндпоинты документированы.
- [x] Smoke: `smoke-retention-purge.ps1`, `smoke-retention-file-cleanup.ps1` проходят.
- [x] Аудит: события `message.retention.hot_row_purged`, `file.retention.deleted`.
- [x] Документация: `docs/RETENTION_AND_DEEP_ARCHIVE.md`, `docs/RECOVERY_FROM_PURGE.md`.

---

## Риски

- **Потеря данных** — если FK или проверки неполные. Требуется тщательное тестирование на копии prod БД.
- **Дедупликация файлов** — один файл может быть прикреплён к разным сообщениям. Нельзя удалять, пока есть хотя бы одна ссылка.
- **Legal hold может конфликтовать** с требованиями регуляторов (разные типы hold для разных данных).
- **Solr** — при `index_op=delete` может не удалить документ, если он заблокирован в Solr.
