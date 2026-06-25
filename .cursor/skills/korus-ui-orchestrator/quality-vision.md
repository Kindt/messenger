# UI Quality Vision — куда эволюционирует pack

**Цель:** не «красиво в чате», а **измеримое** качество до merge.

---

## Слои гарантии (снизу вверх)

| Layer | Что ловит | Статус |
|-------|-----------|--------|
| **L0 Stack** | bundle, locales, CSS source | `gaps-quickref`, engineer |
| **L1 IA** | правильный tab/zone | A, C + `settings-ia-inventory` |
| **L2 Interaction** | шаги, touch, empty/error | B |
| **L3 Semantics** | иконки + подписи + группировка | D, E, F + `quality-rubric` |
| **L4 Audit** | grep/hardcode/parity | ✅ `webui-label-lint.js` + `checkWebuiLabelLint` |
| **L5 Visual** | density, tokens, axis G | ✅ axis G + visual role; ✅ Playwright `ui-visual-regression` |

---

## Что уже работает (Phase 1.6–1.9)

- Gate **A–F** перед implement (P2/P3)
- Living **settings IA inventory** после правок product
- **P5 grep protocol** для icon/label drift
- Product fixes: reminders, DND, federation tab, search empty, call tooltips

---

## Phase 2 — delivered (2026-06-25)

| Initiative | Status |
|------------|--------|
| **T02652 Visual role** | ✅ axis G + `korus-ui-role-visual` |
| **G36** federation trust_level i18n | ✅ |
| **Icon set policy + SVG sprite** | ✅ `ui-icon-buttons.js` |
| **Label lint** | ✅ `scripts/webui-label-lint.js` → `checkWebuiLabelLint` |
| **Screenshot regression** | ✅ tier `ui-visual-regression` |
| **Subagents T02650/51** | ✅ `.cursor/agents/korus-ui-{designer,qa}.md` |
| **Composition tokens** | ✅ [`composition-tokens.md`](composition-tokens.md) |
| **Cross-tab UX tests** | ✅ `settings-cross-tab.spec.ts` in tier `ui-auth` |

---

## Evaluator mantra (P2/P3/P5)

1. **Где** (A, C + inventory)?  
2. **Как пользоваться** (B)?  
3. **Понятно ли глазами** (D, F)?  
4. **Понятно ли словами** (E)?  
5. **RED flags** — block или waiver с цитатой user.

---

## Anti-patterns («пшик»)

- Designer → Engineer без ux-review  
- Одна строка empty без hint (LABEL-RED-03)  
- Новая settings tab для одного toggle (IA-RED-01)  
- EN tooltip при RU UI (LABEL-RED-01)  
- Federation/security в links/general (IA-RED-08, fixed)
