# Цикл: buildIntegrity until GREEN

Скопируй всё ниже в чат агента.

---

## Цель

Добиться **PASS** `./gradlew.bat buildIntegrity` на host (компиляция, unit/H2, integrity checks). Live stack не требуется.

## Цикл (обязательный)

1. **Run** — `./gradlew.bat buildIntegrity` (полный вывод, не `--quiet` при диагностике)
2. **Capture** — сохрани последние 80 строк ошибки + путь к `build/reports/`
3. **Diagnose** — один root cause (compile vs test vs checkstyle vs integrity task)
4. **Fix** — минимальный diff в затронутом модуле
5. **Verify** — повтор `./gradlew.bat buildIntegrity`
6. **Repeat** до exit 0

## Правила

- Не трогать несвязанные модули
- Hex: новый код только `core.domain` / `core.application` / `core.port` / `core.adapter.*`
- Тесты: JUnit 5 + H2; не поднимать Docker на host
- Не объявлять «сборка зелёная» без свежего exit 0

## Типичные ветки

| Симптом | Действие |
|---------|----------|
| Compile error | исправить импорт/тип, пересобрать модуль |
| Unit test FAIL | воспроизвести `:module:test --tests Class`, TDD fix |
| buildIntegrity task | читать имя упавшей задачи в отчёте |
| Flaky | 2 прогона подряд PASS перед заявлением |

## Успех

```
buildIntegrity: BUILD SUCCESSFUL (exit 0)
```

После успеха: кратко перечисли изменённые файлы и упавшие тесты, если были.
