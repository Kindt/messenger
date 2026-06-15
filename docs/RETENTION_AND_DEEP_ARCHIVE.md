# Ретенция данных и deep-archive: проект (AvandocMsg / Korus Messenger)

Документ фиксирует **целевую модель** сроков хранения и глубокого архива в терминах базового ТЗ (`tz_full.html`: Hot DB, Archive DB, Deep Archive, TTL) и текущего кода репозитория. **Реализация** политик, воркеров очистки и админских API выносится на последующие этапы (после согласования продукта и юридических требований).

---

## 1. Термины и цели

| Термин | Смысл в продукте |
|--------|------------------|
| **Hot DB** | PostgreSQL «оперативной» БД (`messages`, `file_metadata`, …) — низкая задержка чтения/поиска. |
| **Archive DB** | Отдельная БД для метаданных истории (уже есть воркер **`ArchiverWorker`**, таблица **`archive_message_meta`** при `ARCHIVE_JDBC_URL`). |
| **Deep Archive** | Объектное хранилище (MinIO/S3): сжатые чанки тел/снимков состояния по политике (воркер **`DeepArchiverWorker`**, ключи вида `messages/{messageId}.json`). |
| **Soft-delete** | Флаг **`messages.deleted`** — сообщение скрыто из UI, строка в Hot DB сохраняется. |
| **TTL сообщения** | Срок **видимости** или срок до **переноса в холодный слой** (в ТЗ — «скрытие без физического удаления» vs политика deep-archive; нужно развести сценарии). |
| **Политика ретенции** | Набор правил: для кого (организация, чат, тип данных), через сколько что происходит (архив метаданных, вынос тела в deep, удаление бинарника, вычистка Solr). |

**Цели проектирования**

1. Соответствие регуляторным и продуктовым срокам (минимум/максимум хранения, экспорт до удаления).
2. Предсказуемое поведение для **E2EE** (`e2ee-*`): на сервере хранится ciphertext — deep-archive не «расшифровывает», только упаковывает байты и метаданные.
3. Идемпотентность и возобновляемость воркеров (повторный прогон без дублей в MinIO / без потери ссылок на чанки).
4. Развязка **удаления у пользователя** (soft-delete) и **истечения политики** (жизненный цикл в холодных слоях).

---

## 2. Текущее состояние репозитория (опорные точки)

- **Схема Hot DB:** в **`messages`** есть **`visibility_ttl_seconds`** (ранее **`ttl_seconds`**, V023 rename) и **`archive_ttl_seconds`** (новое поле V023). **`POST .../messages`:** опциональные поля **`visibility_ttl_seconds`** и **`archive_ttl_seconds`** в **`SendMessageRequest`** (1…**`MESSAGE_VISIBILITY_TTL_MAX_SECONDS`** / **`MESSAGE_ARCHIVE_TTL_MAX_SECONDS`**); лента/поиск/одиночный GET не возвращают истёкшие по visibility TTL сообщения (предикат **`MessageRepository.SQL_MSG_VISIBILITY_TTL_VISIBLE`**). Тело истекает по archive TTL через механизм ретенции.
- **Цепочка событий:** **`msg.send`** → pipeline → **`msg.event.index`** (и др.); **`ArchiverWorker`** пишет метаданные в Archive DB и публикует **`msg.event.deep-archive`**; **`DeepArchiverWorker`** кладёт JSON события в MinIO при настроенных кредах.
- **Файлы:** публичные ссылки с **`expires_at`** и конфигом **`file.public.link.default.ttl.seconds`** — это **TTL ссылки**, не ретенция тела файла в бакете.
- **Поиск:** Solr / SQL; при окончательном выносе или удалении контента из Hot DB нужна согласованная очистка индекса (см. уже реализованные **`index_op`** в **`MessageWorkerEvent`**).
- **Организации:** **`users.org_id`**, админские эндпоинты организаций — естественный якорь для **политики на уровне org** (см. раздел 4).

---

## 3. Слои данных и допустимые переходы

Предлагается явная **модель состояний** для строки сообщения в Hot DB (концептуально; в БД можно хранить одно поле **`retention_state`** + **`retention_next_action_at`**):

```
ACTIVE → ELIGIBLE_ARCHIVE → ARCHIVED_METADATA_ONLY → DEEP_PACKED → (опционально) HOT_ROW_PURGED / REDACTED
```

- **ACTIVE** — обычная жизнь; soft-delete остаётся отдельным флагом **`deleted`**.
- **ELIGIBLE_ARCHIVE** — наступил срок по политике (или размер очереди); воркер может скопировать метаданные в Archive DB (уже близко к текущему archiver на каждое событие — см. раздел 6).
- **ARCHIVED_METADATA_ONLY** — в Hot DB остаётся «каркас» (id, chat_id, sender_id, timestamps, type, возможно hash контента); **`content`** очищен или заменён на ссылку **`deep_archive_ref`**.
- **DEEP_PACKED** — тело (или ciphertext) записано в объектное хранилище; в Hot DB — указатель на объект + checksum.
- **HOT_ROW_PURGED / REDACTED** — опционально для агрессивной экономии места: удаление строки из Hot при наличии полной копии в Archive + Deep и прохождении legal-hold проверок (опасная операция — только после явного этапа проектирования FK и экспорта).

**Файлы (`file_metadata`)** — отдельная траектория: срок хранения бинарника может быть короче, чем у сообщения, если файл не уникален; нужна дедупликация по ссылкам из сообщений (UUID в `content` для не-E2EE).

---

## 4. Модель политики (данные)

Рекомендуется ввести таблицы (имена черновые; миграция **`V011+`** после утверждения):

