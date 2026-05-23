# Локализация воркеров — i18n, метрики, health

**Статус:** `not_started`
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

- **`modules/common`:** `CompositeMessageSource`, `Utf8Control`, `UserMessageSource`.
- **`modules/core-api`:** `messages_core_api_{ru,en}.properties` — работает.
- **`modules/ws-gateway`:** `messages_ws_gateway_{ru,en}.properties` — работает.
- **`modules/common`:** `messages_common_{ru,en}.properties` — общие ключи.
- **Воркеры:** заготовки `messages_worker_*.properties` есть, но не подключены.
- **Health‑ответы:** hardcoded `"ok"` / `"not ready"`.

---

## Зависимости

- **Нет блокирующих зависимостей.**

---

## Шаги реализации

### 1. MessageSource в каждом воркере

**1.1. message-pipeline**
- [ ] `MessagePipelineWorker.java` — в `main()`:
  ```java
  CompositeMessageSource messages = new CompositeMessageSource(
      "messages_worker_pipeline", "messages_common"
  );
  ```
- [ ] Передать `messages` в классы, которые пишут логи человеку (если такие есть).

**1.2. archiver**
- [ ] `ArchiverWorker.java` — аналогично.

**1.3. deep-archiver**
- [ ] `DeepArchiverWorker.java` — аналогично.

**1.4. indexer**
- [ ] `IndexerWorker.java` — аналогично.

**1.5. preview**
- [ ] `PreviewWorker.java` — аналогично (если есть сообщения человеку).

**1.6. push**
- [ ] `PushWorker.java` — аналогично.

**1.7. bot-delivery**
- [ ] `BotDeliveryWorker.java` — аналогично.

**1.8. export-replay**
- [ ] `ExportReplayWorker.java` — аналогично.

**1.9. retention**
- [ ] `RetentionWorker.java` — аналогично.

### 2. Замена hardcoded строк

**2.1. Поиск строк в воркерах**
- [ ] `rg -n "\"(Запущен|Ошибка|Начинаю|Завершён|Не удалось|Успешно)" modules/workers/` — найти русские строки.
- [ ] `rg -n "\"(Starting|Error|Begin|Finished|Failed|Success)" modules/workers/` — найти английские.
- [ ] Каждую найденную строку заменить на `messages.get("worker.<module>.<key>", args)`.

**Пример:**
```java
// Было:
log.warn("Не удалось подключиться к NATS: {}", url);
// Стало:
log.warn(messages.get("worker.pipeline.nats_connect_failed", url));
```

### 3. Health/metrics — язык

**3.1. `RetentionMetricsHttpServer.java`**
- [ ] `GET /health`:
  - [ ] При `200`: `messages.get("worker.retention.health_ok")` → `"ok"` (ru) / `"healthy"` (en).
  - [ ] При `503`: `messages.get("worker.retention.health_not_ready")` → `"not ready"`.
- **Тесты:**
  - [ ] `RetentionMetricsHttpServerI18nTest` — `APP_LOCALE=en` → ответ `"healthy"`.

**3.2. `ExportMetrics.java`**
- [ ] `GET /health` — аналогично.

### 4. Паритет-тесты

**4.1. Для каждого воркера создать тест:**
- [ ] `modules/workers/message-pipeline/src/test/java/.../MessagesPipelineBundleParityTest.java`:
  ```java
  @Test void ruAndEnHaveSameKeys() { ... }
  ```
- [ ] `modules/workers/archiver/.../MessagesArchiverBundleParityTest.java`
- [ ] `modules/workers/deep-archiver/.../MessagesDeepArchiverBundleParityTest.java`
- [ ] `modules/workers/indexer/.../MessagesIndexerBundleParityTest.java`
- [ ] `modules/workers/preview/.../MessagesPreviewBundleParityTest.java`
- [ ] `modules/workers/push/.../MessagesPushBundleParityTest.java`
- [ ] `modules/workers/bot-delivery/.../MessagesBotDeliveryBundleParityTest.java`
- [ ] `modules/workers/export-replay/.../MessagesExportReplayBundleParityTest.java`
- [ ] `modules/workers/retention/.../MessagesRetentionBundleParityTest.java`

### 5. Gradle задача `checkBundleParity`

**5.1. `build.gradle.kts`**
- [ ] Новая задача:
  ```kotlin
  tasks.register("checkBundleParity") {
      group = "verification"
      description = "Run all bundle parity tests across all modules"
      subprojects.forEach { sub ->
          dependsOn(sub.tasks.matching { it.name.contains("BundleParity") })
      }
  }
  ```
- [ ] Добавить `dependsOn("checkBundleParity")` в `buildIntegrity`.

### 6. Заполнить недостающие bundle

**6.1. Создать/дополнить:**
- [ ] `modules/workers/archiver/src/main/resources/messages_worker_archiver_ru.properties`
- [ ] `modules/workers/archiver/.../messages_worker_archiver_en.properties`
- [ ] `modules/workers/deep-archiver/.../messages_worker_deep_archiver_{ru,en}.properties`
- [ ] `modules/workers/indexer/.../messages_worker_indexer_{ru,en}.properties`
- [ ] `modules/workers/preview/.../messages_worker_preview_{ru,en}.properties`
- [ ] `modules/workers/push/.../messages_worker_push_{ru,en}.properties`
- [ ] `modules/workers/bot-delivery/.../messages_worker_bot_delivery_{ru,en}.properties`

### 7. Тесты локализации health

**7.1. `RetentionMetricsHttpServerI18nTest.java`**
- [ ] `APP_LOCALE=en` → `GET /health` → `200` + `"healthy"`.
- [ ] `APP_LOCALE=ru` → `GET /health` → `"ok"` (или `"здоров"`, по выбору).

**7.2. `ExportMetricsHealthI18nTest.java`** — аналогично.

---

## Критерии завершения

- [ ] Все воркеры используют `CompositeMessageSource`.
- [ ] Паритет-тесты проходят для каждого воркера.
- [ ] `checkBundleParity` встроена в `buildIntegrity`.
- [ ] Health-ответы локализованы (зависят от `APP_LOCALE`).
- [ ] Тесты локализации health проходят.

---

## Риски

- Мелкие воркеры почти не пишут сообщения человеку — локализация может быть избыточна.
- Регрессия: после замены строк могут измениться форматы (args, порядок).
