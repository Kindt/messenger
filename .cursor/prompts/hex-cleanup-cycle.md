# Цикл: Hex / legacy cleanup (одна пачка)

Скопируй всё ниже. Укажи aggregate или пакет, если нужен фокус: `MessagePin`, `AdminStats`, …

---

## Цель

За **одну итерацию** убрать legacy-зависимость из **одного** application/coordinator класса или вынести SQL из одного legacy service в adapter.

## Scope (правило hex-legacy-deprecation)

- **Удалять** вызовы `api.repository.*` / legacy `*Service` из `*ApplicationService`, coordinators
- **Не добавлять** dual-path (port null → legacy fallback) в application
- Adapter может делегировать в legacy **внутри** adapter; следующий шаг — SQL в adapter
- Целевой остаток: `AdminServerStatsService`, `PurgeStatusService`, `MlsMigrationService`, `ExportJobStaleCounts`, `HealthResource`

## Итерация

1. **Find** — `rg "api\.repository|legacy" modules/core-api/.../application/` (или заданный класс)
2. **Plan** — port уже есть? (см. таблицу в `.cursor/rules/hex-legacy-deprecation.mdc`)
3. **TDD** — H2 test на поведение до рефактора (если нет покрытия)
4. **Refactor** — application → port → adapter (минимальный diff)
5. **Verify** — `./gradlew.bat :modules:core-api:test` + затронутые модули; при необходимости `buildIntegrity`
6. **Report** — что убрано, что осталось в adapter

## БД

До релиза 0.0.1 схему можно пересоздать в lab; новые миграции — в `db/migration/` + запись в `docs/db/FLYWAY_AND_SCHEMA.md`

## Успех

- Нет новых legacy-import в изменённом application-файле
- Тесты PASS
- Один осмысленный commit-worthy diff (коммит — только по просьбе пользователя)

## Следующая итерация

Выбрать следующий класс из остатка legacy; не смешивать 3+ aggregates в одном цикле.