### 4.1 `retention_policy` (уровень организации)

| Поле | Назначение |
|------|------------|
| `org_id` | FK на организацию; `NULL` = дефолт платформы. |
| `hot_message_body_max_age_days` | После чего тело сообщения выносится из Hot (в deep) или обнуляется по политике. |
| `hot_metadata_min_age_days` | Минимальный срок хранения метаданных в Hot (комплаенс). |
| `archive_metadata_enabled` | Вести ли запись в Archive DB (дублирование с archiver). |
| `deep_archive_enabled` | Разрешить упаковку в MinIO. |
| `legal_hold` | Булево: при `true` автоматические переходы **заморожены** для org. |
| `updated_at`, `updated_by` | Аудит смены политики. |

### 4.2 `chat_retention_override` (опционально)

Переопределение для чата (групповой чат с коротким TTL, «секретный» канал с длинным и т.д.): те же числовые поля, FK **`chat_id`**, приоритет выше, чем у org.

### 4.3 Сообщение: использование visibility/archive TTL

Два независимых сценария (продукт должен выбрать или поддержать оба):

1. **Visibility TTL (`visibility_ttl_seconds`):** после `created_at + visibility_ttl_seconds` сообщение скрывается из ленты/поиска для обычных пользователей, но сохраняется для аудита и жизненного цикла ретенции.
2. **Archive TTL (`archive_ttl_seconds`):** определяет срок до выноса тела сообщения в deep-archive/retention-пайплайн (при включенных политиках и воркерах).

Текущий API разведен на `visibility_ttl_seconds` и `archive_ttl_seconds`; legacy alias `ttl_seconds` для send **удалён** (2026-06-15).

---

## 5. Воркеры и расписание

| Компонент | Роль |
|-----------|------|
| **Новый `retention-worker`** (или расширение **archiver**) | Периодически (cron / `schedule.every`) выбирает пачки сообщений по **`retention_next_action_at`**, применяет политику: копирование в Archive (если ещё не), вызов deep-pack, очистка `content`, публикация события в NATS для indexer/Solr. |
| **Существующий archiver** | Сегодня реагирует на **каждое** `msg.event.index`; при введении политики можно либо оставить «полный снимок при создании», либо перевести archiver на **батч** только для сообщений с флагом «требуется архив» — решение за нагрузкой. |
| **DeepArchiverWorker** | Уже пишет JSON; для больших объёмов проектировать **чанки** (`messages/{id}/part-NNN.wgz`) и манифест с хешами. |

**Идемпотентность:** для каждого перехода — уникальный ключ операции в таблице **`retention_job_log(message_id, action, applied_at)`** или использование **`ON CONFLICT`** на состоянии.

**NATS:** subject **`msg.event.retention`** (JSON **`RetentionAppliedEvent`** в **`modules/common`**) публикуется **`RetentionWorker`** после успешной очистки **`messages.content`** (вместе с **`msg.event.index`** для Solr); в событии задаётся **`pass_id`** (UUID строкой) — общий идентификатор прохода для корреляции с логами и сводным аудитом **`retention_pass`**. Отдельный consumer в репозитории не обязателен — событие для аудита/метрик и будущих подписчиков.

---

## 6. Согласование с текущим archiver / deep-archive

Сегодня **каждое** индексируемое событие может порождать запись в Archive DB и объект в MinIO. Целевая модель:

- **Фаза A (минимальная):** политика только ограничивает **удаление/очистку** из Hot и **не меняет** частоту событий archiver.
- **Фаза B:** archiver пишет в Archive DB **только** если `archive_metadata_enabled` и сообщение старше порога **или** по событию создания (как сейчас) — зафиксировать в продукте, чтобы не плодить дубликаты.
- **Deep:** перенос **тела** (`content`) в объектное хранилище по возрасту/политике; в Hot оставить nullable **`content_deep_uri`** или вынести в таблицу **`message_storage_refs`**.
- **Единый контур MinIO (этап 3, опционально):** `DeepArchiverWorker` пишет **`messages/{messageId}.json`** в бакет **`MINIO_BUCKET`**; `RetentionWorker` по умолчанию пишет **`{RETENTION_MINIO_OBJECT_PREFIX}{message_id}.json`** в **`RETENTION_MINIO_BUCKET`** или том же **`MINIO_BUCKET`**. Чтобы не дублировать JSON-снимок, при **`RETENTION_SKIP_SNAPSHOT_IF_DEEP_EXISTS=true`** воркер ретенции перед `putObject` делает **`statObject`**: если бакет записи ретенции **совпадает** с **`MINIO_BUCKET`** (стабильное сравнение в коде), проверяется ключ deep-archive; иначе проверяется только ключ снимка ретенции в целевом бакете. При пропуске загрузки **`UPDATE messages SET content = NULL`**, NATS и аудит выполняются как раньше; в лог применения попадает фактический ключ объекта (`messages/…` или префикс ретенции). По умолчанию флаг **`false`** (безопасно).
- **Унификация JSON-конверта (этап 3, минимальный конверт):** в корне объекта MinIO (и снимок ретенции, и объект deep-archive после NATS-события) добавлены поля **`snapshot_version`** (**`int`**, сейчас **`1`**) и **`producer`** (**`string`**: **`retention-worker`** / **`deep-archiver`**). Константы и имена полей — **`com.avandocmsg.messenger.common.retention.ArchiveSnapshotFormat`**. Старые полезные поля не удаляются; потребители, не знающие конверт, продолжают читать прежние ключи.
- **Снимок ретенции в MinIO:** при загрузке JSON hot-body воркером в корень добавляется **`pass_id`** (UUID строкой) — тот же идентификатор прохода, что в **`msg.event.retention`** / **`RetentionAppliedEvent`** и сводном аудите **`retention_pass`**; ключ отсутствует в сериализации, если значение не задано (обратная совместимость). В корень добавляется **`snapshot_sha256`** (**`ArchiveSnapshotFormat.JSON_SNAPSHOT_SHA256`**): **нижний регистр**, **64** hex-символа — SHA-256 по **UTF-8 байтам JSON того же объекта до добавления свойства `snapshot_sha256`** (тот же **`ObjectMapper`**, что и **`putObject`/`uploadObject`**). Загружаемый в MinIO документ **включает** поле; проверка целостности: распарсить JSON, удалить **`snapshot_sha256`**, сериализовать остаток тем же порядком полей / настройками, сравнить digest с сохранённым значением. То же значение публикуется в **`RetentionAppliedEvent.snapshot_sha256`** и (при **`RETENTION_AUDIT_ENABLED`**) в **`details_json`** построчного аудита **`message.retention.hot_body_cleared`**; в сводном **`message.retention.bulk_cleared`** поля **`snapshot_sha256`** **нет** (много сообщений за проход).
- **Deep-archive `messages/{messageId}.json`:** **`DeepArchiverWorker`** добавляет в корень тот же **`snapshot_sha256`** (**`ArchiveSnapshotFormat.JSON_SNAPSHOT_SHA256`**) с **той же** семантикой digest (UTF-8 JSON **до** поля, затем поле в объекте, загружаемом в MinIO **`putObject`**); общий хелпер **`ArchiveSnapshotEnvelopeDigest`** в **`modules/common`**. Модуль **`modules/common`** публикует **Jackson** как **`api`** (**`jackson-databind`**, **`jackson-annotations`**), чтобы воркеры использовали **`ArchiveSnapshotEnvelopeDigest`** с теми же типами **`ObjectMapper`**, что и загрузка в MinIO.

