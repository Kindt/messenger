# Цикл: VPP until GREEN (spec 030)

Скопируй всё ниже в чат агента. Долгий прогон — используй с `/loop` только для **мониторинга** checkpoint, не для полного перезапуска.

---

## Цель

`deploy/qemu/run/vpp-evidence/vpp-green.json` с `status: GREEN` и `full_coverage: true` после **full** level.

## Preconditions

```powershell
$env:KORUS_QEMU_THREE_VM='1'
.\scripts\qemu-up.ps1 -WithIntegrations
```

## Старт / resume

```powershell
# Если есть checkpoint — resume (НЕ с gate 1):
.\scripts\vpp\Resume-VppMonitoredLabRun.ps1 -SkipStackPrep

# Иначе полный until-green:
.\scripts\run-vpp-until-green.ps1 -Level full
```

## Цикл при FAIL (vpp-until-green.mdc)

1. **Capture**
   - `deploy/qemu/run/vpp-failure-analysis.json`
   - `deploy/qemu/run/vpp-evidence/vpp-checkpoint.json`
   - `deploy/qemu/run/plan-failure-analysis.json`
   - `deploy/qemu/run/playwright-dev-loop.log`
   - `tests/e2e-web/test-results/` (при UI FAIL)
2. **Diagnose** — gate id + root cause → `specs/030-vpp-product-verification/contracts/vpp-failure-remediation.json`
3. **Fix** — минимальный repo diff **или** guest stack restart (QEMU, не host Docker)
4. **Verify isolated** — `.\scripts\vpp\Invoke-VppGateRunner.ps1 -GateId <id>`
5. **Resume** — checkpoint resume; PASS gates не перезапускать
6. **Final** — после checkpoint GREEN: **один полный прогон с gate 1 без checkpoint** → затем `vpp-green.json`

## Playwright partial

- Env: `UI_TESTS_START_AFTER_INDEX`
- Checkpoint: `playwright_partial.test_index`
- После tier PASS — index сброс для следующего gate

## Запрещено

- Перезапуск gates 1–N, уже PASS в checkpoint
- Объявление GREEN без final full verify
- STALLED без чтения `playwright-dev-loop.log`
- Host `docker compose` / `ansible-playbook` для runtime

## Успех

Файл `vpp-green.json` существует, `full_coverage: true`, свежий timestamp.
