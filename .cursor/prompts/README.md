# Циклические промпты Korus Messenger

Готовые промпты для повторяющихся циклов: доработка → проверка → исправление → чистка. Согласованы с правилами проекта (QEMU host isolation, VPP until GREEN, hex legacy, UI orchestrator).

## Как использовать

| Способ | Команда |
|--------|---------|
| **Полный автомат (рекомендуется)** | `.\scripts\Start-KorusCycleUnattended.ps1` |
| **Автомат в текущем терминале** | `.\scripts\run-korus-cycle-unattended.ps1` |
| **Статус** | `deploy/qemu/run/cycle-unattended-status.json` |
| **Лог** | `deploy/qemu/run/cycle-unattended.log` |
| **Стоп** | `.\scripts\Stop-KorusCycleUnattended.ps1` |
| **Чат (/loop)** | `/loop 30m @.cursor/prompts/build-until-green.md` |
| **Skill** | `.cursor/skills/korus-cycle-prompts/SKILL.md` |

`Start-KorusCycleUnattended` сам: buildIntegrity → ожидание guest `core-api-rebuild` (до 90 мин) → integrations → VPP until GREEN (resume checkpoint).

## Каталог промптов

| Файл | Назначение | Типичный интервал |
|------|------------|-------------------|
| [`master-cycle.md`](master-cycle.md) | Ротация: сборка → стек → VPP/смоук → долг | 1–4 ч |
| [`build-until-green.md`](build-until-green.md) | `./gradlew buildIntegrity` до PASS | по событию / 15m при CI-фиксе |
| [`vpp-until-green.md`](vpp-until-green.md) | Полная продуктовая приёмка spec 030 | часы; resume из checkpoint |
| [`hex-cleanup-cycle.md`](hex-cleanup-cycle.md) | Удаление legacy из application layer | 1 пачка за итерацию |
| [`ui-quality-cycle.md`](ui-quality-cycle.md) | Playwright tier + UX rubric | 30–60m |
| [`code-hygiene-cycle.md`](code-hygiene-cycle.md) | Мёртвый код, дрейф docs, мелкий рефакторинг | 30m |
| [`branch-review-cycle.md`](branch-review-cycle.md) | Pre-merge review + фиксы | перед PR |
| [`qemu-stack-cycle.md`](qemu-stack-cycle.md) | Поднять/починить lab stack | при падении :18080/:19088 |
| [`qemu-server-stack-pipeline.md`](qemu-server-stack-pipeline.md) | **2 агента** server+web: cold → hotswap | `-EmitPrompt` |
| [`playwright-tier-fix.md`](playwright-tier-fix.md) | Один упавший Playwright tier | изолированно |
| [`tech-debt-hunt.md`](tech-debt-hunt.md) | Поиск и закрытие техдолга в заданной области | 1 ч |
| [`desktop-client-full-delivery.md`](desktop-client-full-delivery.md) | **Полная поставка** desktop Java client W0→W4 (spec 031) | по волне / `/loop 2h` |
| [`desktop-client-pipeline.md`](desktop-client-pipeline.md) | Одна фаза конвейера desktop (Analyst…QA) | `-EmitPrompt` |
| [`mobile-client-pipeline.md`](mobile-client-pipeline.md) | Конвейер агентов native mobile Android/iOS (spec 032) | по волне W0–W4 |
| [`security-fstec-cycle.md`](security-fstec-cycle.md) | **ИБ / ФСТЭК**: security-gate -Strict, Sonar, чеклист | 30–60m |

## Общие ограничения (все циклы)

- Коммиты и push — **только по явной просьбе** пользователя; push через `.\scripts\git-push.ps1`
- Runtime (Docker, Ansible, долгие rebuild) — **в QEMU guests**, не на Windows host
- Stage/prod ops — в backlog [`specs/015-live-server-ops-backlog/`](../../specs/015-live-server-ops-backlog/) до Sep 2026+
- Не объявлять SUCCESS без свежего вывода команды (verification-before-completion)
- Минимальный diff; не рефакторить несвязанный код

## Выбор цикла

```
Нужна полная приёмка продукта?     → vpp-until-green
Только компиляция/юнит-тесты?    → build-until-green
Стек недоступен?                 → qemu-stack-cycle или Start-KorusServerStack -Mode warm
2 агента server+web?             → qemu-server-stack-pipeline.md
UI / Playwright?                   → ui-quality-cycle или playwright-tier-fix
Hex / legacy?                      → hex-cleanup-cycle
Перед merge?                       → branch-review-cycle
Не знаете с чего начать?           → master-cycle
```

## Связанные артефакты

| Артефакт | Путь |
|----------|------|
| VPP checkpoint | `deploy/qemu/run/vpp-evidence/vpp-checkpoint.json` |
| VPP GREEN | `deploy/qemu/run/vpp-evidence/vpp-green.json` |
| Failure analysis | `deploy/qemu/run/vpp-failure-analysis.json` |
| Playwright partial | env `UI_TESTS_START_AFTER_INDEX` |
| Spec VPP | `specs/030-vpp-product-verification/` |
| UI orchestrator | `.cursor/skills/korus-ui-orchestrator/` |
| Desktop pipeline status | `deploy/desktop/run/pipeline-status.json` |
| Desktop orchestrator | `.cursor/skills/korus-desktop-orchestrator/` |
| Desktop pipeline script | `scripts/Start-KorusDesktopPipeline.ps1` |
| Desktop full delivery | `desktop-client-full-delivery.md` |
| Mobile pipeline status | `deploy/mobile/run/pipeline-status.json` |
| Mobile orchestrator | `.cursor/skills/korus-mobile-orchestrator/` |
| Mobile pipeline script | `scripts/Start-KorusMobilePipeline.ps1` |
| Mobile product verify | `scripts/Run-KorusMobileProductVerify.ps1` |
