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
| По запросу продукта | **MLS**: wire + client Web Crypto encrypt (`korus-mls-wasm.js`); полный OpenMLS / external interop — отдельный этап. См. **`docs/E2EE_ARCHITECTURE.md`**, **`docs/review/e2ee-wasm-spike-2026-06-10.md`**. |

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

## 7. Инфраструктурная оптимизация (spec 006)

| Приоритет | Содержание |
|-----------|------------|
| Высокий | **Закрыто (2026-06-15):** Pilot compose, Keycloak prod, read cache, scale overlays, zstd deep-archive, batch Solr, NATS cache invalidation, replica/enterprise Ansible. См. **`specs/006-infra-optimization/`**, **`docs/plans/2026-06-15-infra-optimization-design.md`**. |
| Высокий | **Презентация §12 закрыта (v2.3):** FR-OPT-01…07 = реализовано; 08–09 + formal load test — roadmap. См. **`docs/PRODUCT_PRESENTATION.md`**, **`product_presentation.html`**. |
| Средний | **Хвост волна 4:** FR-OPT-08 file dedup ✅ (2026-06-15); FR-OPT-09 PG sharding — scaffold (`DB_SHARD_JDBC_URL`), full router deferred. |
| Средний | **Load test matrix:** k6 baseline на stage (T604) или documented QEMU run; обновление §10.2.1 **измеренными** числами. Скрипты: **`scripts/load/pilot-*.js`**. |

---

## 8. Следующие приоритеты (после spec 007 engineering closure)

| # | Эпик | Блокер | Действие |
|---|------|--------|----------|
| 1 | **Stage/prod TLS + Vault** (spec 004 US1) | Реальный stage host | T601–T602, T607 — **`deploy/ansible/inventory/stage/`**, **`stage-tls-smoke-runbook.md`** |
| 2 | **E2EE formal sign-off** (US7) | Security/Product | T603, T606 — **`e2ee-security-signoff-packet-2026-06-15.md`** |
| 3 | **Hotplug governance** (US6) | Именованные подписи | T605 — **`apply-hotplug-signoff.ps1`** |
| 4 | **k6 load baseline** (T604) | k6 на хосте или stage | **`scripts/load/pilot-health.js`** → JSON отчёт |
| 5 | **FR-OPT-08 dedup** | Нет | design-doc §8, MinIO content-hash |
| 6 | **Hex tail 2b** | Нет | Message write-path через application + port |
| 7 | **Prod features §9** | Продукт | Web Push prod, TURN, Bot API, SSO |

Детальный inventory: **`docs/plans/2026-06-15-unfinished-development-plan.md`** §2–4. Spec ops backlog: **`specs/007-platform-stage-readiness/tasks.md`** Phase 6.

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

*Версия: 2026-06-15 — §12 presentation closure; приоритеты post–spec 007.*
