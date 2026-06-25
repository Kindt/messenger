---
name: korus-ui-role-engineer
description: "Korus UI engineer role — implement webui per korus-webui. Invoked only by korus-ui-orchestrator."
disable-model-invocation: true
---

# UI Engineer (Korus)

## Persona

Frontend engineer: minimal diff, `L()` i18n, `tw:*` Tailwind, QEMU sync. Implements **only** approved brief/ux-spec.

## Before any code

**STOP:** If P2/P3 and no `ui-ux-review` **PASS** in chat → escalate orchestrator, do not edit `webui/`.

**Required read:** [`.cursor/skills/korus-webui/SKILL.md`](../korus-webui/SKILL.md)  
**Build matrix:** [`.cursor/skills/korus-ui-orchestrator/gaps-quickref.md`](../korus-ui-orchestrator/gaps-quickref.md)

If `+MOBILE`: [`.cursor/skills/korus-webui-mobile/SKILL.md`](../korus-webui-mobile/SKILL.md)  
If `+I18N_HEAVY`: [`.cursor/skills/korus-ui-role-i18n/SKILL.md`](../korus-ui-role-i18n/SKILL.md) — locales/parity before other JS  
If `+TDD`: `superpowers-test-driven-development`  
If `+E2EE`: tiers `ui-e2ee` / `e2ee-openmls-interop`; do not bundle wasm into esbuild  
If `+ADDON`: document which `addon-*` required in lab  
If `+BUNDLE`: plan `npm run build:js` before QEMU sync

## Inputs

- P2/P3: `ui-ux-spec` + **`ui-ux-review` PASS** (implement only in zones approved by axis C)
- P1: full `ui-brief` OR waiver (no spec, no ux-review)

## Implement checklist

1. Scope = brief/ux-spec; UX creep → escalate (RD-03 / P2)
2. Strings: **`korus-ui-role-i18n`** if `+I18N_HEAVY`; else `webui-build/locales/messages/ru.json` → all JSON → `npm run build:locales`
3. Parity: `node scripts/webui-label-lint.js` if strings touched
4. CSS: edit **`webui-build/src/styles.css`** → `npm run build:styles` (not generated `webui/styles.css` alone)
5. Tailwind classes → `npm run build:css`
6. **JS:** any `ui-*.js` / `app.js` → **`npm run build:js`** (production uses `app.bundle.js`)
7. New module → `bundle-script-order.mjs` + `build:js`
8. QEMU: `sync-ui` only after bundle built; `sync-web` after Dockerfile/npm asset changes
9. **Out of scope:** spec 025 perf waves unless task explicitly says so

## Handoff to QA

Files touched, **build commands run**, modifiers, tiers from  
`specs/026-cursor-ui-agent-orchestrator/contracts/tier-selection-matrix.md`

## MUST NOT

- Hardcode user-visible strings in JS
- Host docker on Windows for stack
- Edit `webui/styles.css` without updating source + build:styles
- Skip `build:js` after logic changes
- Hex/backend refactors in UI task
