# Экспорт и комплаенс — политика полноты, GDPR

**Статус:** `completed`
**Теги:** `[экспорт]` `[воркер]` `[core-api]` `[аудит]` `[admin-ui]` `[web-client]`

---

## Цель

1. Завершить контур `export-replay`: определить и реализовать политику полноты выгрузки (GDPR).
2. Документировать для операторов: состав пакета, сроки хранения выгрузки.
3. Обеспечить гарантию, что экспорт доступен до агрессивной ретенции (Фаза C).
4. Добавить экспорт чата в web-client UI (кнопка «Экспорт чата»).
5. OpenAPI: документировать новые поля completeness.
6. Prometheus метрики для отслеживания полноты экспорта.

---

## Текущее состояние

- **`ExportReplayWorker`** — полноценный экспорт чата (JSON/ZIP, attachments, manifest, partial download).
- **REST API:** `POST /v1/chats/{chatId}/export`, `GET .../{jobId}`, `GET .../download`.
- **Export в ZIP:** `export.json` + `attachments/` + `attachments/manifest.json`, `part`-download (`bundle/json/manifest`).
- **MinIO-загрузка:** `EXPORT_REPLAY_MINIO_UPLOAD`.
- **NATS:** `msg.export.replay`, `msg.export.replay.complete`, `msg.export.replay.cancel`, `msg.export.suggested`; опциональная auto-queue цепочка реализована.
- **Аудит:** `export.requested`, `export.completed`, `export.downloaded`, `export.suggested`, `export.auto_queued`.
- **Compliance prep:** `POST /admin/export-compliance-prep`.
- **Admin UI:** панель compliance flow + сценарии inspect/download.
- **Web-client:** нет кнопки экспорта.
- **OpenAPI:** core export/openapi контур покрыт smoke и unit, но модель completeness policy требует финализации.
- **Prometheus:** базовая наблюдаемость экспортного контура есть; метрики completeness policy остаются в хвосте.

---

## Зависимости

- **Фаза C ретенции (02)** — должна учитывать наличие экспорта перед purge.

---

## Шаги реализации

### 1. Политика полноты выгрузки (GDPR)

**1.1. Определить чеклист mandatory-полей**
- [x] `ExportCompleteness.java` — добавить поле `mandatoryFields: Map<String, Boolean>`:
  - [x] `messages`, `chat`, `chat_members`, `referenced_users`, `referenced_files`.
  - [x] `solr_index`, `deep_archive`, `retention_snapshots`, `gdpr_disclosures`.

**1.2. `ExportCompletenessValidator.java`** (новый класс в `modules/common/src/main/java/.../common/export/`)
- [x] Метод `validate(ExportCompleteness completeness) → ValidationResult`:
  - [x] Для каждого mandatory-поля проверить, что оно присутствует.
  - [x] Если отсутствует — `result.addWarning("missing_field", fieldName)`.
  - [x] Если `EXPORT_COMPLETENESS_STRICT=true` и отсутствует — `result.addError()`.
- [x] `ValidationResult.java` — `hasErrors(): boolean`, `hasWarnings(): boolean`, `getMessages(): List<String>`.
- **Тесты:**
  - [x] `ExportCompletenessValidatorTest` — проверить различные сценарии полноты.

**1.3. Интеграция в `ExportReplayWorker.java`**
- [x] После сборки пакета вызвать `ExportCompletenessValidator.validate()`.
- [x] Сохранить результат в `ExportCompleteness.complete`.
- [x] Если `EXPORT_COMPLETENESS_STRICT=true` и `ValidationResult.hasErrors()` — пометить статус `export_failed`.
- **Тесты:**
  - [x] `ExportReplayWorkerCompletenessTest` — mock validator.

**1.4. Env-переменные**
- [x] `EXPORT_REQUIRED_FIELDS` (csv: `messages,chat,referenced_users`).
- [x] `EXPORT_COMPLETENESS_STRICT` (default `false`).
- [x] `AppConfig.java` — парсинг.

### 2. OpenAPI: новые поля completeness

