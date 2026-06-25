# UI Pipelines (Korus orchestrator)

Full matrix: `specs/026-cursor-ui-agent-orchestrator/design/classification-matrix.md`

---

## P1 — fix / hotfix

**When:** typo, parity, locale key, testid, CSS typo, build/docker, Auto-P1 (RD-01b).

**When NOT:** new button, new panel, new flow, empty state, «сделай красивее» → P2 or RD-03.

**Sequence:**

```
Analyst (full brief, unless waiver) → Engineer → QA
```

**Waiver:** User urgent → Engineer → QA; log Waiver in evidence.

**Skip:** Designer.

**QA tiers:** See tier matrix; i18n → parity script; mobile CSS → `+MOBILE` + `ui-mobile`.

**Examples:** «опечатка ru.json», «thread-back не виден», «Playwright selector».

---

## P2 — standard UI change

**When:** new UI behavior, states, settings tab, overlay UX, message actions.

**Sequence:**

```
Analyst → Designer (full ux-spec) → UX Evaluator (ui-ux-review) → Engineer → QA
```

**Skip:** Nothing. **UX Evaluator mandatory** (placement + usability + IA scores).

**Designer:** All states table or explicit N/A — no P2-L shortcut.

**Engineer:** `+TDD` if new behavior; Playwright update same mindset.

**Examples:** empty state search, settings tab Notifications, forward overlay UX.

---

## P3 — tracked spec feature

**When:** user references `specs/NNN-*` or platform module UI from spec.

**Sequence:**

```
Analyst → speckit-specify/plan/tasks → Designer → UX Evaluator → Engineer → QA → tasks.md checkbox
```

Speckit owns requirements; ux-spec references spec IDs (no duplicate spec.md).

**Examples:** spec 016 message actions, spec 021 module UI.

---

## P4 — mobile modifier (not standalone)

**When:** any of: phone/tablet, 375/428, touch, safe-area, `@media 960|520`, `thread-back`, call-panel narrow.

**Apply with P1 or P2:**

- Engineer: read `korus-webui-mobile`
- QA: `ui-mobile` + `ui-auth` + `ui-messaging` (+ call/conference tiers if touched)

---

## P5 — audit only

**When:** «проверь UI», a11y, responsive, **удобство / расположение / разделы**, i18n artifacts.

**Sequence:**

```
UX Evaluator (ui-ux-review on live UI) → optional QA tiers / a11y skills
```

Or QA-only for pure regression; **UX Evaluator required** if user asks про UX/IA/удобство.

**Skills:** `korus-ui-role-qa`, optional `accessibility-auditing`, `responsive-testing`, `web-design-guidelines`.

**URL:** `http://127.0.0.1:19088/` only.

---

## RD-03 — pipeline conflict

**When:** P1 vs P2 unclear AND not Auto-P1.

**Procedure:**

1. Analyst writes option table (A/B/C): pipeline, phases, risk, tier impact
2. Recommendation with rationale
3. **Ask user** — wait for answer
4. Record choice in brief

**Template:** see classification-matrix § RD-03.

---

## Modifier reference

| Modifier | Engineer | QA |
|----------|----------|-----|
| +MOBILE | `korus-webui-mobile` | ui-mobile + desktop regression |
| +TDD | superpowers TDD, Playwright | named tier |
| +DEBUG | systematic-debugging first | after stack up |
| +I18N_HEAVY | all 6 locales + parity | ui-i18n-artifacts |
| +SPECKIT | follow spec tasks | tiers per spec |
| +BUNDLE | `npm run build:js` before sync | primary tier |
| +E2EE | wasm paths; no esbuild bundle | ui-e2ee / e2ee-openmls-interop |
| +ADDON | note lab addon availability | SKIP if disabled, not FAIL |
| +PWA | sw/manifest | ui-push if push UI |
| +VISUAL | `korus-ui-role-visual` at UX_EVAL | axis G in ux-review |

---

## Tier hints (summary)

| Surface | Primary tier |
|---------|--------------|
| Settings, auth, sidebar | `ui-auth` |
| Thread, composer, overlays | `ui-messaging` |
| Phase5 kanban/polls/stickers | `ui-messaging-extended` |
| Call panel | `ui-call-flows` |
| Conference | `ui-conference` |
| Live | `ui-live` |

Full table: `specs/026-cursor-ui-agent-orchestrator/contracts/tier-selection-matrix.md`
