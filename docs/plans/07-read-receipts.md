# Per-message read receipts

**Статус:** `not_started`
**Теги:** `[core-api]` `[ws-gateway]` `[воркер]` `[web-client]` `[admin-ui]`

---

## Цель

1. Per-message / per-participant read receipts.
2. Batch-эндпоинт: отметить несколько сообщений прочитанными.
3. Configurable retention для `message_read_receipts` (auto-delete старых записей).
4. Prometheus метрики для отслеживания объёма.
5. OpenAPI документация.
6. Admin UI: просмотр статистики прочтений.

---

## Текущее состояние

- **`POST /v1/chats/{chatId}/read`** — last_read_message_id.
- **`GET /v1/chats/{chatId}/unread-count`** — количество непрочитанных.
- **`chat_read_state`** — одна строка на `(user_id, chat_id)`.
- **`ChatReadRepository`** — JDBC-реализация.
- **WebSocket:** нет событий прочтения.
- **Web-client:** нет UI.

---

## Зависимости

- **Нет блокирующих зависимостей.**
- Согласовать с продуктом (объём данных, приватность).

---

## Шаги реализации

### 1. Таблица `message_read_receipts`

**1.1. Миграция V027**
- [ ] `V027__message_read_receipts.sql`:
  ```sql
  CREATE TABLE message_read_receipts (
      message_id UUID NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
      user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
      read_at TIMESTAMP NOT NULL DEFAULT now(),
      PRIMARY KEY (message_id, user_id)
  );
  CREATE INDEX idx_read_receipts_message ON message_read_receipts(message_id);
  CREATE INDEX idx_read_receipts_user ON message_read_receipts(user_id);
  CREATE INDEX idx_read_receipts_read_at ON message_read_receipts(read_at);
  ```
- [ ] `docs/db/FLYWAY_AND_SCHEMA.md` — V027.

### 2. REST эндпоинты

**2.1. POST /v1/chats/{chatId}/messages/{messageId}/read**
- [ ] `MessageReadReceiptRepository.insert(messageId, userId, now)`.
- [ ] Аудит: `action = "message.read"`.
- [ ] Если запись уже есть — `ON CONFLICT DO NOTHING`.
- **Тесты:**
  - [ ] `MessageReadReceiptRepositoryH2Test` — insert, ON CONFLICT, select.

**2.2. GET /v1/chats/{chatId}/read-receipts?messageId=...**
- [ ] `MessageReadReceiptRepository.findByMessageId(messageId) → List<UserInfo>`.
- [ ] Пагинация: `?offset=0&limit=100`.
- **Тесты:**
  - [ ] `MessageReadReceiptRepositoryQueryTest`.

**2.3. POST /v1/chats/{chatId}/read-batch**
- [ ] Тело: `{"message_ids": ["uuid1", "uuid2", ...]}`.
- [ ] `MessageReadReceiptRepository.insertBatch(messageIds, userId)` — batch INSERT.
- [ ] Лимит: `READ_RECEIPT_BATCH_MAX` (default 100).
- **Тесты:**
  - [ ] `MessageReadReceiptBatchTest` — проверить, что batch создаёт записи для всех ID.

**2.4. DTO**
- [ ] `ReadReceiptResponse.java` — `messageId`, `readBy: List<UserInfo>`.
- [ ] `BatchReadRequest.java` — `List<UUID> messageIds`.

### 3. OpenAPI

**3.1. DTO с аннотациями**
- [ ] `ReadReceiptResponse.java` — `@Schema`.
- [ ] `BatchReadRequest.java` — `@ExampleObject`.
- [ ] `ReadReceiptEvent.java` — `@Schema`.

### 4. Prometheus метрики

**4.1. `ApiMetrics.java` — новые счётчики**
- [ ] `read_receipt_inserts_total` (Counter).
- [ ] `read_receipt_batch_size` (Histogram, buckets: 1, 10, 50, 100).
- [ ] `read_receipt_batch_count` (Counter).
- [ ] `read_receipt_repository_size` (Gauge — число строк, периодически SELECT COUNT).