**2.1. DTO с аннотациями**
- [x] `ExportCompleteness.java` — `@Schema(description = "Результат проверки полноты")`.
- [x] `ExportGdprDisclosures.java` — `@ExampleObject`.
- [x] `ValidationResult.java` — `@Schema`.
- **Тесты:**
  - [x] `AdminExportComplianceOpenApiTest` — обновить.

### 3. Prometheus метрики completeness

**3.1. `ExportMetrics.java` — новые счётчики**
- [x] `export_completeness_check_total` (Counter).
- [x] `export_completeness_failed_total` (Counter с label `reason`).
- [x] `export_completeness_duration_seconds` (Histogram).
- **Тесты:**
  - [x] `ExportMetricsTest` — проверить регистрацию.

### 4. Runbook: экспорт перед purge

**4.1. Дополнить `scripts/pre-retention-export.ps1`**
- [x] Читать список чатов, для которых retention подходит к purge.
- [x] Для каждого чата: проверить наличие экспорта, если нет — запустить `POST /v1/chats/{id}/export`.
- [x] Дождаться завершения (poll `GET .../export/{jobId}`).

**4.2. `docs/RETENTION_AND_DEEP_ARCHIVE.md`**
- [x] §8 — описать runbook: экспорт обязателен перед purge при `EXPORT_REQUIRED_BEFORE_PURGE=true`.

### 5. Документирование для операторов

**5.1. `docs/EXPORT_OPERATOR.md`**
- [x] Состав пакета: `export.json`, `attachments/manifest.json`, `attachments/{hash}/{file}`.
- [x] Срок хранения: `EXPORT_DIR` (default 30 дней).
- [x] Проверка полноты: `GET /v1/chats/{chatId}/export/{jobId}` → `exportCompleteness`.
- [x] Принудительный экспорт: скрипт `pre-retention-export.ps1`.
- **Файлы:**
  - [x] `README.md` — ссылка на `docs/EXPORT_OPERATOR.md`.

### 6. Admin UI для экспорта

**6.1. `CoreAdminUiContributor.java` — раздел «Экспорт»**
- [x] `core-export` — `json_panel`.
- [x] `admin-ui/app.js`:
  - [x] Поле: ввод chatId.
  - [x] Кнопка «Экспортировать» → `POST /v1/chats/{chatId}/export`.
  - [x] Отображение статуса (poll каждый 5 сек).
  - [x] Кнопка «Скачать» при `status == export_v1`.

### 7. Web-client: кнопка «Экспорт чата»

**7.1. `app.js` — кнопка в UI чата**
- [x] В панели информации о чате (или в меню):
  - [x] Кнопка «Экспорт чата».
  - [x] При нажатии: `POST /v1/chats/{chatId}/export`.
  - [x] Показать progress bar (poll каждые 5 сек).
  - [x] По завершении: кнопка «Скачать ZIP».
- [x] `styles.css` — `.export-button`, `.export-progress`.

### 8. Smoke-тесты

**8.1. `scripts/smoke-export-gdpr-fulfillment.ps1`**
- [x] Создать чат, отправить сообщения разных типов.
- [x] Запустить экспорт.
- [x] Проверить `exportCompleteness.complete = true`.
- [x] Проверить все mandatory-поля.

**8.2. `scripts/smoke-export-web-client.ps1`**
- [x] Selenium/Playwright или curl: проверить, что кнопка экспорта есть в web-client UI.

---

## Критерии завершения

- [x] `ExportCompletenessValidator` проверяет все mandatory-поля.
- [x] Smoke: `smoke-export-gdpr-fulfillment.ps1` — успешный прогон.
- [x] Web-client: кнопка «Экспорт» работает, статус отображается.
- [x] Admin UI: статус экспорта виден.
- [x] OpenAPI: документация новых полей completeness.
- [x] Prometheus: метрики completeness экспортируются.
- [x] Документация: `docs/EXPORT_OPERATOR.md`.

---

## Риски

- GDPR-требования могут различаться по регионам — нужна конфигурация политик.
- Экспорт больших чатов может занимать много времени и места на диске.
- Если `EXPORT_REQUIRED_BEFORE_PURGE=true`, часть чатов может никогда не очиститься (нет экспорта).
