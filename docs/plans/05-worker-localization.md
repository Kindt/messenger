# Локализация воркеров — i18n, метрики, health

**Статус:** `completed` (инфраструктура i18n, health, parity-тесты, Gradle gate, замена hardcoded log-строк во всех воркерах)
**Теги:** `[i18n]` `[воркер]` `[common]` `[тесты]`

---

## Цель

1. Подключить файлы `messages_worker_*.properties` к реальному `MessageSource` во всех воркерах.
2. Единый язык для health-проверок и Prometheus метрик.
3. Обеспечить паритет ключей (ru/en) для каждого воркера.
4. Добавить Gradle таску для автоматической проверки bundle.
5. HealthServlet: i18n ответов (200/503 с сообщением на языке `APP_LOCALE`).

---

## Текущее состояние

- **`modules/common`:** `CompositeMessageSource`, `Utf8Control`, `UserMessageSource`, `WorkerMessageSources`, `WorkerHealthText`, `BundleParityTestUtil`.
- **`modules/common`:** `messages_common_{ru,en}.properties` — ключи `worker.health.ok` / `worker.health.not_ready`.
- **Все 9 воркеров:** `WorkerMessageSources.forWorker(...)` в `main()`, bundle `messages_worker_*_{ru,en}.properties` с ключом `worker.module`.
- **Health‑ответы:** локализованы через `WorkerHealthText` (retention, export-replay, push).
- **Gradle:** `checkBundleParity` + `bundleParityTest` в каждом subproject; входит в `buildIntegrity`.

---

## Зависимости

- **Нет блокирующих зависимостей.**

---

## Шаги реализации

### 1. MessageSource в каждом воркере

**1.1. message-pipeline**
- [x] `MessagePipelineWorker.java` — `WorkerMessageSources.forWorker` в `main()`.

**1.2. archiver**
- [x] `ArchiverWorker.java` — аналогично.

**1.3. deep-archiver**
- [x] `DeepArchiverWorker.java` — аналогично.

**1.4. indexer**
- [x] `IndexerWorker.java` — аналогично.

**1.5. preview**
- [x] `PreviewWorker.java` — аналогично.

**1.6. push**
- [x] `PushWorker.java` — аналогично + health server.

**1.7. bot-delivery**
- [x] `BotDeliveryWorker.java` — аналогично.

**1.8. export-replay**
- [x] `ExportReplayWorker.java` — аналогично + health server.

**1.9. retention**
- [x] `RetentionWorker.java` — аналогично + health server.

### 2. Замена hardcoded строк

**2.1. Поиск строк в воркерах**
- [x] Gradle gate `checkBundleParity` / `buildIntegrity`.
- [x] Массовая замена hardcoded log-строк во всех 9 воркерах и janitor/helper-классах (`worker.common.*` + `worker.<module>.*`).

### 3. Health/metrics — язык

**3.1. `RetentionMetricsHttpServer.java`**
- [x] `GET /health` через `WorkerHealthText` + `UserMessageSource`.
- [x] `RetentionMetricsHttpServerI18nTest`.

**3.2. `ExportReplayMetricsHttpServer.java`**
- [x] `GET /health` — аналогично.
- [x] `ExportReplayMetricsHttpServerI18nTest`.

**3.3. `PushHealthHttpServer.java`**
- [x] `GET /health` — аналогично.
- [x] `PushHealthHttpServerI18nTest`.

### 4. Паритет-тесты

- [x] `MessagesPipelineBundleParityTest`
- [x] `MessagesArchiverBundleParityTest`
- [x] `MessagesDeepArchiverBundleParityTest`
- [x] `MessagesIndexerBundleParityTest`
- [x] `MessagesPreviewBundleParityTest`
- [x] `MessagesPushBundleParityTest`
- [x] `MessagesBotDeliveryBundleParityTest`
- [x] `MessagesExportReplayBundleParityTest`
- [x] `MessagesRetentionBundleParityTest`

### 5. Gradle задача `checkBundleParity`

- [x] `bundleParityTest` в каждом subproject (фильтр `*BundleParityTest`).
- [x] Корневая `checkBundleParity` + `dependsOn` в `buildIntegrity`.

### 6. Заполнить недостающие bundle

- [x] Все `messages_worker_*_{ru,en}.properties` — ключ `worker.module` (базовый паритет).

### 7. Тесты локализации health

- [x] `RetentionMetricsHttpServerI18nTest` — ru/en.
- [x] `ExportReplayMetricsHttpServerI18nTest` — ru/en.
- [x] `PushHealthHttpServerI18nTest` — ru/en.
- [x] `WorkerMessageSourcesTest` (common).

---

## Критерии завершения

- [x] Все воркеры используют `WorkerMessageSources` / `CompositeMessageSource`.
- [x] Паритет-тесты проходят для каждого воркера.
- [x] `checkBundleParity` встроена в `buildIntegrity`.
- [x] Health-ответы локализованы (зависят от `APP_LOCALE` / переданного `UserMessageSource`).
- [x] Тесты локализации health проходят.
- [x] `./gradlew.bat buildIntegrity` — green (2026-05-24).

---

## Риски

- Мелкие воркеры почти не пишут сообщения человеку — полная замена лог-строк отложена.
- Регрессия: после замены строк могут измениться форматы (args, порядок) — при deferred PR.
