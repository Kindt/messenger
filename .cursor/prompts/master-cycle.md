# Мастер-цикл: ротация здоровья проекта

Скопируй всё ниже в чат агента (или `/loop 2h` + этот текст).

---

## Роль

Ты — оператор циклической доработки Korus Messenger. За одну итерацию проходишь **один** приоритетный слой; при FAIL — чинишь и повторяешь **тот же** слой, не перескакивая.

## Приоритет слоёв (сверху вниз)

1. **Сборка** — `./gradlew.bat buildIntegrity` (host, без live stack)
2. **Lab stack** — `http://127.0.0.1:18080/api/v1/health` и `http://127.0.0.1:19088/` → 200
3. **Продукт** — если есть `deploy/qemu/run/vpp-evidence/vpp-checkpoint.json` → resume VPP; иначе quick smoke или один Playwright tier по запросу пользователя
4. **Долг** — одна пачка из `hex-cleanup-cycle` или `code-hygiene-cycle` (≤ 1 aggregate / ≤ 3 файла)

## Итерация

```
A. Оцени состояние (git status, последний checkpoint, health URLs)
B. Выбери верхний слой с FAIL или «не проверено >24ч»
C. Выполни соответствующий промпт из .cursor/prompts/
D. Verify свежей командой
E. Запиши краткий отчёт: слой | статус | следующий шаг
```

## Критерий остановки итерации

- Все слои 1–2 PASS **и** нет открытого VPP FAIL **и** пользователь не заказал слой 4 → сообщи «стабильно» и предложи целевой цикл (VPP full, hex, UI tier)

## Ограничения

- QEMU lifecycle только host-скриптами (`qemu-up`, `qemu-guest-job`, `qemu-sync-api-core`)
- Не коммитить без явной просьбы
- Русский в чате; код/логи — как в репо

## Артефакты для чтения

- `deploy/qemu/run/vpp-evidence/vpp-checkpoint.json`
- `deploy/qemu/run/vpp-failure-analysis.json`
- `deploy/qemu/run/playwright-dev-loop.log`
