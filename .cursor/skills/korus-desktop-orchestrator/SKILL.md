---
name: korus-desktop-orchestrator
description: "Orchestrate Korus desktop Java client (JavaFX, multi-server, profiles, updates) across analyst, architect, designer, UX evaluator, plan-reviewer, engineer, QA. Full delivery: desktop-client-full-delivery.md"
---

# Korus Desktop Orchestrator

Spec: `specs/031-desktop-java-client/`

## Entry points

| Goal | Command / prompt |
|------|------------------|
| **Полный продукт W0→W4** | `.cursor/prompts/desktop-client-full-delivery.md` + `-InitFullDelivery` |
| **Непрерывный конвейер** | `specs/031-desktop-java-client/design/continuous-conveyor.md` + `-EmitPrompt -Continuous` |
| **Одна фаза** | `.\scripts\Start-KorusDesktopPipeline.ps1 -EmitPrompt` |
| **UX / IA проработка** | `-Role Designer -EmitPrompt` (после Architect) |
| **Каталог ролей** | `.cursor/prompts/desktop-client-pipeline.md` |

```powershell
.\scripts\Start-KorusDesktopPipeline.ps1 -InitFullDelivery
.\scripts\Start-KorusDesktopPipeline.ps1 -EmitPrompt
```

## STOP — read first (routing only)

| Rule | Action |
|------|--------|
| Not desktop scope | Exit → `korus-agent-workflow` |
| Desktop scope | Classify pipeline **before** editing `modules/desktop-*` |
| **Continuous conveyor** | **Не останавливаться** между ролями — см. [`continuous-conveyor.md`](../../specs/031-desktop-java-client/design/continuous-conveyor.md) |
| **UI / shell** | Designer → UX Evaluator **параллельно** с Engineer на следующем срезе |
| Role skills | Read skill for **текущей** фазы; **следующую** фазу готовить без ожидания пользователя |

**Desktop scope YES:** `modules/desktop-client*`, JavaFX, jpackage, multi-server, profiles, desktop smokes, spec `031-*`.

**Desktop scope NO:** `webui/`, admin browser UI.

**Designer subagent:** `.cursor/agents/korus-desktop-designer.md`  
**UX subagent:** `.cursor/agents/korus-desktop-ux-evaluator.md`  
**UX framework:** `specs/031-desktop-java-client/design/desktop-ux-framework.md`

---

## Wave guides (implementation blueprints)

| Wave | Guide |
|------|-------|
| W0 | [`waves/W0.md`](waves/W0.md) |
| W1 | [`waves/W1.md`](waves/W1.md) |
| W2 | [`waves/W2.md`](waves/W2.md) |
| W3 | [`waves/W3.md`](waves/W3.md) |
| W4 | [`waves/W4.md`](waves/W4.md) |

---

## INTAKE

### Pipeline

| ID | Flow |
|----|------|
| D1 | Analyst → Engineer → QA (SDK-only hotfix; UX waiver in qa-evidence) |
| D2 | Analyst → Architect → **Designer** → **UX Evaluator** → Plan Reviewer → Engineer → QA |
| D3 | Analyst → Architect → Designer → UX Evaluator → Plan Reviewer (spec only) |
| D5 | QA audit only |

**Default:** D2. **Full delivery:** D2 per wave.

### Modifiers

| Flag | When |
|------|------|
| `+SDK_ONLY` | No JavaFX — UX Evaluator marks `ux_review: N/A` |
| `+UI_POLISH` | W4 / QIP shell — Designer **required** |
| `+VISUAL` | Themes, bubbles, density — axis G scored |
| `+SPECKIT` | Tracked spec 031 change |

### Hard gates

**ENGINEER forbidden** until:

1. `plan_review == APPROVED`
2. `ux_review == PASS` or `N/A` (+ reason for `+SDK_ONLY`)

```powershell
.\scripts\Start-KorusDesktopPipeline.ps1 -SetUxReview PASS
.\scripts\Start-KorusDesktopPipeline.ps1 -SetPlanReview Approved -Advance
.\scripts\Start-KorusDesktopPipeline.ps1 -SetPlanReview ChangesRequested -Rollback
.\scripts\Start-KorusDesktopPipeline.ps1 -CompleteWave   # after QA PASS
```

---

## State machine

```text
INTAKE → ANALYST → ARCHITECT → DESIGNER → UX_EVALUATOR → PLAN_REVIEWER → ENGINEER → QA_VERIFIER → DONE
                              ↘ FAIL (ux) → DESIGNER
                              ↘ CHANGES_REQUESTED (plan) → ARCHITECT
```

---

## Gates & artifacts

| Gate | Tag | On disk |
|------|-----|---------|
| brief | `<!-- ARTIFACT:desktop-brief -->` | `artifacts/waves/W*/desktop-brief.md` |
| plan | `<!-- ARTIFACT:desktop-plan -->` | `.../desktop-plan.md` |
| ux-spec | `<!-- ARTIFACT:desktop-ux-spec -->` | `.../desktop-ux-spec.md` |
| ux-review | `<!-- ARTIFACT:desktop-ux-review -->` | `.../desktop-ux-review.md` |
| plan-review | `<!-- ARTIFACT:desktop-plan-review -->` | `.../desktop-plan-review.md` |
| qa | `<!-- ARTIFACT:desktop-qa-evidence -->` | `.../desktop-qa-evidence.md` |

Templates: [`artifacts/`](artifacts/)

---

## Product complete

`pipeline-status.json`: `"product_delivery": "COMPLETE"`, `wave=W4`, `role=Done`.

See `desktop-client-full-delivery.md` Definition of Done.
