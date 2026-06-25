# UI/UX Spec

<!-- ARTIFACT:ui-ux-spec -->

**Brief ref:**  
**Surfaces:** (e.g. settings panel, thread composer)  
**Author (agent role):** Designer

## User flow

1. 
2. 

## IA placement (UX Evaluator axis C)

**Target zone:** sidebar | thread | composer | settings:`___` | overlay | call-panel | header | other  
**Why this section (1 sentence):**

## Layout & breakpoints

| Viewport | Behavior |
|----------|----------|
| Desktop ≥1280 | |
| Tablet 768 | |
| Mobile ≤960 (`korus-webui-mobile`) | |
| Compact ≤520 | |

## States (mandatory per surface)

| State | UI behavior | Copy key (`L()`) |
|-------|-------------|------------------|
| Default | | |
| Empty | | |
| Loading | | |
| Error | | |

## Components & patterns

Reference `ui-design-brain` / `using-ui-stack` where applicable.  
**Stack constraint:** vanilla JS, Tailwind `tw:*`, no React.

## `data-testid` map

| Element | testid | Notes |
|---------|--------|-------|
| | | |

## i18n notes

New keys (prefix `section.`):

- `section.key` — RU reference text

All 6 locales required before engineer handoff complete.

## Accessibility

- Focus order:
- Keyboard:
- Screen reader labels:

## Icons & affordances (axis D)

| Control | Icon/emoji | `L()` title key | testid |
|---------|------------|-----------------|--------|
| | | | |

Rules: `iconBtn(emoji, L("…"), { testId })`; no icon-only primary on mobile.

## Labels & copy (axis E)

| Element | Role | Key | RU reference |
|---------|------|-----|--------------|
| | button / hint / title | | |

Empty states: **title + hint** keys (not single `nothingFound` line).

## Composition (axis F)

- Section grouping (`settings-subtitle`, blocks):
- Primary vs secondary visual weight:
- Destructive placement:

## Prototype (optional)

- [ ] Canvas mockup link/path (not production)

## Handoff

- [ ] Ready for **UX Evaluator** → `korus-ui-role-ux-evaluator` (P2/P3 gate)
- [ ] Mobile QA required → flag +MOBILE
