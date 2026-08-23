---
name: korus-agent-workflow
description: "Korus Messenger agent workflow bridge - when to use speckit-* vs superpowers-* skills, project constraints (Russian comms, spec-first features)."
---

# Korus Messenger Agent Workflow

This project uses **two complementary skill sets**. Read this skill when unsure which workflow applies.

## Quick decision

| Situation | Use |
|-----------|-----|
| New feature, spec change, sprint closure | **speckit-*** (`speckit-specify` -> `speckit-plan` -> `speckit-tasks` -> `speckit-implement`) |
| Constitution / principle updates | **speckit-constitution** |
| Spec quality / consistency check | **speckit-analyze**, **speckit-checklist**, **speckit-clarify** |
| Rough idea before a formal spec | **superpowers-brainstorming** (then feed into speckit-specify) |
| Web UI, i18n, Tailwind | **`korus-ui-orchestrator`** → role pipeline (spec 026) |
| Desktop Java client (full W0–W4) | **`desktop-client-full-delivery.md`** + `Start-KorusDesktopPipeline.ps1 -InitFullDelivery` |
| Desktop single phase | **`desktop-client-pipeline.md`** + `-EmitPrompt` |
| Desktop UI / IA / «что в меню» | **`korus-desktop-orchestrator`** → **Designer → UX Evaluator** before Engineer (spec 031) |
| UX placement / usability / «не туда раздел» | **`korus-ui-orchestrator`** P5 or P2/P3 **UX Evaluator** gate (web) |
| Mobile / responsive webui | **`korus-ui-orchestrator`** with `+MOBILE`; engineer/QA → **`korus-webui-mobile`** |
| Native mobile Android/iOS (KMP) | **`korus-mobile-orchestrator`** first (spec **032**); not `webui/` |
| Engineer webui stack reference | **`korus-webui`** (after orchestrator ENGINEER phase) |
| Implementation plan from approved design (non-spec-kit) | **superpowers-writing-plans** |
| Execute a written plan with checkpoints | **superpowers-executing-plans** or **superpowers-subagent-driven-development** |
| Parallel isolated work | **superpowers-using-git-worktrees**, **superpowers-dispatching-parallel-agents** |
| Bug / failure investigation | **superpowers-systematic-debugging**, **superpowers-verification-before-completion** |
| New logic / behavior | **superpowers-test-driven-development** (JUnit 5 / H2; Playwright for E2E) |
| Pre-merge review | **superpowers-requesting-code-review**, **superpowers-receiving-code-review** |
| Branch done, merge/PR decision | **superpowers-finishing-a-development-branch** |
| Cyclic fix/verify/cleanup loops | **`korus-cycle-prompts`** + **`scripts/Start-KorusCycleUnattended.ps1`** |
| How skills work in Cursor | **superpowers-using-superpowers** |

## Spec-kit is the project standard for features

For **tracked features** in `specs/<NNN-feature>/`:

1. **speckit-specify** - create/update `spec.md`
2. **speckit-plan** - `plan.md`, `research.md`, contracts
3. **speckit-tasks** - `tasks.md`
4. **speckit-implement** - execute tasks, update `tasks.md` checkboxes

Current active plan: `specs/011-korus-cloud-platform/plan.md` (Phase 0–1 closed). **Live-server ops:** `specs/015-live-server-ops-backlog/` (deferred registry). **VM lab acceptance:** `specs/029-qemu-vm-acceptance/` (VMA registry). **Product verification:** `specs/030-vpp-product-verification/` (VPP — всеобъемлющая проверка продукта, `run-vpp-until-green.ps1` until GREEN).

Do **not** replace speckit with superpowers `writing-plans` for formal spec-kit features.

**Note (T02660):** Informal UI-agent notes from archived specs (e.g. early UI workflow drafts) are superseded by **spec 026** orchestrator + role skills. New UI work → `korus-ui-orchestrator` first.

## Project constraints (override generic superpowers defaults)

