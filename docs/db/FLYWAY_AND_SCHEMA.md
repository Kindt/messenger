# Flyway и схема PostgreSQL (core-api)

См. также **`docs/CI_AND_REPO_HYGIENE.md`** (локальный **`./gradlew test`** и CI на GitHub).

## Идемпотентность (ТЗ п. 85)

- Версии **`Vnnn__*.sql`** применяются **ровно один раз** на базу (таблица **`flyway_schema_history`**).
- Новые правки — только **новый номер** миграции; уже применённые файлы **не переписывать** на проде (иначе расход checksum → нужен **`flyway repair`** и согласование с DevOps).
- Внутри скрипта предпочтительно: **`CREATE INDEX IF NOT EXISTS`**, **`ADD COLUMN IF NOT EXISTS`**, явные **`IF NOT EXISTS`** для объектов, если операция может повторяться при ручном прогоне на копии БД.

## Таблица `audit_events` (V001 → V008)

- В актуальном **`V001`** таблица **`audit_events`** **не** создаётся (схема только в **`V008`**).
- В начале **`V008`** выполняется **`DROP TABLE IF EXISTS audit_events CASCADE`**, чтобы на старых копиях БД убрать legacy-определение из прежнего V001 перед **`CREATE TABLE`** под **`AuditRepository`**. Не применяйте этот скрипт вручную повторно к уже заполненной **`audit_events`** с нужной схемой — будет потеря данных.
- После обновления файлов миграций на уже развёрнутой базе может потребоваться **`flyway repair`** (расход checksum с историей Flyway).

Текущий код (**`AuditRepository`**) ожидает колонки **`occurred_at`**, **`actor_user_id`**, **`action`**, … из **`V008`**.

## Внешние ключи и каскады (ТЗ п. 87)

Основная политика в **`V001`**–**`V009`**: каскадное удаление для **`chats` → `messages`**, **`chat_members`**, вложенных сущностей; **`users`** для части связей с **`ON DELETE CASCADE`** (**`devices`**, **`contacts`**, **`blocks`** и т.д.). Сообщения ссылаются на **`users(sender_id)`** без каскада (поведение по умолчанию **NO ACTION** / ограничение удаления пользователя с сообщениями — по продуктовой политике).

## Индексы (ТЗ п. 86)

Базовые индексы в **`V001`** и последующих версиях; дополнительные «горячие» пути — **`V010__hot_path_indexes.sql`** и далее по мере профилирования.

## `org_retention_policy` (V011)

- Таблица **одна строка на организацию** (`org_id` PK → **`organizations`** с **`ON DELETE CASCADE`**).
- Числовые поля возраста (**`hot_message_body_max_age_days`**, **`hot_metadata_min_age_days`**) допускают **`NULL`**: при чтении API подставляются дефолты из **`AppConfig`** (см. **`docs/RETENTION_AND_DEEP_ARCHIVE.md`**).
- Репозиторий: **`RetentionPolicyRepository`** (`find`, **`upsert`**); админские **GET** / **PATCH** — **`AdminResource`**.

## `chat_retention_policy` (V012)

- **Одна строка на чат** (`chat_id` PK → **`chats`** с **`ON DELETE CASCADE`**); override поверх org и дефолтов платформы.
- Репозиторий: **`ChatRetentionPolicyRepository`**; выбор базовой org для слоя org — **`ChatRepository.findOrgIdForRetentionOverlay`** (владелец чата → участники по ролям owner / admin / member).
- Админские **GET** / **PATCH** **`/api/v1/admin/chats/{chatId}/retention`** — **`AdminResource`**; аудит **`chat.retention.set`**.

## `retention_hot_body_applied` (V013)

- **Одна строка на сообщение** (`message_id` PK → **`messages`** с **`ON DELETE CASCADE`**): факт успешного выноса тела из Hot воркером **`RetentionWorker`**; опционально **`storage_object_key`** (ключ объекта в MinIO).
- Используется для **исключения повторной обработки** в SQL выборки (при **`RETENTION_USE_APPLIED_LOG=true`**, по умолчанию). Публикация **`msg.event.retention`** — см. **`RetentionAppliedEvent`** в **`modules/common`**.
- При **`RETENTION_AUDIT_ENABLED=true`** (по умолчанию) тот же воркер добавляет строку в **`audit_events`** (**`V008`**, действие **`message.retention.hot_body_cleared`**, **`actor_user_id`** = **`NULL`**).

## `audit_events` list indexes (V014)

- **`idx_audit_events_action_occurred`** на **`(action, occurred_at DESC)`** и **`idx_audit_events_resource_occurred`** на **`(resource_id, occurred_at DESC)`** — для админского **`GET /api/v1/admin/audit-events`** с фильтром **`action`** или **`resource_id`** и сортировкой **`occurred_at DESC`** + **`LIMIT`**. Дополняют **`idx_audit_occurred`** (**`V008`**). См. также **`docs/RETENTION_AND_DEEP_ARCHIVE.md`** §8 и §13 (**фаза A**).

## `messages` partial index для hot-body ретенции (V015)

- **`idx_messages_retention_hot_body_candidates`** на **`(created_at ASC, chat_id)`** с условием **`deleted = false`**, **`content IS NOT NULL`**, **`trim(content) <> ''`** — вспомогательный доступ для **`RetentionHotBodyJanitor.hotBodyCandidateSelectSql`** (порядок **`ORDER BY m.created_at ASC`**, ключ **`chat_id`** для join к **`chat_retention_policy`**). Частичный индекс не заменяет **`idx_messages_chat_created_not_deleted`** (**`V010`**: ведущий **`chat_id`**, лента по чату).
- Семантика воркера, политики и env — **`docs/RETENTION_AND_DEEP_ARCHIVE.md`** §9 (укрупнённо — **фаза A** в §13).