---

## 7. Поиск и Solr

При очистке **`content`** в Hot для сообщения, участвующего в Solr с **`content_txt`**, воркер обязан опубликовать **`MessageWorkerEvent`** с **`index_op: update`** (пустой поисковый текст) или **`delete`**, если строка удаляется из Hot полностью — см. реализацию п. **59–60** в **`IndexerWorker`**.

---

## 8. Админский API и аудит

- **`GET/PATCH /api/v1/admin/organizations/{orgId}/retention`** — политика org (роль **`admin`**); аудит **`organization.retention.set`** на **PATCH**.
- **`GET/PATCH /api/v1/admin/chats/{chatId}/retention`** — эффективная политика чата: дефолты платформы → org (по **`users.org_id`** владельца чата, иначе первая org среди участников с непустым **`org_id`**, порядок ролей owner → admin → member) → строка **`chat_retention_policy`** (**`V012`**), если есть. Тело **PATCH** — как у org (**`UpdateRetentionPolicyRequest`**); аудит **`chat.retention.set`**. Если базовую org вывести нельзя, слой org в ответе — только дефолты платформы (**`RetentionPolicyResponse.platformDefaults`**), поле **`base_org_id`** в JSON — **`null`**.
- **`AuditRepository.record`** на каждое изменение политики и на массовые операции воркера (порог: N записей за прогон).
- Другие события **`audit_events`** из **`core-api`** (не воркер ретенции): **`organization.create`** / **`organization.delete`** — в **`details_json`** объект **`{"name":"..."}`** (имя после создания / на момент удаления); **`user.organization.set`** — **`{"org_id":"<uuid>"}`**; **`file.public_link.create`** / **`file.public_link.revoke`** — **`link_id`**, при создании также **`kind`**; **`organization.retention.set`** и **`chat.retention.set`** — поля эффективной политики (**`hot_message_body_max_age_days`**, **`hot_metadata_min_age_days`** при отсутствии значения — JSON **`null`**, **`archive_metadata_enabled`**, **`deep_archive_enabled`**, **`legal_hold`**). Сериализация через Jackson (**`AdminResource`**, **`FileResource`**), без конкатенации строк вручную.
- **`RetentionWorker`:** при **`RETENTION_AUDIT_ENABLED=true`** (по умолчанию) после успешной очистки тела пишет строку в **`audit_events`**: **`action`** = **`message.retention.hot_body_cleared`**, **`resource_type`** = **`message`**, **`resource_id`** = id сообщения, **`actor_user_id`** = **`NULL`**, **`details_json`** — **`chat_id`**, длина очищенного UTF-8, опционально **`pass_id`** (UUID прохода — тот же, что **`msg.event.retention.pass_id`** и **`resource_id`** у **`message.retention.bulk_cleared`**), опционально **`storage_object_key`**, опционально **`snapshot_sha256`** (тот же digest, что в MinIO / NATS, когда снимок материализован воркером с MinIO). Сбой вставки не откатывает очистку Hot (только предупреждение в логе воркера). Связка NATS ↔ HTTP: **`docs/NATS_SUBJECTS_INTEROP.md`** (абзац после таблицы subject’ов — корреляция **`pass_id`** с **`audit_events`**). Просмотр в админке: **`GET /api/v1/admin/audit-events`** — опциональные query **`action`**, **`resource_type`**, **`resource_id`**: заданные параметры объединяются через **AND** (точное совпадение с колонками **`audit_events`**). Примеры ретенции: построчно — **`action=message.retention.hot_body_cleared`**, при необходимости **`resource_type=message`** и **`resource_id=<id сообщения>`**; сводка прохода — **`action=message.retention.bulk_cleared`**, **`resource_type=retention_pass`**, **`resource_id=<pass_id>`** (тот же UUID, что **`pass_id`** в **`msg.event.retention`** / **`RetentionAppliedEvent`**).
- **Сводный аудит прохода:** если **`RETENTION_BULK_AUDIT_MIN_CLEARED`** задан больше **`0`** и за один проход успешно очищено тел **не меньше** этого порога, в **`audit_events`** добавляется **одна** строка: **`action`** = **`message.retention.bulk_cleared`**, **`resource_type`** = **`retention_pass`**, **`resource_id`** = UUID прохода (тот же идентификатор, что **`pass_id`** в NATS **`RetentionAppliedEvent`** за этот проход); **`details_json`**: **`pass_id`** (дубликат UUID строкой — удобно парсить только JSON), время прохода, **`cleared_count`**, **`batch_limit`**, **`candidate_count`**, **`errors_count`**, **`duration_ms`**, опционально **`sample_chat_ids`** (до 5 уникальных `chat_id` из кандидатов). Не отключает построчный аудит выше — при включённом **`RETENTION_AUDIT_ENABLED`** строки **`message.retention.hot_body_cleared`** остаются; сводка **дополнительная**.
- Экспорт (**`ExportResource`**) перед агрессивной фазой **HOT_ROW_PURGED** — обязательный шаг в runbook, не обязательно автомат в MVP.

