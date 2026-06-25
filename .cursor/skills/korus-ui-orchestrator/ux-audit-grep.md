# UX audit — grep checklist (P5 / QA / Evaluator)

Run from repo root on **touched files** or whole `webui/` for broad audit.

---

## E — Labels / i18n (LABEL-RED)

```powershell
# Hardcoded Cyrillic in JS (exclude comments — manual review)
rg "[\u0400-\u04FF]" modules/web-client/src/main/resources/webui/*.js --glob '!*.bundle.js'

# iconBtn with non-L() second argument (heuristic)
rg 'iconBtn\([^,]+,\s*"' modules/web-client/src/main/resources/webui/app.js modules/web-client/src/main/resources/webui/ui-*.js

# Locale parity + label lint (CI)
node scripts/webui-label-lint.js

# Legacy parity-only
node scripts/webui-locale-parity-audit.js
```

Playwright tier: `ui-i18n-artifacts`

---

## D — Icons (ICON-RED)

```powershell
# iconBtn without testId on new code (sample)
rg 'iconBtn\(' modules/web-client/src/main/resources/webui/app.js -A2 | rg -v testId

# Hardcoded EN product names in tooltips
rg 'iconBtn\([^,]+,\s*"[A-Za-z]' modules/web-client/src/main/resources/webui/
```

Manual: mobile viewport — primary actions not icon-only without visible label.

---

## C — Settings IA

```powershell
rg 'appendSettings\w+Panel|SETTINGS_TAB_IDS|data-testid="settings-' modules/web-client/src/main/resources/webui/app.js
```

Compare output to [`settings-ia-inventory.md`](settings-ia-inventory.md).

---

## F — Composition

Manual / browser:

- Empty states: `*-empty-title` + hint (not lone `nothingFound`)
- Settings: `settings-subtitle` before block lists
- Destructive actions separated from primary row

---

## Evidence in qa-evidence

```markdown
## UX grep (P5)
- parity: PASS
- iconBtn hardcoded EN: 0 / N findings
- settings IA inventory: match / drift list
```
