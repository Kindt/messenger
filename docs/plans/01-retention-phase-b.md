# Ретенция Фаза B — TTL, чанки, унификация deep-archive

**Статус:** `in_progress` (шаги 1–5 завершены; 6–9 не начаты)
**Теги:** `[ретенция]` `[БД]` `[воркер]` `[core-api]` `[deep-archiver]` `[export]`

---

## Цель

1. Определить и реализовать семантику TTL: развести **видимость** (скрыть из UI) и **перенос в deep-archive** (физическое перемещение).
2. Унифицировать формат deep-archive с retention-снимками: чанкование больших JSON, единый контракт чтения.
3. Обработка крупных тел (attachments): не дублировать в MinIO, а ссылаться.
4. Обеспечить совместимость export с новым форматом deep-archive (чанки).
5. Добавить OpenAPI документацию для новых DTO.
6. Добавить web-client отображение TTL (оставшееся время жизни сообщения).

---

## Текущее состояние

- **`V023`** применена: `messages.ttl_seconds` переименовано в `messages.visibility_ttl_seconds`; dual TTL (`visibility_ttl_seconds` + `archive_ttl_seconds`) включен.
- API отправки поддерживает `visibility_ttl_seconds` и `archive_ttl_seconds`; обратная совместимость по JSON сохранена через alias `ttl_seconds`.
- Фильтр видимости TTL унифицирован: `MessageRepository.SQL_MSG_VISIBILITY_TTL_VISIBLE`, `findByChatId`, `findById`, поиск, `viewerMayAccessFilePublicLink`, `findLatestMessageId`, `ChatReadRepository.countUnreadFromOthers`.
- `DeepArchiverWorker` и `RetentionHotBodyJanitor` поддерживают чанкование JSON с `manifest.json` + `part-*.json`.
- Добавлены `DeepArchiveManifest`, `ChunkEntry`, `DeepArchiveReader`, `ContentAnalyzer`; export читает deep-archive через единый reader.
- Реализован skip snapshot для `file://{uuid}` ссылок (без дублирования бинарников в MinIO).
- Введены env-флаги для chunk thresholds (`DEEP_ARCHIVE_CHUNK_SIZE_BYTES`, `RETENTION_CHUNK_THRESHOLD_BYTES`).
- **Осталось по фазе B:** завершить Solr-очистку (step 6), TTL-индикатор web-client (step 7), финализацию метрик/smoke (step 8), и финальную синхронизацию документации (step 9).

---

## Зависимости

- Фаза A ретенции (завершена) — hot-body pass работает.
- [Export compliance](03-export-compliance.md) — экспорт должен читать чанки.
- Нет блокирующих зависимостей от других эпиков.

---

## Шаги реализации

Каждый шаг разбит на подзадачи с указанием конкретных файлов, классов, методов и тестов.

### 1. Продуктовое решение: модель TTL

**1.1. Согласовать модель TTL с product owner** ✅
- [x] Определиться: `visibility_ttl_seconds` (скрыть из UI) vs `archive_ttl_seconds` (перенос в deep) vs обе.
- [x] **Решение: обе модели параллельно.** `visibility_ttl_seconds` — скрытие из UI; `archive_ttl_seconds` — перенос тела в deep-archive.
- [x] Зафиксировать решение в `tz_revision_proposal.md`.
- [x] Обновить `docs/RETENTION_AND_DEEP_ARCHIVE.md` §2 (описание TTL).

**1.2. Rename поля в API (если нужно)** ✅
- [x] `SendMessageRequest.java` — rename `ttlSeconds` → `visibilityTtlSeconds` (с retain Jackson alias для обратной совместимости).
- [x] `MessageRepository.java` — rename `SQL_MSG_TTL_VISIBLE` → `SQL_MSG_VISIBILITY_TTL_VISIBLE`.
- [x] `MessageService.java` — rename валидации `MESSAGE_TTL_MAX_SECONDS`.
- [x] `ApplicationProperties` / `AppConfig.java` — rename ключа `message.ttl.max.seconds`.
- **Тесты:**
  - [x] `MessageServiceTest` — обновить тесты TTL-валидации.
  - [x] `MessageRepositoryH2Test` — проверить новый SQL-предикат.

