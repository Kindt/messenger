# Планы разработки Korus Messenger

Этот каталог содержит детальные планы по каждому эпику проекта. Каждый план расширен кросс-функциональными разделами: OpenAPI, Prometheus метрики, Admin UI, Web-client UI, Env-переменные, Smoke-тесты.

## Таблица эпиков

| № | Файл | Эпик | Приоритет | Статус |
|---|------|------|-----------|--------|
| 1 | `01-retention-phase-b.md` | Ретенция Фаза B — TTL, чанки, унификация deep-archive | Высокий | `in_progress` |
| 2 | `02-retention-phase-c.md` | Ретенция Фаза C — purge hot, файлы, legal hold | Высокий | `not_started` |
| 3 | `03-export-compliance.md` | Экспорт и комплаенс — политика полноты, GDPR | Средний | `in_progress` |
| 4 | `04-security-timing.md` | Безопасность — timing, унификация ответов, security headers | Средний | `not_started` |
| 5 | `05-worker-localization.md` | Локализация воркеров — i18n, метрики, health | Низкий | `not_started` |
| 6 | `06-e2ee-mls.md` | E2EE/MLS — полное RFC 9420 | По запросу | `not_started` |
| 7 | `07-read-receipts.md` | Per-message read receipts | По запросу | `not_started` |
| 8 | `08-hexagonal-refactoring.md` | Ports & Adapters рефакторинг Phase 2-3 | Опционально | `not_started` |
| 9 | `09-code-health-backlog.md` | Серия малых PR по рефакторингу hotspot-зон | Высокий | `completed` |
| 10 | `10-web-client-code-health-backlog.md` | Серия малых PR по оздоровлению web-client | Высокий | `completed` |

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
  - `specs/001-system-review-refactoring/*`
  - `docs/plans/*.md` (детальные эпики)
  - `docs/ROADMAP_EPICS.md` (укрупненный roadmap)
  - `scripts/SMOKE_INDEX.md` (индекс smoke-сценариев)
- **Deprecated (переходные сценарии, не удалять до миграции ссылок):**
  - дублирующие smoke-обертки (`.cmd`/`.ps1`), если canonical отмечен в `scripts/SMOKE_INDEX.md`
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
