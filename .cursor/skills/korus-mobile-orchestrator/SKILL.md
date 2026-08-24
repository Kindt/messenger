---
name: korus-mobile-orchestrator
description: "Orchestrate Korus native mobile client work (mobile/, Android/iOS/KMP, Maestro) across analyst, architect, designer, UX evaluator, plan-reviewer, engineer, QA roles. Use for ANY mobile native task before editing files."
---

# Korus Mobile Orchestrator

Spec: `specs/032-mobile-native-client/`

## STOP — read first

| Rule | Action |
|------|--------|
| Not mobile scope | Exit → `korus-agent-workflow` (webui, backend, hex) |
| Mobile scope | Classify wave + role **before** editing `mobile/**` |
| UI work | **Designer → UX Evaluator** before Engineer (no «кнопки без IA») |
| Role skills | Read **only** the skill for current phase (max 2 reads) |

**Mobile scope YES:** `mobile/**`, KMP/Android/iOS modules, Maestro flows, `smoke-mobile-*.ps1`, push/downloads/platform services.

**Mobile scope NO:** `webui/` (→ `korus-ui-orchestrator`), Jersey `core-api` unless API gap for mobile, desktop `modules/desktop-*` (→ spec 031).

**Reference UX:** web `ui-mobile` tier + `mobile-ux-framework.md` — reference, not implementation target.

---

## INTAKE

### 1 — Mobile native scope?

If NO → stop orchestrator.

### 2 — Wave (implementation)

| Wave | Focus |
|------|-------|
| W0 | Scaffold, auth, 1 server |
| W1 | Multi-profile, multi-server |
| W2 | Messaging parity, attachments, offline |
| W3 | Add-ons, push, calls, E2EE |
| W4 | Updates, distribution, polish |

Read [`../specs/032-mobile-native-client/tasks.md`](../../specs/032-mobile-native-client/tasks.md) for task IDs.

### 3 — Modifiers

| Flag | When |
|------|------|
| `+IOS_FALLBACK` | Compose MP iOS blocked → SwiftUI + shared SDK |
| `+SPECKIT` | Tracked spec 032 change |
| `+TDD` | New SDK behavior |
| `+E2EE` | OpenMLS / MLS mobile ADR |
| `+PUSH` | addon-engage, FCM/APNs |
| `+CORP_UPDATE` | Corporate APK/IPA feed |
| `+SDK_ONLY` | SDK-only slice — UX Evaluator may mark ux-review N/A in artifact |

### 4 — Announce

```text
[MOBILE-ORCHESTRATOR] wave=W2 state=DESIGNER modifiers=+SPECKIT
```

---

## State machine

```text
INTAKE → ANALYST → ARCHITECT → DESIGNER → UX_EVALUATOR → PLAN_REVIEWER → ENGINEER → QA_VERIFIER → DONE
                              ↘ REJECTED (plan) → ARCHITECT
                              ↘ FAIL (ux) → DESIGNER
```

**BLOCK Engineer** until:

1. `plan-review.status == APPROVED`
2. `ux-review.status == PASS` (or `N/A` with reason for `+SDK_ONLY`)

**BLOCK Designer** until `mobile-brief` + `mobile-plan-diff` exist.

Artifact templates: [`artifacts/`](artifacts/)  
UX framework: [`../../specs/032-mobile-native-client/design/mobile-ux-framework.md`](../../specs/032-mobile-native-client/design/mobile-ux-framework.md)  
Implementation blueprint: [`../../specs/032-mobile-native-client/design/implementation-blueprint.md`](../../specs/032-mobile-native-client/design/implementation-blueprint.md)

---

## Gates

**brief gate (→ Architect)**

- [ ] `<!-- ARTIFACT:mobile-brief -->` with problem, in/out scope, ≥2 acceptance, wave id

**plan gate (→ Designer)**

- [ ] `<!-- ARTIFACT:mobile-plan-diff -->` — tasks.md / plan.md diff summary

**ux-spec gate (→ UX Evaluator)**

- [ ] `<!-- ARTIFACT:mobile-ux-spec -->` — IA table, navigation, states, testTags

**ux-review gate (→ Plan Reviewer)**

- [ ] `<!-- ARTIFACT:mobile-ux-review -->` — axes A–F ≥3, verdict PASS (or user waiver)

**plan-review gate (→ Engineer)**

- [ ] `<!-- ARTIFACT:plan-review -->` with status `APPROVED`
- [ ] ux-review PASS aligned with plan
- [ ] REJECTED → Architect, no code

**implement gate (→ QA)**

- [ ] UI matches approved `mobile-ux-spec` (testTags, IA)
- [ ] `<!-- ARTIFACT:implement-notes -->` — files touched, tests run

**qa gate (→ DONE)**

- [ ] `<!-- ARTIFACT:qa-evidence -->` — smoke scripts, matrix rows PASS
- [ ] Maestro flows match ux-spec testTags when UI touched
- [ ] `superpowers-verification-before-completion` before claim «готово»

---

## Role skills

| Role | Skill | Subagent |
|------|-------|----------|
| Analyst | `korus-mobile-role-analyst` | — |
| Architect | `korus-mobile-role-architect` | — |
| Designer | `korus-mobile-role-designer` | `korus-mobile-designer` |
| UX Evaluator | `korus-mobile-role-ux-evaluator` | `korus-mobile-ux-evaluator` |
| Plan reviewer | `korus-mobile-role-plan-reviewer` | — |
| Engineer | `korus-mobile-role-engineer` | — |
| QA verifier | `korus-mobile-role-qa-verifier` | — |

Web designer skills (`korus-ui-role-designer`) — **reference only** for patterns; mobile uses `mobile-ux-spec`.

Pipeline design: [`../../specs/032-mobile-native-client/design/agent-pipeline.md`](../../specs/032-mobile-native-client/design/agent-pipeline.md)

---

## Script entry

```powershell
.\scripts\Start-KorusMobilePipeline.ps1 -Phase W0 -Role ANALYST
.\scripts\Start-KorusMobilePipeline.ps1 -Phase W2 -Role DESIGNER
.\scripts\Start-KorusMobilePipeline.ps1 -Phase W2 -Role UX_EVALUATOR -UxReviewStatus PASS
```