**1.3. Миграция БД (если rename)** ✅
- [x] `V023__rename_ttl_to_visibility.sql`.
- [x] Обновить `docs/db/FLYWAY_AND_SCHEMA.md` — добавить V023.
- **Тесты:**
  - [ ] Проверить, что `flyway migrate` проходит на существующей БД с данными.
  - [ ] Проверить, что старые запросы с `ttl_seconds` продолжают работать (через alias).

**1.4. OpenAPI: новые поля** ✅
- [x] `SendMessageRequest.java` — добавить `example = "3600"` для `visibilityTtlSeconds`.
- [x] `MessageResource.java` — `@Schema` на request body (автоматически через record).
- **Тесты:**
  - [ ] `ApiDocTest` / `OpenApiTest` — проверить генерацию OpenAPI.json (не создан, существующий `AdminExportComplianceOpenApiTest` покрывает общий механизм).

---

### 2. Чанкование deep-archive

**2.1. Создать DTO манифеста чанков** ✅
- [x] `DeepArchiveManifest.java` (в `modules/common/src/main/java/.../common/dto/`).
- [x] `ChunkEntry.java`.
- **Тесты:**
  - [x] `DeepArchiveManifestSerializationTest` — Jackson сериализация/десериализация.
  - [x] `DeepArchiveManifestValidationTest` — проверка полей.

**2.2. Добавить константы в `ArchiveSnapshotFormat.java`** ✅
- [x] `CHUNK_MANIFEST_FILENAME`, `CHUNK_PART_PREFIX`, `CHUNK_PART_FORMAT`, `DEFAULT_CHUNK_SIZE_BYTES`.
- **Тесты:**
  - [x] `ArchiveSnapshotFormatTest` — константы.

**2.3. Модифицировать `DeepArchiverWorker.java`** ✅
- [x] При `byte[].length > DEEP_ARCHIVE_CHUNK_SIZE_BYTES`:
  - [x] Разделить JSON на чанки по `CHUNK_SIZE_BYTES`.
  - [x] Записать `messages/{id}/manifest.json`.
  - [x] Записать `messages/{id}/part-000.json`, `part-001.json`, ...
  - [x] Иначе (без чанков) — как сейчас, один `messages/{id}.json`.
- [x] `DeepArchiverWorker.java` — метод `writeChunked(MinioClient, String bucket, String messageId, byte[] json)`.
- **Тесты:**
  - [x] `DeepArchiverWorkerChunkingTest` — проверка формата чанков, manifest round-trip, SHA-256.

**2.4. Env-переменная `DEEP_ARCHIVE_CHUNK_SIZE_BYTES`** ✅
- [x] `application.properties` — добавлена ссылка `DEEP_ARCHIVE_CHUNK_SIZE_BYTES`.
- [x] `DeepArchiverWorker` — чтение env при старте (в `main()`).
- **Тесты:**
  - [ ] Проверка парсинга: `0` → без чанков, `1` → каждый байт отдельный чанк (edge case).

**2.5. Prometheus метрики чанков**
- [ ] `deep_archiver_chunk_writes_total` (Counter с label `message_id`).
- [ ] `deep_archiver_chunked_messages_total` (Counter).
- [ ] `deep_archiver_chunk_size_bytes` (Histogram).
- **Тесты:**
  - [ ] `DeepArchiverMetricsTest` — проверить, что метрики экспортируются.

**2.6. Smoke-тесты чанков**
- [ ] `scripts/smoke-deep-archive-chunks.ps1`:
  - [ ] Отправить сообщение с большим content.
  - [ ] Дождаться deep-archive.
  - [ ] Проверить, что в MinIO появились `messages/{id}/manifest.json` + `part-*.json`.
  - [ ] Проверить, что manifest корректен (sha256 совпадает с собранным JSON).

---

### 3. Чанкование retention-снимков (единый формат)

