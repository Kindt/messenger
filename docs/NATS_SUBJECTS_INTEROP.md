# NATS: канонические subject’ы (interop core-api ↔ workers ↔ ws-gateway)

Источник правды в коде: **`com.avandocmsg.messenger.common.nats.NatsSubjects`** (модуль **`modules/common`**). Имена **не менять** без согласования с ws-gateway и воркерами.

| Subject | Назначение | JSON / полезная нагрузка | Кто публикует | Кто потребляет |
|--------|------------|--------------------------|---------------|----------------|
| **`msg.send`** | Исходящее сообщение в pipeline (JetStream при включённом JS) | см. **`MessageService`** / pipeline | **core-api** | **message-pipeline** worker |
| **`rtc.signal`** | WebRTC signaling (mesh); fan-out в **`msg.deliver.{peer}`** после проверки членства в чате | JSON **`RtcSignalEvent`** (`type`, `chatId`, `fromUserId`, `payload`: offer / answer / candidate / hangup) | **ws-gateway** (из **WebSocket**) | **message-pipeline** worker |
| **`msg.deliver.{userId}`** | Доставка клиенту (per-user) | payload сообщения/события | воркеры / pipeline | **ws-gateway** (`MessagingWebSocket` подписывается на префикс + `sub`) |
| **`msg.typing`** | Набор текста | **`TypingEvent`** | клиент / gateway (по ТЗ) | подписчики |
| **`msg.event.index`** | Событие для индексации (Solr и т.д.) | **`MessageWorkerEvent`** (поле **`index_op`**: upsert при отсутствии, **`update`** после правки, **`delete`** при мягком удалении) | **message-pipeline** (новые сообщения), **core-api** **`MessageService`** (edit/delete), **`RetentionWorker`** (очистка тела по политике, **`index_op=update`**) | indexer worker |
| **`msg.event.retention`** | Факт применения ретенции к сообщению в Hot | JSON **`RetentionAppliedEvent`** (`modules/common`): **`message_id`**, **`chat_id`**, **`action`** (сейчас **`hot_body_cleared`**), **`applied_at_epoch_ms`**, **`cleared_content_utf8_bytes`**, **`snapshot_version`** (**`int`**, как в MinIO JSON, сейчас **`1`** — **`ArchiveSnapshotFormat.SNAPSHOT_VERSION`**; в старых payload без поля при десериализации подставляется **`1`**), опционально **`storage_object_key`** (ключ фактического снимка в MinIO, в т.ч. при пропуске **`putObject`**), опционально **`pass_id`** (**`string`**, UUID одного прохода **`RetentionHotBodyJanitor.runOnce`**; в старых payload без поля → **`null`**), опционально **`snapshot_sha256`** (**`string`**, 64 hex-символа в нижнем регистре — SHA-256 UTF-8 JSON **конверта снимка до добавления этого поля**, тот же алгоритм, что корень MinIO JSON; при отсутствии в старых payload → **`null`**; при отключённом MinIO в воркере или без материализации снимка → **`null`**; в **`RETENTION_DRY_RUN=true`** событие не публикуется) | **`RetentionWorker`** | подписчики аудита / метрик (отдельного consumer в репозитории нет) |
| **`msg.event.push`** | Push | **`MessageWorkerEvent`** | pipeline | push worker |
| **`msg.event.bot`** | Bot / webhook | **`MessageWorkerEvent`** | pipeline | bot worker |
| **`msg.event.deep-archive`** | Архивация | **`MessageWorkerEvent`** | pipeline | archiver |
| **`msg.export.replay`** | Запрос replay/экспорта чата | **`ExportReplayJob`** | **core-api** `ExportResource` | **export-replay** worker |
| **`msg.export.replay.cancel`** | Отмена экспорта (подсказка воркеру; источник правды — **`export_jobs.status=export_cancelled`**) | **`ExportReplayCancelEvent`** (`job_id`, `chat_id`, `cancelled_at_epoch_ms`) | **core-api** `ExportCancelPublisher` после **DELETE** export (user/admin) | **export-replay** worker (queue `export-replay-workers`; опрос БД в цикле экспорта) |
| **`msg.export.replay.complete`** | Завершение экспорта (`export_v1` / `stub_written` / `export_failed`) | **`ExportReplayCompleteEvent`** (`job_id`, `chat_id`, `status`, `output_path`, `message_ttl_filter_applied`) | **export-replay** worker | **core-api** `ExportReplayCompleteSubscriber` (синхронизация **`export_jobs`**, queue `core-api-export-complete`); статус также в БД воркером и **GET** `/v1/chats/{chatId}/export/{jobId}` |
| **`msg.export.suggested`** | Подсказка compliance: перед очисткой hot-body сделать export чата | **`ExportSuggestedEvent`** (`chat_id`, `reason`, `candidate_message_count`, `suggested_at_epoch_ms`) | **retention** worker (`RETENTION_PUBLISH_EXPORT_SUGGESTED=true`, до обработки батча) | **core-api** `ExportSuggestedSubscriber` → **`audit_events`** `export.suggested` (queue `core-api-export-suggested`); опционально **`EXPORT_AUTO_QUEUE_ON_SUGGESTED=true`** → **`msg.export.replay`** + `export.auto_queued` (дедуп по pending/cooldown) |

**Корреляция с админским аудитом (HTTP):** тот же UUID прохода, что **`RetentionAppliedEvent.pass_id`** в **`msg.event.retention`**, попадает в **`audit_events.details_json`** как **`pass_id`** (построчно **`message.retention.hot_body_cleared`** и в сводке **`message.retention.bulk_cleared`**; у сводки он же в колонке **`resource_id`**). Список событий: **`GET /api/v1/admin/audit-events`** (фильтры **`action`**, **`resource_type`**, **`resource_id`**). Подробности — **`docs/RETENTION_AND_DEEP_ARCHIVE.md`** §8.

Объект MinIO **`messages/{messageId}.json`**, который пишет **`DeepArchiverWorker`**, — JSON события с **`msg.event.deep-archive`** плюс на корне поля **`snapshot_version`** и **`producer`** (см. **`ArchiveSnapshotFormat`** в **`modules/common`**); по NATS subject по-прежнему уходит только **`MessageWorkerEvent`**.

## ws-gateway

Модуль **`modules/ws-gateway`** импортирует **`NatsSubjects`** и подписывается на **`msg.deliver.`** + **`userId`** из JWT **`sub`** — см. **`MessagingWebSocket`**. Клиент может отправлять в сокет JSON **`rtc_signal`** (см. **`RtcSignalEvent`**); шлюз публикует в **`rtc.signal`**.

## Переменные окружения

Связанные с NATS ключи задаются через **`AppConfig`** (**`NATS_URL`**, **`NATS_JETSTREAM`**, …), см. **`application.properties`** и **`AppConfig.overrideFromEnv`**.
