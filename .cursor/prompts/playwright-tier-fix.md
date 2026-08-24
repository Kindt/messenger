# Цикл: исправление одного Playwright tier

Скопируй всё ниже. **Обязательно** укажи `<TIER>` и при partial fail — `<TEST_INDEX>`.

---

## Параметры

- **Tier:** `<TIER>` (например `ui-auth`, `ui-messaging`, `ui-mobile`)
- **Resume index:** `<TEST_INDEX>` (0 = с начала; из checkpoint `playwright_partial.test_index`)

## Цель

`.\scripts\playwright-dev-loop.ps1 -Tier <TIER>` → 0 failed.

## Preconditions

- Stack healthy (`qemu-stack-cycle` если нет)
- UI scope → `korus-ui-orchestrator` INTAKE

## Цикл

1. **Run**
   ```powershell
   $env:UI_TESTS_START_AFTER_INDEX='<TEST_INDEX>'  # если partial
   .\scripts\playwright-dev-loop.ps1 -Tier <TIER>
   ```
2. **Capture**
   - `deploy/qemu/run/playwright-dev-loop.log`
   - `tests/e2e-web/test-results/<failed-test>/`
   - trace/video если есть
3. **Diagnose** — один failing test, assertion vs selector vs timing vs product
4. **Fix**
   - test drift → test + product parity
   - selector → `data-testid` policy (orchestrator)
   - flake → condition-based wait, не `sleep` без причины
5. **Bundle** — после `webui/` JS: rebuild в web guest / `qemu-web-hotswap`
6. **Verify** — повтор **с того же index** до PASS, затем полный tier без index
7. **Repeat**

## VPP context

Если tier — часть VPP gate: после PASS изолированно → `Invoke-VppGateRunner.ps1 -GateId <id>` → resume VPP checkpoint.

## Запрещено

- Перезапуск всего VPP с gate 1
- Правки webui на P2/P3 без UX Evaluator PASS
- Skip теста без явного решения пользователя

## Успех

Полный tier PASS, `UI_TESTS_START_AFTER_INDEX` сброшен.
