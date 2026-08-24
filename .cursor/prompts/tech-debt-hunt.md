# Цикл: охота на техдолг (scoped)

Скопируй всё ниже. Укажи **область**: `core-api/messaging`, `webui/settings`, `deploy/ansible`, `scripts/vpp`, …

---

## Цель

За итерацию закрыть **1–3** измеримых пункта техдолга в области с verify.

## Источники кандидатов

| Источник | Путь |
|----------|------|
| Hex остаток | `.cursor/rules/hex-legacy-deprecation.mdc` |
| VPP fortress gaps | `scripts/vpp/Invoke-VppFortressGapReport.ps1` |
| Spec open tasks | `specs/**/tasks.md` unchecked |
| TODO в коде | `rg "TODO|FIXME|HACK"` в области |
| UX gaps | `korus-ui-orchestrator/gaps-quickref.md` |
| Deferred ops | **не** трогать — `specs/015-live-server-ops-backlog/` |

## Итерация

1. **Inventory** — список 5–10 кандидатов с оценкой: impact × effort
2. **Pick** — top 1–3 только «можно закрыть за сессию»
3. **Spec check** — если feature-tracked → speckit tasks, не ad-hoc
4. **Implement** — минимальный diff; TDD для поведения
5. **Verify** — команда из кандидата (test, gate, buildIntegrity)
6. **Document** — обновить tasks.md / gap report **только** если закрыли пункт
7. **Report** — таблица: id | было | стало | verify

## Приоритет

1. Блокирует сборку или VPP
2. Безопасность / data loss
3. Flaky tests
4. Читаемость / docs
5. Косметика

## Запрещено

- Stage/prod deploy (spec 015 deferred)
- Большой рефактор «заодно»
- Закрытие checkbox без verify

## Успех

Каждый выбранный пункт — verify PASS + явная ссылка на артефакт (тест, gate, файл).

## Следующая итерация

Новая область или следующие кандидаты из inventory.
