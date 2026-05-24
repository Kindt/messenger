# Per-message read receipts

**Статус:** `completed` (2026-05-24)
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

## Реализовано

| Область | Артефакты |
|---------|-----------|
| Миграции | `V026__message_read_receipts.sql`, `V027__user_privacy_read_receipts.sql` |
| REST | `POST …/messages/{id}/read`, `POST …/read-batch`, `GET …/read-receipts`, `PATCH /v1/users/me/privacy` |
| NATS/WS | `msg.read_receipt`, fan-out в `MessagePipelineWorker`, WS `read_receipt` в web-client |
| Retention | `ReadReceiptRetentionJanitor` + `READ_RECEIPT_RETENTION_DAYS` |
| Admin | `GET /admin/read-receipts/stats`, раздел `core-read-receipts` |
| Smoke | `scripts/smoke-read-receipts.ps1` |
| Тесты | `MessageReadReceiptRepositoryH2Test`, privacy column в `UserRepositoryH2Test` |

---

## Шаги реализации (закрыты)

### 1. Таблица `message_read_receipts`

**1.1. Миграция V026**
- [x] `V026__message_read_receipts.sql` (см. репозиторий)
- [x] `docs/db/FLYWAY_AND_SCHEMA.md` — V026/V027

### 2. REST эндпоинты

**2.1. POST /v1/chats/{chatId}/messages/{messageId}/read**
- [x] `MessageReadReceiptRepository.insert`
- [x] Аудит `message.read`
- [x] `MessageReadReceiptRepositoryH2Test`

**2.2. GET /v1/chats/{chatId}/read-receipts`
- [x] `findByMessageId` + пагинация
- [x] H2 query-side tests

**2.3. POST /v1/chats/{chatId}/read-batch`
- [x] Batch insert + `READ_RECEIPT_BATCH_MAX`

**2.4. DTO**
- [x] `ReadReceiptResponse`, `BatchReadRequest`, `ReadReceiptEvent`

### 3. OpenAPI — [x]

### 4. Prometheus — [x] `ReadReceiptMetrics`

### 5. NATS — [x] `NatsSubjects.MSG_READ_RECEIPT`

### 6. ws-gateway — [x] pipeline fan-out + web-client handler

### 7. Retention — [x] janitor + worker integration

### 8. Web-client UI — [x] ✓✓, read-batch в `markChatRead()`

### 9. Приватность — [x] `privacy_disable_read_receipts` (V027)

### 10. Admin UI — [x]

### 11. Smoke — [x] `smoke-read-receipts.ps1` (UI Selenium — manual/deferred)

---

## Критерии завершения

- [x] `POST /read` создаёт запись в `message_read_receipts`.
- [x] `POST /read-batch` создаёт записи для нескольких сообщений.
- [x] `GET /read-receipts` возвращает список.
- [x] WebSocket доставляет событие участникам.
- [x] Web-client отображает read receipts.
- [x] Старые записи удаляются через `READ_RECEIPT_RETENTION_DAYS`.
- [x] Admin UI: статистика отображается.
- [x] Smoke: `smoke-read-receipts.ps1` (API/WS; UI automation optional).

---

## Отложено / вне scope

- Popup со списком прочитавших при hover (базовые ✓✓ реализованы).
- Партиционирование таблицы при росте объёма (операционная задача).