---

## 9. Конфигурация (`AppConfig`) — ориентир

Переменные окружения:

- **`RETENTION_WORKER_ENABLED`** (по умолчанию `false`), **`RETENTION_SCAN_INTERVAL_SECONDS`** (по умолчанию `3600`), **`RETENTION_INITIAL_DELAY_SECONDS`** (по умолчанию `0`, макс. `86400`) — в **`AppConfig`** для первых двух (`retention.worker.enabled`, `retention.scan.interval.seconds`) и в процессе **`RetentionWorker`** (`modules/workers/retention`, те же env для интервала; задержка старта — только env). При **`RETENTION_WORKER_ENABLED=true`**: подключение к Hot PostgreSQL (**`DB_JDBC_URL`**), к NATS (**`NATS_URL`**) и (по умолчанию обязательно) к MinIO (**`MINIO_*`**) — см. ниже.
- **Остановка процесса (SIGTERM / JVM exit):** один shutdown-hook: выставляется флаг кооперативной остановки, однопоточный планировщик сканов получает **`shutdown`** и ожидание завершения (**до ~15 с**); затем по порядку закрываются HTTP метрик (**`/metrics`**, **`/health`**), соединение NATS, пул **`HikariDataSource`** — с **`WARN`** в лог при ошибке на каждом шаге. Новый проход hot-body после сигнала не стартует; уже выполняющийся проход и текущий **`processOne`** завершаются по возможности до таймаута ожидания (**best-effort**). Повторный вызов hook идемпотентен.
- **`RETENTION_DRY_RUN`** (по умолчанию **`false`**): при **`true`** процесс по-прежнему может подключаться к БД/NATS/MinIO для старта и пинга, но **каждый** проход hot-body **не мутирует** данные: выполняется тот же **`SELECT`** кандидатов, в лог (**`INFO`**) — одна строка с **`pass_id`** (UUID прохода), **`candidates`**, **`would_clear`** (равно числу кандидатов в пачке), **`dry_run=true`**; **без** **`UPDATE messages SET content = NULL`**, **без** **`putObject`/`statObject`** на пути реальной мутации (ветка обработки сообщений не вызывается), **без** **`INSERT`** в **`retention_hot_body_applied`**, **без** построчного и **без** сводного **`audit_events`**, **без** публикации **`msg.event.index`** / **`msg.event.retention`** (в т.ч. нет **`snapshot_sha256`** в NATS — снимки не материализуются). При старте в лог пишется явное **`RETENTION_DRY_RUN=true`**. Счётчик Prometheus **`retention_worker_dry_run_passes_total`** увеличивается на **1** за каждый такой проход (гистограмма числа кандидатов за проход — та же **`retention_worker_hot_body_pass_candidates`**, что и в обычном режиме).
- **`RETENTION_USE_ADVISORY_LOCK`** (по умолчанию **`false`**): при **`true`** и **`DB_JDBC_URL`**, начинающемся с **`jdbc:postgresql:`**, перед проходом hot-body (включая **dry-run**, чтобы не дублировать тяжёлый **`SELECT`** между репликами) на **одном** JDBC-соединении выполняется **`pg_try_advisory_lock`** с фиксированной парой ключей (**`RetentionAdvisoryLockIds`**, session lock). Удержание до конца прохода; в **`finally`** — **`pg_advisory_unlock`** на том же соединении, если блокировка была получена. Если **`pg_try_advisory_lock`** вернул **`false`**, в лог (**`INFO`**) — пропуск прохода **без** **`SELECT`**, счётчик **`retention_worker_pass_skipped_advisory_lock_total`**, возврат **`0`** очищенных. При **`false`** или не-PostgreSQL JDBC — поведение как раньше (без advisory SQL).
- **`RETENTION_METRICS_PORT`** (по умолчанию `0`): если задан порт `1…65535`, после успешной инициализации процесса поднимается HTTP на этом порту: **`GET /metrics`** (Prometheus text, те же клиентские библиотеки, что у **`core-api`**: JVM default exports + счётчики/гистограммы воркера: проходы ретенции, очистки тел, MinIO, ошибки, пинг БД) и **`GET /health`** — **`200`** с телом **`ok`**, если воркер считается готовым (см. §9.1); иначе **`503`** и **`not ready`** (plain text, без секретов). При пропуске `putObject` из‑за уже существующего объекта — счётчик **`retention_worker_minio_snapshot_skipped_existing_total`** с меткой **`reason`** (`deep` / `retention`).
- **`RETENTION_BATCH_LIMIT`** (по умолчанию `25`, макс. `500`), **`RETENTION_REQUIRE_MINIO`** (по умолчанию `true`), **`RETENTION_USE_APPLIED_LOG`** (по умолчанию `true`), **`RETENTION_AUDIT_ENABLED`** (по умолчанию `true`), **`RETENTION_BULK_AUDIT_MIN_CLEARED`** (по умолчанию `0` = выключено), **`RETENTION_SKIP_SNAPSHOT_IF_DEEP_EXISTS`** (по умолчанию **`false`**): пакетный проход выбирает сообщения старше эффективного **`hot_message_body_max_age_days`** (платформа → **`org_retention_policy`** → **`chat_retention_policy`**, при **`deep_archive_enabled`**) с непустым **`content`**, исключая уже обработанные (**`retention_hot_body_applied`**, миграция **`V013`**, если лог включён). **Legal hold:** в SQL воркера для кандидатов требуется **`eff_legal = false`** — при **`legal_hold = true`** в эффективной политике чата (если есть строка **`chat_retention_policy`**) или организации (**`org_retention_policy`**, иначе дефолт платформы **`RETENTION_DEFAULT_LEGAL_HOLD`**) сообщения **не** попадают в выборку и тело **не** очищается. При настроенном MinIO перед записью снимка (если флаг включён) может быть выполнен **`statObject`** по ключу **`messages/{message_id}.json`** только когда бакет снимков ретенции **совпадает** с **`MINIO_BUCKET`**; иначе — только проверка ключа **`{RETENTION_MINIO_OBJECT_PREFIX}{message_id}.json`** в целевом бакете. Затем при необходимости **`putObject`** снимка тела в **`{RETENTION_MINIO_OBJECT_PREFIX}{message_id}.json`**, **`UPDATE messages SET content = NULL`**, публикация **`msg.event.index`** (**`index_op=update`**) и **`msg.event.retention`** (**`RetentionAppliedEvent`**), затем **`INSERT`** в **`retention_hot_body_applied`**, при включённом аудите — **`INSERT`** в **`audit_events`** (построчно); при **`RETENTION_BULK_AUDIT_MIN_CLEARED` > 0** и **`cleared_count` ≥ порога** — ещё одна сводная строка **`message.retention.bulk_cleared`** (см. §8). SQL только для **`jdbc:postgresql:`**; иначе проход пропускается с предупреждением в логе. Если **`RETENTION_REQUIRE_MINIO=false`**, вынос в объектное хранилище не выполняется (риск потери тела при очистке Hot — только для отладки). **`RETENTION_USE_APPLIED_LOG=false`** — без фильтра/записи таблицы **`retention_hot_body_applied`** (например до применения **`V013`**).
- **`RETENTION_JDBC_QUERY_TIMEOUT_SECONDS`** (по умолчанию **`0`**): при значении **`> 0`** воркер задаёт **`Statement.setQueryTimeout`** (секунды) для JDBC **`SELECT`** кандидатов hot-body и для **`UPDATE messages SET content = NULL`** на пути очистки; при **`0`** лимит не задаётся (поведение драйвера по умолчанию). Таблица **`retention_hot_body_applied`** (**`V013`**) уже имеет **PK по `message_id`** (анти‑join **`NOT EXISTS`**) и индекс по **`applied_at`** — отдельный индекс под этот фильтр обычно не требуется.
- **`RETENTION_INTER_MESSAGE_DELAY_MS`** (по умолчанию **`0`** = без паузы): после каждого обработанного кандидата в пачке hot-body (успех или ошибка **`processOne`**) перед следующим — пауза **`Thread.sleep`** в миллисекундах; после **последнего** кандидата в пачке пауза **не** выполняется. Значение ограничено **`0…60000`**; при **`0`** вызовов **`sleep`** нет. Если поток прерывают во время паузы, флаг **`interrupt`** восстанавливается, проход **прерывается** (остальные кандидаты в пачке в этом цикле не обрабатываются), в лог — предупреждение с числом уже очищенных тел и ошибок. Не применяется в режиме **`RETENTION_DRY_RUN=true`** (ветка по‑сообщению не выполняется). Парсинг: **`RetentionPlatformDefaults.interMessageDelayMsFromEnv`**.
- **`RETENTION_SNAPSHOT_TEMPFILE_THRESHOLD_BYTES`** (по умолчанию **`0`** = выключено): при значении **`> 0`** и **строго большей** UTF-8 длине поля **`messages.content`** (строка из **`SELECT`**) порогу, снимок JSON материализуется во **временный файл** в **`java.io.tmpdir`** (префикс имени **`retention-snapshot-`**, суффикс **`.json`**), затем загрузка в MinIO: если **`Files.size(temp) >= RETENTION_MINIO_MULTIPART_THRESHOLD_BYTES`** (см. ниже), **`MinioClient.uploadObject`** (SDK **8.5.10**, multipart при необходимости), иначе **`MinioClient.putObject`** с **`InputStream`** и известным размером; файл удаляется в **`finally`**. При пороге **`0`**, при длине контента **равной** порогу или **меньше**, либо когда загрузка не выполняется (**`RETENTION_DRY_RUN`**, пропуск по **`statObject`**), поведение как раньше: **`ObjectMapper.writeValueAsBytes`** в памяти (или нет загрузки). Нечисловые и отрицательные значения env → **`0`**; верхняя граница разбора — **1 GiB** (**`RetentionPlatformDefaults.SNAPSHOT_TEMPFILE_THRESHOLD_BYTES_MAX`**). Счётчик **`retention_worker_minio_snapshot_tempfile_total`** — на **1** за каждую успешную загрузку снимка через temp-file путь (независимо от **`putObject`** / **`uploadObject`**).
- **`RETENTION_MINIO_MULTIPART_THRESHOLD_BYTES`**: только на **temp-file** пути; при **`Files.size(temp) >=`** порога — **`MinioClient.uploadObject`** с тем же ключом и **`Content-Type: application/json`**; иначе — **`putObject`** со стримом. Если env **не задан**, дефолт **`Long.MAX_VALUE`** (**`RetentionPlatformDefaults.MINIO_MULTIPART_THRESHOLD_BYTES_DEFAULT`**) — фактически только **`putObject`** для temp-file (поведение как до введения env). Нечисловые и **`<= 0`** → тот же дефолт. Типичное значение для включения multipart — **`33554432`** (32 MiB). Счётчик **`retention_worker_minio_multipart_uploads_total`** увеличивается на **1** после успешного **`uploadObject`**.
- **Уже в `AppConfig`:** `RETENTION_DEFAULT_HOT_BODY_MAX_AGE_DAYS`, `RETENTION_DEFAULT_HOT_METADATA_MIN_AGE_DAYS`, `RETENTION_DEFAULT_ARCHIVE_METADATA_ENABLED`, `RETENTION_DEFAULT_DEEP_ARCHIVE_ENABLED`, `RETENTION_DEFAULT_LEGAL_HOLD` (см. **`application.properties`**, ключи `retention.default.*`)
- Повторное использование существующих **`ARCHIVE_JDBC_*`**, **`MINIO_*`**. Для снимков тел ретенции: опционально отдельный бакет **`RETENTION_MINIO_BUCKET`** (иначе тот же, что **`MINIO_BUCKET`** / дефолт воркера **`deep-archive`**), префикс ключей **`RETENTION_MINIO_OBJECT_PREFIX`** (по умолчанию **`retention/body/`**, нормализуется без ведущего слэша и с завершающим **`/`**). При **`RETENTION_ENSURE_MINIO_BUCKET=true`** (по умолчанию) воркер при старте пытается создать бакет ретенции, если его ещё нет (как **`DeepArchiverWorker`**); при **`false`** бакет должен существовать (IaC / ручная настройка).

