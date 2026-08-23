---
name: korus-cycle-prompts
description: "Циклические промпты Korus: buildIntegrity, VPP until GREEN, hex cleanup, UI QA, code hygiene, QEMU stack. Use when user asks for cyclic improvement, until-green loops, iterative fix/verify/cleanup, or /loop prompts for the project."
---

# Korus Cycle Prompts

Когда пользователь просит **циклическую доработку**, **until-green**, **повторяющуюся проверку/чистку** — выбери промпт из каталога и выполни его **полностью**, не останавливаясь на первом FAIL.

## Skill

Полный автомат без ручного poll: **`.\scripts\Start-KorusCycleUnattended.ps1`**

Полный индекс: [`.cursor/prompts/README.md`](../../prompts/README.md)

| Сценарий | Файл |
|----------|------|
| Неясно с чего начать | [`master-cycle.md`](../../prompts/master-cycle.md) |
| Сборка / unit | [`build-until-green.md`](../../prompts/build-until-green.md) |
| Продуктовая приёмка | [`vpp-until-green.md`](../../prompts/vpp-until-green.md) |
| Hex / legacy | [`hex-cleanup-cycle.md`](../../prompts/hex-cleanup-cycle.md) |
| Web UI | [`ui-quality-cycle.md`](../../prompts/ui-quality-cycle.md) |
| Гигиена кода | [`code-hygiene-cycle.md`](../../prompts/code-hygiene-cycle.md) |
| Pre-merge | [`branch-review-cycle.md`](../../prompts/branch-review-cycle.md) |
| QEMU lab | [`qemu-stack-cycle.md`](../../prompts/qemu-stack-cycle.md) |
| QEMU 2-agent server+web | [`qemu-server-stack-pipeline.md`](../../prompts/qemu-server-stack-pipeline.md) |
| Desktop full product W0–W4 | [`desktop-client-full-delivery.md`](../../prompts/desktop-client-full-delivery.md) |
| Desktop single phase | [`desktop-client-pipeline.md`](../../prompts/desktop-client-pipeline.md) |
| Техдолг | [`tech-debt-hunt.md`](../../prompts/tech-debt-hunt.md) |
| **Безопасность / ФСТЭК** | [`security-fstec-cycle.md`](../../prompts/security-fstec-cycle.md) |
| Native mobile client | [`mobile-client-pipeline.md`](../../prompts/mobile-client-pipeline.md) |
| Mobile guest conveyor | `.\scripts\qemu-mobile-conveyor.ps1 -UntilGreen` |

## Обязательный цикл (все промпты)

1. **Capture** — логи, артефакты, checkpoint
2. **Diagnose** — root cause, не симптом
3. **Fix** — минимальный diff или перезапуск guest job
4. **Verify** — изолированная команда/gate
5. **Resume** — продолжить с checkpoint, не с gate 1 (VPP, Playwright partial)
6. **Exit** — только при выполнении критериев успеха промпта + свежий вывод verify

## Skills по приоритету

| Фаза | Skill |
|------|-------|
| Баг / FAIL | `superpowers-systematic-debugging` |
| Перед «готово» | `superpowers-verification-before-completion` |
| Новая логика | `superpowers-test-driven-development` |
| UI | `korus-ui-orchestrator` |
| Native mobile | `korus-mobile-orchestrator` |
| Pre-merge review | `superpowers-requesting-code-review` |

## /loop

Пользователь может запустить: `/loop 30m <текст промпта>`. На каждом тике — **одна итерация** цикла; если критерий успеха достигнут — сообщить и **не** продолжать бессмысленно.

## Запрещено

- Объявлять GREEN / PASS без свежего вывода
- Host Docker для runtime
- TCG / `KORUS_QEMU_FORCE_TCG` (см. `qemu-whpx-required.mdc`)
- Полный перезапуск VPP с gate 1 при наличии `vpp-checkpoint.json`
- Статус-only отчёт без fix + verify