**3.1. Переиспользовать `DeepArchiveManifest` в `RetentionHotBodyJanitor.java`** ✅
- [x] Импортировать `DeepArchiveManifest` из `modules/common`.
- [x] При `RETENTION_CHUNK_THRESHOLD_BYTES > 0` и размере снимка > порога:
  - [x] Писать `{RETENTION_MINIO_OBJECT_PREFIX}{messageId}/manifest.json`.
  - [x] Писать `part-000.json`, `part-001.json`, ...
- [x] Реализован метод `writeRetentionChunks()` с манифестом и чанками.
- [x] `RetentionHotBodyJanitor` — ветвление на chunked/flat в обоих путях (tempfile и direct bytes).

**3.2. Env-переменная `RETENTION_CHUNK_THRESHOLD_BYTES`** ✅
- [x] `RetentionPlatformDefaults.java` — парсинг env.
- [x] `RetentionHotBodyJanitor.java` — проверка env при старте.
- **Тесты:**
  - [ ] `RetentionHotBodyJanitorChunkingTest` — mock MinIO, проверить чанки при пороге > 0.
  - [ ] `RetentionHotBodyJanitorNoChunkingTest` — поведение без изменений при пороге 0.

**3.3. Метрики чанков ретенции**
- [ ] `retention_worker_chunk_writes_total` (Counter).
- [ ] `retention_worker_chunk_skipped_total` (Counter — если чанки отключены).
- **Тесты:**
  - [ ] `RetentionMetricsChunkingTest`.

---

### 4. Унификация контракта чтения (`DeepArchiveReader`)

**4.1. Создать `DeepArchiveReader.java` в `modules/common`** ✅
- [x] Пакет: `com.avandocmsg.messenger.common.retention`.
- [x] Метод `readMessage(MinioClient client, String bucket, String messageId) → Optional<InputStream>`:
  - [x] Проверить существование `messages/{id}/manifest.json`.
  - [x] Если есть — прочитать манифест, склеить чанки в один InputStream.
  - [x] Если нет — прочитать `messages/{id}.json` (старый формат).
  - [x] Если и его нет — вернуть `Optional.empty()`.
- **Тесты:**
  - [ ] `DeepArchiveReaderTest` — интеграционный (требует MinIO).

**4.2. Интегрировать reader в `ExportReplayWorker.java`** ✅
- [x] Заменить прямой `ExportMinioJsonFetcher.fetchSnapshot` на `DeepArchiveReader.readMessage(...)` в `ExportDeepArchiveReader.fetchMessageSnapshot`.
- [x] Убедиться, что все места чтения deep-archive используют reader (теперь `ExportDeepArchiveReader` делегирует `DeepArchiveReader`).
- **Тесты:**
  - [ ] `ExportReplayWorkerDeepArchiveTest` — экспорт сообщения с чанками.

---

### 5. Крупные тела: не дублировать в MinIO

**5.1. Определить формат ссылки на файл в `messages.content`** ✅
- [x] Паттерн: `file://{fileId}`.
- [x] `ContentAnalyzer.java` — хелпер в `modules/common/retention`:
  - [x] `isFileReference(String content) → boolean`.
  - [x] `extractFileId(String content) → Optional<UUID>`.
- **Тесты:**
  - [x] `ContentAnalyzerTest` — все кейсы.

**5.2. `RetentionHotBodyJanitor.java` — пропуск снимка для ссылок** ✅
- [x] Перед `putObject` проверить `ContentAnalyzer.isFileReference(content)`.
- [x] Если true — не создавать снимок, сразу очищать `content = NULL`.
- [x] В лог: `"Skipped snapshot for message {id}: content is file reference"`.
- **Тесты:**
  - [x] `ContentAnalyzerTest` — `isFileReference`, `extractFileId`.
  - [ ] `RetentionHotBodyJanitorFileRefTest` — mock MinIO, проверить, что `putObject` не вызывается.

**5.3. `DeepArchiverWorker.java` — аналогично** ✅
- [x] Если content — ссылка на файл, не создавать deep-archive (проверка через `ContentAnalyzer.isFileReference` на raw JSON).
- **Тесты:**
  - [ ] `DeepArchiverWorkerFileRefTest`.

---