Значения по умолчанию должны быть **консервативными** (воркер выключен), чтобы деплой без DBA не запускал очистку.

### 9.1 Наблюдаемость (Prometheus и readiness)

| Эндпоинт (тот же порт, что **`RETENTION_METRICS_PORT`**) | Ответ |
|--------------------------------------------------------|--------|
| **`GET /metrics`** | Текст Prometheus (JVM **`jvm_*`** / **`process_*`** из default exports + метрики воркера ниже). |
| **`GET /health`** | **`200`** + **`ok`**: воркер **выключен** (**`RETENTION_WORKER_ENABLED=false`**) **или** (включён и) **`SELECT 1`** по Hot DB успешен, NATS в состоянии **`CONNECTED`**, и при **`RETENTION_REQUIRE_MINIO=true`** — MinIO настроен и бакет записи ретенции существует (**`bucketExists`**). Иначе **`503`** + **`not ready`**. |

В **`docker/docker-compose.dev-min.yml`** сервис **`retention-worker`** может объявлять **`healthcheck`**, который бьёт **`GET /health`** на порту **`RETENTION_METRICS_PORT`** внутри контейнера (тот же HTTP, что **`/metrics`**); при **`RETENTION_METRICS_PORT=0`** HTTP не поднимается — статус **`healthy`** у Compose недостижим без ненулевого порта.

