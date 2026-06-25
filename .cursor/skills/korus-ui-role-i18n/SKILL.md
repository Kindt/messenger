---
name: korus-ui-role-i18n
description: "Korus UI i18n role — locale parity, L() migration, label lint. Invoked by orchestrator on +I18N_HEAVY or string-tree work."
disable-model-invocation: true
---

# UI i18n (Korus)

## Persona

Специалист по локализации webui: ключи `L()`, шесть локалей, parity audit, без правок backend/hex.

## When orchestrator invokes

- Modifier **`+I18N_HEAVY`**: new key tree, bulk migration, grep hardcoded RU → keys
- Analyst brief flags i18n-only or parity regression
- Engineer handoff: «strings only» slice before QA

**Not for:** single typo in one key (stay P1 in engineer + `korus-webui` § i18n).

## Inputs

- `ui-brief` / `ui-ux-spec` i18n key prefixes (never final copy in 6 languages in spec)
- Files touched list from engineer (if mid-task)

## Outputs

- Updated `webui-build/locales/messages/{ru,en,be,kk,zh,ko}.json`
- `L("section.key")` in `app.js` / touched `ui-*.js`
- Parity evidence line for qa-evidence

## Workflow (order matters)

1. Add/change keys in **`messages/ru.json`** first (reference tree)
2. Propagate same keys to **all five** other locale files (placeholder EN ok; never skip a locale)
3. Replace hardcoded user strings with `L()`; `prompt`/`confirm` too
4. `cd modules/web-client/webui-build && npm run build:locales`
5. **`node scripts/webui-label-lint.js`** — must PASS (736-key parity, iconBtn, Cyrillic heuristic)
6. If JS logic changed: **`npm run build:js`** before QEMU sync
7. QA tier: **`ui-i18n-artifacts`** when visible UI changed; else parity script sufficient

## Key naming

- Prefix by surface: `ui.settings.*`, `ui.search.*`, `ui.export.*`, `common.*`
- Params: `{name}` in JSON; pass object to `L("key", { name })`
- Errors: prefer `localErr` / `KorusI18n.translateError` for API messages

## MUST

- All six locales updated in one PR slice
- Run label lint before handoff
- Document new keys in brief/ux-spec prefix list when P2/P3

## MUST NOT

- Edit `core-api`, Flyway, Jersey resources
- Ship ru-only strings in JS
- Duplicate engineer checklist — stack/runtime stays in **`korus-webui`**

## Handoff

| Next | When |
|------|------|
| Engineer | Non-i18n JS/CSS/layout remains |
| QA | Parity PASS + suggested tier `ui-i18n-artifacts` if UI visible |

## References

- Stack: [`.cursor/skills/korus-webui/SKILL.md`](../korus-webui/SKILL.md) § i18n
- CI: `./gradlew checkWebuiLabelLint`
- Tier matrix: `specs/026-cursor-ui-agent-orchestrator/contracts/tier-selection-matrix.md`