### 6. Solr: очистка при выносе тела

**6.1. Проверить `IndexerWorker.java`**
- [ ] Найти обработку `index_op=update` с пустым `content_txt`.
- [ ] Убедиться, что Solr `AtomicUpdate` с `set` пустой строки действительно очищает индекс.
- [ ] Если нет — добавить `delete` документа перед `add`.
- **Тесты:**
  - [ ] `IndexerWorkerSolrUpdateTest` — mock SolrClient, проверить последовательность вызовов.

---

### 7. Web-client: отображение TTL

**7.1. `app.js` — рендер TTL-индикатора**
- [ ] В `renderMessage()`: если `message.ttl_seconds != null`:
  - [ ] Показать иконку таймера ⏱ (unicode).
  - [ ] Показать оставшееся время: `formatTimeLeft(createdAt + ttlSeconds - now)`.
- [ ] Обновлять таймер каждые 60 секунд (setInterval).
- [ ] По истечении TTL: скрыть сообщение или показать placeholder «Сообщение недоступно».

**7.2. `styles.css` — стили**
- [ ] `.msg-ttl-indicator` — стиль для иконки и текста.
- [ ] `.msg-ttl-expired` — полупрозрачный placeholder.

**7.3. Обработка `visibility_ttl` при получении через WS**
- [ ] При получении нового сообщения через WebSocket: рассчитать TTL локально.
- **Тесты:**
  - [ ] Ручная проверка: отправить сообщение с TTL=60, проверить таймер в UI.

---

### 8. Prometheus метрики (сводные)

**8.1. deep-archiver метрики**
- [ ] `deep_archiver_chunk_writes_total` — см. п. 2.5.

**8.2. retention-worker метрики**
- [ ] `retention_worker_chunk_writes_total` — см. п. 3.3.
- [ ] `retention_worker_file_ref_skipped_total` — сколько сообщений пропущено как file reference.

---

### 9. Обновление документации

**9.1. `docs/RETENTION_AND_DEEP_ARCHIVE.md`**
- [ ] §10 этап 2-3 — отметить прогресс.
- [ ] §6 — описать чанки.
- [ ] §2 — TTL семантика.

**9.2. `docs/db/FLYWAY_AND_SCHEMA.md`**
- [ ] Добавить V023 (если rename TTL).

**9.3. `docs/NATS_SUBJECTS_INTEROP.md`**
- [ ] Уточнить payload deep-archive при чанках (нет изменений subject-ов).

**9.4. `docs/ROADMAP_EPICS.md`**
- [ ] Отметить прогресс Фазы B.

---

## Критерии завершения

- [ ] Unit-тесты: `RetentionHotBodyJanitorTest` (чанки, крупные тела, file ref).
- [ ] Unit-тесты: `DeepArchiverWorkerTest` (чанки, манифест).
- [ ] Unit-тесты: `DeepArchiveReaderTest` (чтение старого и нового формата).
- [ ] Unit-тесты: `ExportReplayWorkerTest` (чтение чанков).
- [ ] Unit-тесты: `ContentAnalyzerTest`.
- [ ] Интеграционные тесты: `scripts/smoke-deep-archive-chunks.ps1`.
- [ ] Smoke: `scripts/smoke-export-chat.ps1` — обновлён для работы с чанками.
- [ ] OpenAPI: `AdminExportComplianceOpenApiTest` — обновлён.
- [ ] Паритет: старые объекты в MinIO читаются новым читателем.
- [ ] Web-client: TTL-индикатор работает.
- [ ] Документация: `docs/RETENTION_AND_DEEP_ARCHIVE.md`, `docs/db/FLYWAY_AND_SCHEMA.md`.

---

## Риски

- Продуктовое решение по TTL может затянуться (нет ответа от product owner).
- Чанкование может сломать обратную совместимость, если какой-то потребитель читает `messages/{id}.json` напрямую.
- При чанковании растёт число объектов в MinIO — проверить лимиты бакета.
- Export без поддержки чанков не сможет прочитать большие сообщения после Фазы B.
- Web-client TTL — может не совпадать с серверным TTL (часовые пояса, задержки).