Имена метрик воркера (default registry), кратко:

| Имя метрики | Смысл |
|-------------|--------|
| **`retention_worker_db_ping_failures_total`** | Неудачные пинги Hot DB |
| **`retention_worker_hot_body_cleared_total`** | Успешно очищено тело после снимка / пути отладки |
| **`retention_worker_hot_body_processing_errors_total`** | Ошибки обработки одного кандидата |
| **`retention_worker_hot_body_row_not_updated_total`** | **`UPDATE`** не затронул строку |
| **`retention_worker_minio_snapshot_uploads_total`** | Успешные записи снимков в MinIO (**`putObject`** или **`uploadObject`**) |
| **`retention_worker_minio_snapshot_tempfile_total`** | Успешные загрузки, где JSON снимка сначала записан во временный файл (см. **`RETENTION_SNAPSHOT_TEMPFILE_THRESHOLD_BYTES`**) |
| **`retention_worker_minio_multipart_uploads_total`** | Успешные **`uploadObject`** для больших temp-file снимков (см. **`RETENTION_MINIO_MULTIPART_THRESHOLD_BYTES`**) |
| **`retention_worker_minio_snapshot_skipped_existing_total`** | Пропуск **`putObject`** (метка **`reason`**: **`deep`** / **`retention`**) |
| **`retention_worker_pass_skipped_minio_required_total`** | Проход пропущен: MinIO обязателен, но не настроен |
| **`retention_worker_pass_skipped_advisory_lock_total`** | Проход пропущен: **`RETENTION_USE_ADVISORY_LOCK=true`** и **`pg_try_advisory_lock`** вернул **`false`** (другая сессия держит lock) |
| **`retention_worker_dry_run_passes_total`** | Завершённые проходы в **`RETENTION_DRY_RUN=true`** |
| **`retention_worker_audit_insert_failures_total`** | Сбой вставки в **`audit_events`** после очистки |
| **`retention_worker_hot_body_pass_duration_seconds`** | Длительность прохода (**histogram**) |
| **`retention_worker_hot_body_pass_candidates`** | Число кандидатов за проход (**histogram**) |
| **`retention_worker_last_hot_body_pass_epoch_seconds`** | Unix epoch (**gauge**): последний проход hot-body **после** успешного **`SELECT`** кандидатов (в т.ч. пустая выборка и **dry-run**); не обновляется при исключении до завершения, при пропуске **`RETENTION_REQUIRE_MINIO`**, при пропуске из‑за **`pg_try_advisory_lock`** (без **`SELECT`** кандидатов) |
| **`retention_worker_last_pass_cleared_count`** | **Gauge** (целое): число успешно очищенных тел в последнем таком проходе; в **dry-run** всегда **`0`** (не «would_clear») |
| **`retention_worker_minio_snapshot_bytes`** | Размер JSON снимка, байты (**histogram**) |
| **`retention_worker_build_info`** | **Info** (значение **1**): статические метки **`version`** (из manifest JAR воркера / **`Package#getImplementationVersion`**, иначе **`unknown`**) и **`name`** = **`retention-worker`** |