| Constraint | Rule |
|------------|------|
| **User communication** | Russian for user-facing chat; code/logs/identifiers stay as in repo |
| **Commits** | Only when user explicitly asks |
| **Git push** | **Only** `.\scripts\git-push.ps1` (never raw `git push` — corporate proxy breaks GitHub). See `.cursor/rules/git-push-proxy-bypass.mdc` |
| **Scope** | Minimal diff; do not refactor unrelated code |
| **PR gate** | `./gradlew buildIntegrity` |
| **Product gate** | VPP until GREEN — `.\scripts\run-vpp-until-green.ps1`; on FAIL: fix, **resume from `vpp-checkpoint.json`** (not full restart), then **one final full verify pass** (spec 030, `vpp-until-green.mdc`) |
| **Live stack** | Docker Compose + Ansible — [`deploy/ansible/DEPLOY_QUICKSTART.md`](../../deploy/ansible/DEPLOY_QUICKSTART.md). QEMU scripts — **not in Git** (`.gitignore`, local optional) |
| **QEMU long jobs** | **Do not block** on long IDE terminals (rebuild 15–90 min). Launch `qemu-sync-api-core.ps1 -NoCache`, poll `qemu-guest-job.ps1` or `-Loop` every **3 min**. **WHPX only** — TCG forbidden (`qemu-whpx-required.mdc`, `qemu-fast-up.ps1`). See [`.cursor/rules/qemu-host-isolation.mdc`](../../.cursor/rules/qemu-host-isolation.mdc) |
| **E2E** | Playwright — [`tests/e2e-web/README.md`](../../tests/e2e-web/README.md) against running API/UI |
| **Stage/prod** | **No stage until September 2026.** Deferred ops → [`specs/015-live-server-ops-backlog/`](../../specs/015-live-server-ops-backlog/). Do not list LSO/T601+ in agent backlogs. |

When a superpowers skill suggests host Docker for full stack — use **deploy/ansible** or documented compose scripts on Linux/VM.

## Skill locations

| Set | Path |
|-----|------|
| Spec-kit | `.cursor/skills/speckit-*/SKILL.md` (committed) |
| Superpowers | `.cursor/skills/superpowers-*/` (junctions -> `.cursor/superpowers/skills/`) |
| This bridge | `.cursor/skills/korus-agent-workflow/SKILL.md` |
| **UI orchestrator** | `.cursor/skills/korus-ui-orchestrator/` (spec **026** — role pipeline, gates A–G, smokes S1–S9) |
| UI subagents (optional) | `.cursor/agents/korus-ui-{designer,qa}.md` |
| UI roles | `.cursor/skills/korus-ui-role-{analyst,designer,ux-evaluator,engineer,qa,visual,i18n}/` |
| Web UI (engineer ref) | `.cursor/skills/korus-webui/SKILL.md` |
| Mobile web UI | `.cursor/skills/korus-webui-mobile/SKILL.md` |
| **Native mobile client** | `.cursor/skills/korus-mobile-orchestrator/` (spec **032** — analyst→architect→designer→ux-eval→plan-reviewer→engineer→QA) |
| **Desktop Java client** | `.cursor/skills/korus-desktop-orchestrator/` (spec **031** — + designer→ux-evaluator) |
| Desktop roles | `.cursor/skills/korus-desktop-role-{analyst,architect,designer,ux-evaluator,plan-reviewer,engineer,qa-verifier}/` |
| Desktop subagents | `.cursor/agents/korus-desktop-{architect,designer,ux-evaluator,qa}.md` |
| Cycle prompts | `.cursor/skills/korus-cycle-prompts/SKILL.md`, catalog [`.cursor/prompts/`](../../prompts/README.md) |
| Desktop orchestrator | `.cursor/skills/korus-desktop-orchestrator/` (spec **031** — plan → review → implement → QA) |

Install/update superpowers: see `AGENTS.md` section "Superpowers (Cursor skills)".
