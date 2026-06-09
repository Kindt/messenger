# Дорожная карта: эпики после базовой реализации

Базовое серверное ТЗ (`tz_full.html`) и согласованные дополнения по репозиторию закрыты. Дальнейшая работа ведётся **эпиками** ниже; детали ретенции и deep-archive — в **`docs/RETENTION_AND_DEEP_ARCHIVE.md`**.

---

## 1. Ретенция, архив и экспорт (связанный контур)

**Статус по фазам:** A — в проде, B — `completed`, C — `completed` (hot-row purge, file cleanup scaffold, extended legal hold).

| Приоритет | Содержание |
|-----------|------------|
| Высокий | **Фаза B** — закрыта: TTL visibility, чанки deep-archive, Solr atomic update, web-client TTL UI. |
| Высокий | **Фаза C** — hot-row purge (`RetentionHotRowPurger`), orphaned **`file_metadata`** (`FileRetentionJanitor`), legal hold V025, admin purge/legal-hold API. |
| Средний | Связка **экспорта** с агрессивными операциями: smoke **`scripts/smoke-export-replay-before-purge.ps1`**; purge gate — только **`export_v1`**. |
| Средний | Solr validation: smoke **`scripts/smoke-retention-solr-clear.ps1`**; метрики **`indexer_solr_*_total`**. |

**Источник этапов:** **`docs/RETENTION_AND_DEEP_ARCHIVE.md`** (**§10**, **§13**).

---

## 2. Экспорт и комплаенс

| Приоритет | Содержание |
|-----------|------------|
| Средний | Завершение контура **`export-replay`**: не только stub-файл и опционально **`MSG_EXPORT_REPLAY_COMPLETE`**, но и **политика полноты** выгрузки (GDPR / региональные требования — отдельное согласование). |
| Низкий | Документирование для операторов: что именно попадает в пакет, сроки хранения выгрузки. |

---

## 3. E2EE и MLS

| Приоритет | Содержание |
|-----------|------------|
| По запросу продукта | **MLS scaffold** (group state, capabilities `mls-stub`, admin status). Полный RFC 9420 — отдельный этап. См. **`docs/E2EE_ARCHITECTURE.md`**, **`MlsGroupManager`**. |

---

## 4. Сообщения: read receipts

| Приоритет | Содержание |
|-----------|------------|
| По запросу продукта | **Per-message read receipts** — реализовано: REST, NATS/WS, retention, privacy, admin stats. См. epic **`docs/plans/07-read-receipts.md`**. |

Ранее: только **`POST .../read`** (last-read cursor) и **`chat_read_state`**.

---

## 5. Безопасность и устойчивость к абьюзу

| Приоритет | Содержание |
|-----------|------------|
| Средний | Углубление защиты от перечисления: **`scripts/audit-timing.ps1`**, **`TimingNormalization`**, отчёт **`docs/SECURITY_AUDIT.md`**. |

Базово: rate limit на **`/auth`**, поиск пользователей с учётом блокировок.

---

## 6. Локализация и операционные интерфейсы воркеров

| Приоритет | Содержание |
|-----------|------------|
| Низкий | Подключение **`messages_worker_*`** — инфраструктура готова (epic 05); точечная замена hardcoded строк — по мере PR. |

**Уже сделано для API:** **`UserMessageSource`** в **`core-api`**, **`ws-gateway`** для текстов закрытия WebSocket.

---

## Связанные документы

| Документ | Роль |
|----------|------|
| **`docs/RETENTION_AND_DEEP_ARCHIVE.md`** | Целевая модель ретенции, фазы A/B/C, воркеры. |
| **`docs/NATS_SUBJECTS_INTEROP.md`** | Контракты NATS. |
| **`docs/db/FLYWAY_AND_SCHEMA.md`** | Миграции БД. |
| **`docs/PARALLEL_DEVELOPMENT.md`** | Параллельные потоки разработки. |
| **`CHANGELOG.md`** | История изменений. |

---

*Версия: 2026-05-08 — замена закрытого трекера **`docs/TZ_SERVER_100.md`**.*
