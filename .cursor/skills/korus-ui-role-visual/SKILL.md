---
name: korus-ui-role-visual
description: "Korus UI visual polish — axis G (density, hierarchy, shell consistency). Read with UX Evaluator on +VISUAL or brand/layout P2."
disable-model-invocation: true
---

# UI Visual (Korus)

## Persona

Visual reviewer **messenger shell** — не marketing landing. Tokens: `var(--*)`, `styles.css`, `themes.css`, `tw:*`.

## When invoked

| Trigger | Who reads this |
|---------|----------------|
| `+VISUAL` modifier | UX Evaluator (same phase, 2nd skill) |
| themes / density / empty illustration / settings layout redesign | orchestrator adds `+VISUAL` |
| P1 typo/CSS one-liner | **skip** axis G (N/A) |

## Axis G — Visual polish (1–5)

| Score | Criteria |
|-------|----------|
| 1–2 | Clutter, inconsistent spacing, breaks dark/light, landing-page tropes |
| 3 | Matches adjacent blocks; readable hierarchy |
| 4–5 | Scan path clear; tokens consistent; mobile density OK |

### Checklist

- [ ] Uses existing CSS classes (`settings-row`, `settings-subtitle`, `global-search-empty`, …) — not one-off inline styles
- [ ] Spacing aligns 4/8/12px rhythm from `styles.css`
- [ ] Primary vs secondary weight obvious (btn-primary, dashed empty border)
- [ ] Dark + light: relies on `var(--panel)`, `var(--text)`, `var(--muted)` — no hardcoded `#fff`
- [ ] Motion: no gratuitous animation; existing transitions only
- [ ] **Anti landing-cliché:** no serif display fonts, terracotta gradients, hero sections in settings/thread

### VIS-RED flags (cap G ≤2)

| Flag | Meaning |
|------|---------|
| VIS-RED-01 | New color outside theme tokens without plan |
| VIS-RED-02 | `frontend-design` patterns (cream card stacks, oversized radii) |
| VIS-RED-03 | Empty state without visual hierarchy (title/hint/border) |
| VIS-RED-04 | Mobile: controls smaller than 44px touch target |

## Inputs

- `ui-ux-spec` icons/composition sections
- [`icon-set-policy.md`](../korus-ui-orchestrator/icon-set-policy.md)
- Optional: `:19088` screenshots

## Outputs

Axis **G** block inside `ui-ux-review` (evaluator merges) — not a separate artifact.

## MUST NOT

- Ship React/Vue or new icon library
- Override IA (axis C) for aesthetics
- Replace UX Evaluator — only **G** depth

## References

- [`quality-rubric.md`](../korus-ui-orchestrator/quality-rubric.md) — axis G summary
- [`korus-webui-mobile`](../korus-webui-mobile/SKILL.md) — touch targets
