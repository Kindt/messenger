# Экспорт и комплаенс — политика полноты, GDPR

**Статус:** `in_progress`
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
- [ ] `ExportCompleteness.java` — добавить поле `mandatoryFields: Map<String, Boolean>`:
  - [ ] `messages`, `chat`, `chat_members`, `referenced_users`, `referenced_files`.
  - [ ] `solr_index`, `deep_archive`, `retention_snapshots`, `gdpr_disclosures`.

**1.2. `ExportCompletenessValidator.java`** (новый класс в `modules/common/src/main/java/.../common/export/`)
- [ ] Метод `validate(ExportCompleteness completeness) → ValidationResult`:
  - [ ] Для каждого mandatory-поля проверить, что оно присутствует.
  - [ ] Если отсутствует — `result.addWarning("missing_field", fieldName)`.
  - [ ] Если `EXPORT_COMPLETENESS_STRICT=true` и отсутствует — `result.addError()`.
- [ ] `ValidationResult.java` — `hasErrors(): boolean`, `hasWarnings(): boolean`, `getMessages(): List<String>`.
- **Тесты:**
  - [ ] `ExportCompletenessValidatorTest` — проверить различные сценарии полноты.

**1.3. Интеграция в `ExportReplayWorker.java`**
- [ ] После сборки пакета вызвать `ExportCompletenessValidator.validate()`.
- [ ] Сохранить результат в `ExportCompleteness.complete`.
- [ ] Если `EXPORT_COMPLETENESS_STRICT=true` и `ValidationResult.hasErrors()` — пометить статус `export_failed`.
- **Тесты:**
  - [ ] `ExportReplayWorkerCompletenessTest` — mock validator.

**1.4. Env-переменные**
- [ ] `EXPORT_REQUIRED_FIELDS` (csv: `messages,chat,referenced_users`).
- [ ] `EXPORT_COMPLETENESS_STRICT` (default `false`).
- [ ] `AppConfig.java` — парсинг.

### 2. OpenAPI: новые поля completeness

**2.1. DTO с аннотациями**
- [ ] `ExportCompleteness.java` — `@Schema(description = "Результат проверки полноты")`.
- [ ] `ExportGdprDisclosures.java` — `@ExampleObject`.
- [ ] `ValidationResult.java` — `@Schema`.
- **Тесты:**
  - [ ] `AdminExportComplianceOpenApiTest` — обновить.

### 3. Prometheus метрики completeness

**3.1. `ExportMetrics.java` — новые счётчики**
- [ ] `export_completeness_check_total` (Counter).
- [ ] `export_completeness_failed_total` (Counter с label `reason`).
- [ ] `export_completeness_duration_seconds` (Histogram).
- **Тесты:**
  - [ ] `ExportMetricsTest` — проверить регистрацию.

### 4. Runbook: экспорт перед purge

**4.1. Дополнить `scripts/pre-retention-export.ps1`**
- [ ] Читать список чатов, для которых retention подходит к purge.
- [ ] Для каждого чата: проверить наличие экспорта, если нет — запустить `POST /v1/chats/{id}/export`.
- [ ] Дождаться завершения (poll `GET .../export/{jobId}`).

**4.2. `docs/RETENTION_AND_DEEP_ARCHIVE.md`**
- [ ] §8 — описать runbook: экспорт обязателен перед purge при `EXPORT_REQUIRED_BEFORE_PURGE=true`.

### 5. Документирование для операторов

**5.1. `docs/EXPORT_OPERATOR.md`**
- [ ] Состав пакета: `export.json`, `attachments/manifest.json`, `attachments/{hash}/{file}`.
- [ ] Срок хранения: `EXPORT_DIR` (default 30 дней).
- [ ] Проверка полноты: `GET /v1/chats/{chatId}/export/{jobId}` → `exportCompleteness`.
- [ ] Принудительный экспорт: скрипт `pre-retention-export.ps1`.
- **Файлы:**
  - [ ] `README.md` — ссылка на `docs/EXPORT_OPERATOR.md`.

### 6. Admin UI для экспорта

**6.1. `CoreAdminUiContributor.java` — раздел «Экспорт»**
- [ ] `core-export` — `json_panel`.
- [ ] `admin-ui/app.js`:
  - [ ] Поле: ввод chatId.
  - [ ] Кнопка «Экспортировать» → `POST /v1/chats/{chatId}/export`.
  - [ ] Отображение статуса (poll каждый 5 сек).
  - [ ] Кнопка «Скачать» при `status == export_v1`.

### 7. Web-client: кнопка «Экспорт чата»

**7.1. `app.js` — кнопка в UI чата**
- [ ] В панели информации о чате (или в меню):
  - [ ] Кнопка «Экспорт чата».
  - [ ] При нажатии: `POST /v1/chats/{chatId}/export`.
  - [ ] Показать progress bar (poll каждые 5 сек).
  - [ ] По завершении: кнопка «Скачать ZIP».
- [ ] `styles.css` — `.export-button`, `.export-progress`.

### 8. Smoke-тесты

**8.1. `scripts/smoke-export-gdpr-fulfillment.ps1`**
- [ ] Создать чат, отправить сообщения разных типов.
- [ ] Запустить экспорт.
- [ ] Проверить `exportCompleteness.complete = true`.
- [ ] Проверить все mandatory-поля.

**8.2. `scripts/smoke-export-web-client.ps1`**
- [ ] Selenium/Playwright или curl: проверить, что кнопка экспорта есть в web-client UI.

---

## Критерии завершения

- [ ] `ExportCompletenessValidator` проверяет все mandatory-поля.
- [ ] Smoke: `smoke-export-gdpr-fulfillment.ps1` — успешный прогон.
- [ ] Web-client: кнопка «Экспорт» работает, статус отображается.
- [ ] Admin UI: статус экспорта виден.
- [ ] OpenAPI: документация новых полей completeness.
- [ ] Prometheus: метрики completeness экспортируются.
- [ ] Документация: `docs/EXPORT_OPERATOR.md`.

---

## Риски

- GDPR-требования могут различаться по регионам — нужна конфигурация политик.
- Экспорт больших чатов может занимать много времени и места на диске.
- Если `EXPORT_REQUIRED_BEFORE_PURGE=true`, часть чатов может никогда не очиститься (нет экспорта).
