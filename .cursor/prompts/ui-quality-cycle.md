# Цикл: UI quality (Playwright + UX)

Скопируй всё ниже. Замени `<TIER>`: `ui-auth`, `ui-messaging`, `ui-mobile`, `ui-admin`, `vpp-ui-blocks`, …

---

## Preconditions

- Прочитай `.cursor/skills/korus-ui-orchestrator/SKILL.md` **до** правок `webui/`
- Stack: `http://127.0.0.1:19088/` и API `:18080` healthy (иначе `qemu-stack-cycle` сначала)

## Цель

Tier `<TIER>` — **PASS** через `playwright-dev-loop.ps1` + UX gates A–G по spec 026 (если tier ux-related).

## Pipeline

| Тип задачи | Pipeline |
|------------|----------|
| typo, testid, locale | P1 |
| placement, IA, новый экран | P2/P3 + UX Evaluator PASS до ENGINEER |

## Цикл

1. **Run tier**
   ```powershell
   .\scripts\playwright-dev-loop.ps1 -Tier <TIER>
   ```
2. **Capture** — `tests/e2e-web/test-results/`, скриншоты, trace
3. **Classify** — flaky / regression / product bug / test drift
4. **Fix** — `webui/` + i18n + `npm run build:js` в guest/web VM при bundle-изменениях
5. **UX** — для P2+: заполнить/обновить `ui-ux-review` по шаблону orchestrator
6. **Verify** — повтор tier; при partial resume: `-StartAfterTestIndex` / env `UI_TESTS_START_AFTER_INDEX`
7. **Repeat** до PASS

## Mobile

Добавь `+MOBILE`: tier `ui-mobile`, skill `korus-webui-mobile`

## Запрещено

- Правки `webui/` до UX gate на P2/P3
- Host Docker для web stack
- «UI готов» без свежего Playwright PASS

## Успех

```
playwright-dev-loop.ps1 -Tier <TIER> → exit 0, 0 failed
```

Краткий evidence: tier, длительность, изменённые файлы, UX gate id (если был).
