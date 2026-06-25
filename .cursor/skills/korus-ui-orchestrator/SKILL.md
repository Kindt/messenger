---
name: korus-ui-orchestrator
description: "Orchestrate Korus web UI work (webui/, i18n, Tailwind, responsive, Playwright) across analyst, designer, UX evaluator, engineer, QA roles. Use for ANY UI task before editing files."
---

# Korus UI Orchestrator

Spec: `specs/026-cursor-ui-agent-orchestrator/`

## STOP — read first

| Rule | Action |
|------|--------|
| Not UI scope | Exit → `korus-agent-workflow` (backend, hex, ansible) |
| UI scope | Classify pipeline **before** editing `webui/` |
| Role skills | Read **only** the skill for current phase (max 2 reads) |

**UI scope YES:** paths under `modules/web-client/.../webui/`, i18n, Tailwind `webui-build`, responsive/mobile, Playwright `ui-*`, settings/composer/auth/call UI.

**UI scope NO:** Jersey `core-api`, workers, deploy-only (unless Dockerfile web-client). **Admin `/admin/` browser UI** on core-api → tier `ui-admin`, not messenger orchestrator unless shell overlap.

**UI scope YES (continued):** `webui-build/`, `app.bundle.js` rebuild, `sw.js`, E2EE wasm paths.

---

## INTAKE (4 steps)

### 1 — UI scope?

If NO → stop orchestrator.

### 2 — Base pipeline

Read [`pipelines.md`](pipelines.md). Summary:

| ID | Flow |
|----|------|
| P1 | Analyst → Engineer → QA |
| P2 | Analyst → Designer → **UX Evaluator** → Engineer → QA |
| P3 | Analyst → speckit → Designer → **UX Evaluator** → Engineer → QA |
| P5 | UX Evaluator and/or QA (audit) |

**Auto-P1 (RD-01b):** typo, parity, locale-only, testid/CSS/build — P1 **without** RD-03.  
**RD-03:** P1 vs P2 unclear → 2–3 options + **ask user** (see `specs/026-.../design/classification-matrix.md`).

### 3 — Modifiers (stack)

| Flag | When |
|------|------|
| `+MOBILE` | 960/520 CSS, touch, thread-back, composer mobile |
| `+SPECKIT` | `specs/NNN-*` tracked feature |
| `+TDD` | New behavior / testid contract |
| `+DEBUG` | :19088 down, blank UI → debugging before engineer |
| `+I18N_HEAVY` | New key tree / migration → read **`korus-ui-role-i18n`** in ENGINEER |
| `+BUNDLE` | Edit `ui-*.js` / `app.js` → **`npm run build:js`** |
| `+E2EE` | MLS wasm, `ui-e2ee-*`, openmls scripts |
| `+ADDON` | Live/bot/e2ee/push UI — lab may lack addon |
| `+PWA` | `sw.js`, manifest, web push install UI |
| `+VISUAL` | themes, density, empty illustration, settings layout redesign — UX Evaluator reads visual role for axis G |

### 4 — Announce

```
[ORCHESTRATOR] pipeline=P1+MOBILE state=ANALYST modifiers=+MOBILE
```

Every phase transition must use `[ORCHESTRATOR]` or role tag.

---

## State machine

```
INTAKE → ANALYST → DESIGNER → UX_EVAL → ENGINEER → QA → DONE
              ↘ speckit (P3)
P1 waiver: INTAKE → ENGINEER → QA (skip UX_EVAL)
P5: INTAKE → QA or UX_EVAL audit
```

### Gates

**brief gate (→ Designer or Engineer on P1)**

- [ ] `<!-- ARTIFACT:ui-brief -->` with problem, in/out scope, ≥2 acceptance, pipeline id
- [ ] P1 waiver: skip only if user said urgent + will log in qa-evidence

**ux-spec gate (→ UX Evaluator on P2/P3)** — skip on P1 / P5 implement