**База данных (Flyway)** — см. также **`docs/db/FLYWAY_AND_SCHEMA.md`**:

- **`V014`** — индексы списка **`audit_events`** (**`action`**, **`resource_id`** + **`occurred_at DESC`**) под админский **`GET .../audit-events`** с фильтрами и **`ORDER BY occurred_at`**.
- **`V015`** — частичный индекс на **`messages`** под выборку кандидатов hot-body (**`ORDER BY created_at`**, join к политике чата по **`chat_id`**).

---

## 10. Этапы внедрения (рекомендация)

**Укрупнённо (**§13**):** основной фокус репозитория здесь — **фаза A** (политики + hot-body + эксплуатация). Таблица этапов **0–4** ниже — детализация; **завершение фазы A** — стабильный hot-body и сопутствующий контур **без** перехода к чанкам/унификации тел (**хвост §10 этапа 3**), смене продуктовой семантики TTL (**этап 2**) и без purge/файлов (**фаза C** / **§12**).

| Этап | Содержание |
|------|------------|
| **0** | Утвердить этот документ + юридический минимум для целевых регионов. |
| **1** | **Частично сделано:** **`V011`**/**`V012`**, админ **`GET`/`PATCH`** org и чата, аудит смены политик; **`RetentionWorker`** (§9). **Сделано:** порт Prometheus (**`RETENTION_METRICS_PORT`**), сводный аудит (**`bulk_cleared`**), skip‑snapshot (**`RETENTION_SKIP_SNAPSHOT_IF_DEEP_EXISTS`**), регрессия SQL (**`RetentionHotBodyCandidateSqlTest`**), graceful shutdown воркера (§9), порог temp-file снимка (**`RETENTION_SNAPSHOT_TEMPFILE_THRESHOLD_BYTES`**), **`GET .../audit-events`**: фильтр **`resource_id`** (AND с **`action`**/**`resource_type`**) для **`pass_id`**/id сообщения. Дальше: чанки; TTL — этап 2. |
| **2** | **Сделано:** dual TTL в API/БД — `visibility_ttl_seconds` + `archive_ttl_seconds` (миграция `V023`, alias `ttl_seconds` для backward compatibility). Фильтр visibility TTL действует в чтении сообщений/поиске/доступе к файлам. Дальше: UI-индикатор TTL и эксплуатационные smoke для user-flow. |
| **3** | **Сделано:** hot-body pass, MinIO-снимок, `content=null`, `msg.event.index` (`update`) + `msg.event.retention`, `V013`; observability (`/metrics`, `/health`), bulk audit, skip-snapshot, advisory lock, temp-file/multipart, общий JSON envelope (`snapshot_version`/`producer`), `snapshot_sha256`. **Сделано в Phase B:** chunked deep-archive/retention (`manifest.json` + `part-*.json`) и унифицированное чтение через `DeepArchiveReader`. Дальше: финальная валидация Solr и web-client TTL. |
| **4** | Purge Hot row, ретенция **файлов** и связка с MinIO file bucket; **legal hold** для hot-body уже учитывается в SQL воркера (§9), дальше — расширения вне очистки тела (см. §12). |

---

## 11. Связанные файлы в репозитории

- Воркеры: **`modules/workers/archiver`**, **`modules/workers/deep-archiver`**, **`modules/workers/retention`** (hot-body pass)
- Docker (dev): **`docker/Dockerfile.retention-worker`**, сервис **`retention-worker`** в **`docker/docker-compose.dev-min.yml`** (профиль **`retention`**, не стартует без **`--profile retention`**); в корне репозитория **`.dockerignore`** — меньший контекст для **`docker build`** / Compose.
- Схема: **`V001`** (`ttl_seconds`), **`V011__org_retention_policy.sql`**, **`V012__chat_retention_policy.sql`**, **`V013__retention_hot_body_applied.sql`**, **`V014__audit_events_list_indexes.sql`**, **`V015__messages_retention_hot_body_candidate_index.sql`** (детали — **`docs/db/FLYWAY_AND_SCHEMA.md`**)
- API: **`AdminResource`** (org и chat retention)
- Дорожная карта эпиков: **`docs/ROADMAP_EPICS.md`** (раздел ретенции); детали — этот документ.
- Экспорт: **`ExportResource`**, **`NatsSubjects.MSG_EXPORT_REPLAY`**

---