### 5. NATS события

**5.1. `NatsSubjects.java` — `MSG_EVENT_READ_RECEIPT`**
- [ ] Subject: `msg.event.read_receipt`.

**5.2. `ReadReceiptEvent.java`** (DTO):
```java
public class ReadReceiptEvent {
    private UUID chatId;
    private UUID messageId;
    private UUID userId;
    private Instant readAt;
    private List<UUID> batchMessageIds; // опционально
}
```

### 6. ws-gateway: доставка

**6.1. `PipelineFanoutLogic.java`**
- [ ] Подписка на `msg.event.read_receipt`.
- [ ] Fan-out на `msg.deliver.{participant}` — всем, кроме отправителя.

**6.2. `MessagingWebSocket.java`**
- [ ] Обработка события `read_receipt` на клиенте: обновить UI.

### 7. Retention для `message_read_receipts`

**7.1. `MessageReadReceiptRepository.deleteOlderThan(days)`**
- [ ] SQL: `DELETE FROM message_read_receipts WHERE read_at < now() - INTERVAL 'N days'`.
- **Тесты:**
  - [ ] `MessageReadReceiptRetentionH2Test`.

**7.2. `RetentionWorker` — интеграция**
- [ ] `RetentionHotBodyJanitor.java` — `purgeOldReadReceipts()`:
  - [ ] Если `READ_RECEIPT_RETENTION_DAYS > 0` (default 365): вызвать `deleteOlderThan`.
- **Тесты:**
  - [ ] `RetentionReadReceiptPurgeTest`.

### 8. Web-client UI

**8.1. `app.js` — отображение**
- [ ] Для своих сообщений: две галочки (прочитано/доставлено).
- [ ] При наведении: popup со списком прочитавших.
- [ ] При входе в чат: `POST /v1/chats/{id}/read-batch` с последними N сообщениями.

**8.2. `styles.css`**
- [ ] `.msg-read-receipt-double-check` — синие галочки.
- [ ] `.msg-read-receipt-popup` — список пользователей.

### 9. Настройки приватности

**9.1. Миграция V028**
- [ ] `V028__user_privacy_settings.sql`:
  ```sql
  ALTER TABLE users ADD COLUMN privacy_disable_read_receipts BOOLEAN DEFAULT false;
  ```

**9.2. `UserResource.patchUser()`**
- [ ] PATCH `/v1/users/{id}` — поле `privacy_disable_read_receipts`.
- **Тесты:**
  - [ ] `UserServicePrivacyTest`.

### 10. Admin UI

**10.1. `CoreAdminUiContributor.java` — раздел «Read receipts»**
- [ ] `core-read-receipts` — `json_panel` с `data_path = /admin/read-receipts/stats`.
- [ ] `AdminResource.java` — `GET /admin/read-receipts/stats`:
  - [ ] `total_rows`, `rows_by_chat_top_10`, `avg_per_message`.

### 11. Smoke-тесты

**11.1. `scripts/smoke-read-receipts.ps1`**
- [ ] POST /read → 201.
- [ ] GET /read-receipts → список.
- [ ] POST /read-batch → 201.
- [ ] WebSocket: получить событие `read_receipt`.
- [ ] UI: проверить галочки (Selenium/Playwright).

---

## Критерии завершения

- [ ] `POST /read` создаёт запись в `message_read_receipts`.
- [ ] `POST /read-batch` создаёт записи для нескольких сообщений.
- [ ] `GET /read-receipts` возвращает список.
- [ ] WebSocket доставляет событие участникам.
- [ ] Web-client отображает read receipts.
- [ ] Старые записи удаляются через `READ_RECEIPT_RETENTION_DAYS`.
- [ ] Admin UI: статистика отображается.
- [ ] Smoke: `smoke-read-receipts.ps1` проходит.

---

## Риски

- **Объём данных:** `участники × сообщения`. Нужны индексы и партиционирование.
- **Batch:** риск превышения лимита БД.
- **Приватность:** read receipts могут быть нежелательны.
