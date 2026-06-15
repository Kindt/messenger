# Планы разработки Korus Messenger

Этот каталог содержит детальные планы по каждому эпику проекта. Каждый план расширен кросс-функциональными разделами: OpenAPI, Prometheus метрики, Admin UI, Web-client UI, Env-переменные, Smoke-тесты.

## Таблица эпиков

| № | Файл | Эпик | Приоритет | Статус |
|---|------|------|-----------|--------|
| 1 | `01-retention-phase-b.md` | Ретенция Фаза B — TTL, чанки, унификация deep-archive | Высокий | `completed` |
| 2 | `02-retention-phase-c.md` | Ретенция Фаза C — purge hot, файлы, legal hold | Высокий | `completed` |
| 3 | `03-export-compliance.md` | Экспорт и комплаенс — политика полноты, GDPR | Средний | `completed` |
| 4 | `04-security-timing.md` | Безопасность — timing, унификация ответов, security headers | Средний | `completed` |
| 5 | `05-worker-localization.md` | Локализация воркеров — i18n, метрики, health | Низкий | `completed` |
| 6 | `06-e2ee-mls.md` | E2EE/MLS — wire + hybrid client MLS (OpenMLS deferred) | По запросу | `completed` (prod gate pending) |
| 7 | `07-read-receipts.md` | Per-message read receipts | По запросу | `completed` |
| 8 | `08-hexagonal-refactoring.md` | Ports & Adapters Phase 2a (Chat) | Опционально | `completed` |
| 9 | `09-code-health-backlog.md` | Серия малых PR по рефакторингу hotspot-зон | Высокий | `completed` |
| 10 | `10-web-client-code-health-backlog.md` | Серия малых PR по оздоровлению web-client | Высокий | `completed` |
| 11 | `2026-06-15-infra-optimization-design.md` | Infra optimization (spec 006): pilot, cache, scale, zstd, batch Solr | Высокий | `completed` |
| 12 | `2026-06-15-unfinished-development-plan.md` | Inventory + hybrid sprint D closure → [`specs/007-platform-stage-readiness/`](../specs/007-platform-stage-readiness/) | Справочник | `completed` |
| 13 | `2026-06-16-presentation-gaps-implementation-plan.md` | Закрытие «Частично»/«Запланировано» в продуктовой презентации (P0–P4) | Высокий | `active` |

## Структура каждого плана

Каждый файл содержит:

- **Цель** — что должно быть реализовано
- **Текущее состояние** — что уже есть в коде
- **Зависимости** — от каких эпиков/модулей зависит
- **Шаги реализации** — конкретные задачи с указанием файлов, модулей, env-переменных
- **Критерии завершения** — тесты, smoke, документация
- **Риски** — известные проблемы и неопределённости
- **Статус** — `not_started` / `in_progress` / `done` / `blocked`
- **Теги** — `[ретенция]` `[БД]` `[воркер]` `[core-api]` и т.д.

## Canonical vs Deprecated (freeze-правила)

До отдельного cleanup PR массовые удаления запрещены. Используйте эту матрицу:

- **Canonical (источник правды для реализации):**
  - `specs/007-platform-stage-readiness/`, `specs/009-platform-modules/` (active)
  - `specs/008-repository-cleanup/` (closed 2026-06-15)
  - `docs/plans/*.md` (детальные эпики)
  - `docs/parity/`, `docs/contracts/`, `docs/review/ops-signoff-log.md`
  - `docs/ROADMAP_EPICS.md` (укрупненный roadmap)
  - `scripts/SMOKE_INDEX.md` (индекс smoke-сценариев)
  - `specs/archive/README.md` (закрытые 001–006)
- **Deprecated (переходные сценарии, не удалять до миграции ссылок):**
  - дублирующие smoke-обёртки (`.ps1`), если canonical отмечен в `scripts/SMOKE_INDEX.md`
  - устаревшие примеры команд в quickstart/plan-доках (подлежат замене в docs-first PR)

### Правило обновления статусов

При изменениях в `01-retention-phase-b.md` и `03-export-compliance.md` статус и краткий прогресс должны синхронно обновляться:

1. в таблице этого файла,
2. в соответствующем файле эпика,
3. в `[Unreleased]` секции `CHANGELOG.md` (если изменения влияют на поведение/эксплуатацию).

## Cross-cutting разделы, добавленные в каждый план

- **OpenAPI/Swagger** — `@ExampleObject`, `@Schema` для новых DTO
- **Prometheus метрики** — счётчики, гистограммы для новых операций
- **Admin UI** — разделы в админке для управления фичами
- **Web-client** — UI-изменения для пользователя
- **Env-переменные** — новые конфигурационные параметры
- **Smoke-тесты** — PowerShell/Bash скрипты для E2E-проверок
- **Документация** — какие docs нужно обновить

Ориентир: [ROADMAP_EPICS.md](../ROADMAP_EPICS.md), [RETENTION_AND_DEEP_ARCHIVE.md](../RETENTION_AND_DEEP_ARCHIVE.md).

## Связанные Spec-Kit пакеты

- Для web-client parity: [`docs/parity/README.md`](../parity/README.md) (archived spec 002).
