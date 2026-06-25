# UI Quality Rubric (Korus shell) — axes D / E / F

Companion to [`ux-evaluation-framework.md`](../../../specs/026-cursor-ui-agent-orchestrator/design/ux-evaluation-framework.md) (axes A–C).

**Gate (P2/P3):** scores **D, E, F** each 1–5; any **≤2** → FAIL ux-review (same as A–C).  
**RED flags** below → auto cap axis at **2** unless user waiver.

---

## D. Иконки и визуальные affordances

| Score | Критерий |
|-------|----------|
| 1–2 | Icon-only без `title`/aria; emoji непонятен; destructive без текста рядом |
| 3 | Emoji/iconBtn с `L()` title; primary узнаваем |
| 4–5 | Единый паттерн shell; mobile не зависит от hover; статус через pill/banner |

### Паттерны Korus (locked)

| Контекст | Паттерн | Пример |
|----------|---------|--------|
| Settings row action | `iconBtn(emoji, L("…"), { testId })` | 🗑 + title «Сбросить кэш» |
| Primary in row | `iconBtn(…, { primary: true })` | PWA install |
| Destructive | icon + **text label** в row или confirm | revoke, delete |
| Status | pill / banner, не только emoji | ws-status, offline |
| Empty state | **title + hint**, не одна строка | global-search-empty |

### ICON-RED flags

| Flag | Meaning |
|------|---------|
| ICON-RED-01 | Primary action icon-only on mobile без text/tooltip |
| ICON-RED-02 | Разные emoji для одного действия в соседних зонах |
| ICON-RED-03 | Decorative emoji как единственный label кнопки |
| ICON-RED-04 | Новая иконка без `data-testid` на интерактиве |

---

## E. Подписи и текст (copy clarity)

| Score | Критерий |
|-------|----------|
| 1–2 | Hardcode; «OK»; EN в RU UI; обрезка без tooltip |
| 3 | Все через `L()`; глагол на кнопке действия |
| 4–5 | Empty/error говорят **что делать дальше**; 6 locales planned |

### LABEL-RED flags

| Flag | Meaning |
|------|---------|
| LABEL-RED-01 | User-visible string not in `messages/*.json` |
| LABEL-RED-02 | Button label = noun only («Файл») без глагола где нужно действие |
| LABEL-RED-03 | Empty state = one word without hint |
| LABEL-RED-04 | Settings row: `<span>` label missing while control has only emoji |

### Designer MUST list in ux-spec

- New `L()` keys with **RU reference** (not final 6 locales)
- Button vs heading vs hint roles
- Truncation plan for long chat titles in lists

---

## F. Компоновка и иерархия (composition)

| Score | Критерий |
|-------|----------|
| 1–2 | Controls float; нет группировки; primary = secondary визуально |
| 3 | `settings-row` / subtitle sections; related items together |
| 4–5 | Scan path ясен; destructive отделён; mobile stack без clutter |

### Shell composition rules

| Zone | Pattern |
|------|---------|
| Settings tab | `settings-subtitle` → blocks; rows label-left, control-right |
| Search empty | Block under input: **title → hint** (`role="status"`) |
| Thread tools | Search row → hits/empty → pins → messages |
| Lists | Row: primary text + trailing actions cluster |

### COMP-RED flags

| Flag | Meaning |
|------|---------|
| COMP-RED-01 | ≥3 unrelated controls in one row without subtitle |
| COMP-RED-02 | Empty state visually identical to loading hint |
| COMP-RED-03 | Modal action bar: destructive left of primary |
| COMP-RED-04 | Mobile: horizontal scroll for new feature |

---

## G. Visual polish (density, tokens, hierarchy)

| Score | Критерий |
|-------|----------|
| 1–2 | Clutter, off-brand landing tropes, hardcoded colors, tiny touch targets |
| 3 | Matches shell tokens (`var(--*)`), existing CSS classes |
| 4–5 | Clear scan path; empty blocks with title/hint/border; dark/light OK |

**Deep review:** read [`korus-ui-role-visual`](../korus-ui-role-visual/SKILL.md) when `+VISUAL`.

### VIS-RED flags

| Flag | Meaning |
|------|---------|
| VIS-RED-01 | Color outside theme without plan |
| VIS-RED-02 | Landing-page cliché (serif hero, cream stacks) |
| VIS-RED-03 | Empty block without hierarchy |
| VIS-RED-04 | Touch target &lt;44px on mobile |

**N/A:** P1 typo-only; pure logic with no layout — document reason; gate uses A–F only.

---

## Evaluator workflow (D–G)

1. Read ux-spec **Icons** ([`icon-set-policy.md`](icon-set-policy.md)), **Labels**, **Composition**
2. Score D–G with rationale
3. List ICON/LABEL/COMP/VIS-RED triggered
4. P5 audit: [`ux-audit-grep.md`](ux-audit-grep.md) + [`settings-ia-inventory.md`](settings-ia-inventory.md)

---

## References

- Framework A–C: `specs/026-cursor-ui-agent-orchestrator/design/ux-evaluation-framework.md`
- Settings IA: `SETTINGS_TAB_IDS` in `app.js`
- i18n: `korus-webui` skill