- [ ] `<!-- ARTIFACT:ui-ux-spec -->` with states (or N/A per state), testids, i18n key prefixes
- [ ] Breakpoints 960/520 if layout

**ux-review gate (→ Engineer on P2/P3)** — skip on P1

- [ ] `<!-- ARTIFACT:ui-ux-review -->` with scores **A–G** ≥3 (G N/A ok) or user waiver
- [ ] No unresolved IA/ICON/LABEL/COMP-RED without waiver
- [ ] Read `korus-ui-role-ux-evaluator` + framework + [`quality-rubric.md`](quality-rubric.md)

**implement gate (→ QA)**

- [ ] Scope matches brief/ux-spec
- [ ] Strings → parity audit mentioned if touched
- [ ] `tw:*` → build:css noted if touched
- [ ] If `+BUNDLE`: `npm run build:js` run or noted
- [ ] Engineer read `korus-webui` + [`gaps-quickref.md`](gaps-quickref.md) if JS/CSS/build

**evidence gate (→ DONE)**

- [ ] `<!-- ARTIFACT:ui-qa-evidence -->` verdict PASS | FAIL | BLOCKED
- [ ] Then `superpowers-verification-before-completion`

### Escalation

- **Refuse ENGINEER** on P2/P3 until `ui-ux-review` PASS or user waiver (axes **A–G** ≥3, no RED flags)
- UX Evaluator FAIL → Designer (revise ux-spec), not Engineer
- Engineer finds UX creep on Auto-P1 → stop, RD-03 or P2
- Never P1→P2 without user OK if user chose P1
- QA FAIL → Engineer only (QA does not fix)

---

## Subagent delegation (Phase 2)

When **Task** tool is available, delegate isolated role work to project subagents; otherwise read the matching role skill in-process.

| Phase | Subagent | Fallback skill |
|-------|----------|----------------|
| DESIGNER | `korus-ui-designer` | `korus-ui-role-designer` |
| QA | `korus-ui-qa` | `korus-ui-role-qa` |

Subagent files: `.cursor/agents/korus-ui-designer.md`, `.cursor/agents/korus-ui-qa.md`.

---

## Phase execution

| State | Read | Output |
|-------|------|--------|
| ANALYST | `korus-ui-role-analyst` | ui-brief |
| DESIGNER | `korus-ui-role-designer` | ui-ux-spec |
| UX_EVAL | `korus-ui-role-ux-evaluator`, +[`korus-ui-role-visual`](../korus-ui-role-visual/SKILL.md) if `+VISUAL` | ui-ux-review |
| ENGINEER | `korus-ui-role-engineer`, **`korus-webui`**, +[`korus-ui-role-i18n`](../korus-ui-role-i18n/SKILL.md) if `+I18N_HEAVY`, +mobile if flag | code + handoff |
| QA | `korus-ui-role-qa`, +mobile if flag | ui-qa-evidence |

Templates: [`artifacts/`](artifacts/)

Artifact format: fenced markdown block with `<!-- ARTIFACT:type -->` header.

---

## Waiver (P1 urgent only)

User must explicitly say «без brief» / «срочно».  
Skip Analyst/Designer. Log in qa-evidence:

`Waiver: user urgent hotfix — skipped analyst/designer`

---

## Done

1. qa-evidence **PASS** (or user accepts FAIL backlog)
2. Read `superpowers-verification-before-completion`
3. Notable behavior → `CHANGELOG.md` `[Unreleased]` if product-visible
4. Tracked spec task checkbox if applicable
5. Do **not** say «готово» without evidence

---

## References

Deep docs — read **in phase only** (see [`retro-2026-06-25.md`](retro-2026-06-25.md)):

[`pipelines.md`](pipelines.md) · [`gaps-quickref.md`](gaps-quickref.md) · [`quality-rubric.md`](quality-rubric.md) · [`smoke-catalog.md`](smoke-catalog.md) · spec `026/design/` + `contracts/tier-selection-matrix.md`