## 12. Этап 4: черновик работ (purge строки Hot, файлы)

Краткий чеклист проектирования (без детализации SQL/Java). Контекст состояний и таблиц — **§3**, **§4**; текущий проход очистки тела в Hot и конфиг — **`RetentionWorker`**, **§9**.

### HOT row purge (удаление строки из Hot)

- **Предпосылки:** завершённый/доступный **экспорт** там, где он обязателен политикой; сняты или учтены ограничения **legal hold**; проработаны **FK** и ссылки с других сущностей (чтобы не оставить «висячие» записи и не сломать отчётность).
- **Связь с `ExportResource`:** перед агрессивной фазой **HOT_ROW_PURGED** экспорт — обязательный шаг в runbook (см. **§8**); для воспроизводимости рассмотреть **`MSG_EXPORT_REPLAY`** / сценарии выгрузки.
- **Риски:** потеря возможности показать сообщение из Hot без полной цепочки Archive + Deep; гонки с индексатором/Solr; ошибки при отсутствии копии в холодных слоях.

### `file_metadata` и бинарники в MinIO

- **Ретенция отдельно от строки сообщения:** срок в бакете может быть короче политики сообщения, если файл **дедуплицирован** и ещё используется другими сообщениями — нужен подсчёт ссылок (в т.ч. из **`messages.content`**: UUID/вложения для не-E2EE).
- **E2EE:** на сервере ciphertext и метаданные; политика удаления бинарника не должна предполагать чтение открытого текста — только ссылки, ключи объектов, согласованность с клиентом.

### Legal hold вне hot-body

- Сейчас **legal hold** для очистки **тела** в Hot уже в SQL **`RetentionWorker`** (**§9**); этап 4 — **расширения:** заморозка/учёт hold для **файлов** и для **архива / deep-archive** (отдельные флаги или единая модель — за продуктом).

---

## 13. Укрупнённые фазы для планирования (A / B / C)

Детальная шкала **§10** сохраняется для миграций и кода. Для обсуждений со стейкхолдерами удобно использовать **три крупные фазы**:

| Фаза | Содержание | Соответствие §10 |
|------|------------|------------------|
| **A** | Политики, hot-body в Hot DB, MinIO/NATS, аудит, эксплуатация (метрики, healthcheck, индексы), **без** purge строки и **без** отдельного контура ретенции файлов. | Этапы **1** и основная часть **3** (по репозиторию — в основном здесь и сейчас). |
| **B** | Семантика TTL/видимости vs deep-archive; крупные тела и унификация формата с deep-archive (чанки, единый контракт чтения). | Этап **2** + хвост этапа **3** в §10. |
| **C** | Purge строки Hot, ретенция **`file_metadata`** и бакета файлов, расширения **legal hold** вне hot-body. | Этап **4** (черновик — **§12**). |

**Автономная разработка (пока нет вопросов):** правки **обратимы**, **локальны** и **не меняют продуктовую семантику для пользователя** (или уже описаны в этом документе) — можно выполнять без отдельного раунда согласования. Переход к фазе **C** и спорные решения по фазе **B** — после явных ответов продукта/юристов или короткой фиксации в **`docs/ROADMAP_EPICS.md`**.

---

*Дополнение **2026-05-11:** §8 — формы **`details_json`** для операций **`core-api`** вне воркера ретенции (**`organization.*`**, **`user.organization.set`**, **`file.public_link.*`**, **`PATCH`** политик org/чата), сериализация Jackson.*

*Версия документа: 2026-05-10 (дополнение: **§10** — маркер текущей **фазы A** (**§13**) и граница завершения A vs B/C; **§13** — укрупнённые фазы **A/B/C** и правило автономной разработки; §9.1 — **`docker-compose.dev-min.yml`**: **`retention-worker`**, **`healthcheck`** → **`GET /health`** на **`RETENTION_METRICS_PORT`**; порт **`0`** — без HTTP/`healthy`; §9.1 / §11 — **Flyway `V014`** (индексы **`audit_events`** под список), **`V015`** (частичный индекс **`messages`** под кандидатов hot-body), отсылка **`docs/db/FLYWAY_AND_SCHEMA.md`**; **`GET .../audit-events`**: **`resource_id`** AND с **`action`**/**`resource_type`**; deep **`messages/{id}.json`**: **`snapshot_sha256`**, **`ArchiveSnapshotEnvelopeDigest`**, Jackson **`api`** на **`modules/common`**; **`RETENTION_USE_ADVISORY_LOCK`**, session **`pg_try_advisory_lock`** / **`pg_advisory_unlock`**, метрика **`retention_worker_pass_skipped_advisory_lock_total`**, **`RetentionAdvisoryLockIds`**; **`RETENTION_MINIO_MULTIPART_THRESHOLD_BYTES`**, **`uploadObject`**, метрика **`retention_worker_minio_multipart_uploads_total`**; graceful shutdown **`RetentionWorker`** (§9); **`RETENTION_SNAPSHOT_TEMPFILE_THRESHOLD_BYTES`**, **`retention_worker_minio_snapshot_tempfile_total`**; ранее: фаза 1 org + V011; чат V012 + admin; **`RetentionWorker`** — hot-body, Solr update, метрики, bulk‑аудит, skip‑snapshot, SQL‑регрессия кандидатов). При смене поведения воркеров или схемы — обновлять этот файл и при необходимости раздел ретенции в **`docs/ROADMAP_EPICS.md`**.*

*Дополнение **2026-05-08:** ссылки на закрытый трекер **`TZ_SERVER_100.md`** заменены на **`docs/ROADMAP_EPICS.md`**.*
