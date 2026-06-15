---
name: korus-agent-workflow
description: "Korus Messenger agent workflow bridge - when to use speckit-* vs superpowers-* skills, project constraints (QEMU-only runtime, Russian comms, spec-first features)."
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
| Web UI, i18n, Tailwind, `:19088` | **korus-webui** (then speckit **005** or superpowers debugging) |
| Implementation plan from approved design (non-spec-kit) | **superpowers-writing-plans** |
| Execute a written plan with checkpoints | **superpowers-executing-plans** or **superpowers-subagent-driven-development** |
| Parallel isolated work | **superpowers-using-git-worktrees**, **superpowers-dispatching-parallel-agents** |
| Bug / failure investigation | **superpowers-systematic-debugging**, **superpowers-verification-before-completion** |
| New logic / behavior | **superpowers-test-driven-development** (JUnit 5 / H2; Playwright for E2E) |
| Pre-merge review | **superpowers-requesting-code-review**, **superpowers-receiving-code-review** |
| Branch done, merge/PR decision | **superpowers-finishing-a-development-branch** |
| How skills work in Cursor | **superpowers-using-superpowers** |

## Spec-kit is the project standard for features

For **tracked features** in `specs/<NNN-feature>/`:

1. **speckit-specify** - create/update `spec.md`
2. **speckit-plan** - `plan.md`, `research.md`, contracts
3. **speckit-tasks** - `tasks.md`
4. **speckit-implement** - execute tasks, update `tasks.md` checkboxes

Current active plan: `specs/007-platform-stage-readiness/plan.md` (ops tail; specs 008/009 closed).

Do **not** replace speckit with superpowers `writing-plans` for formal spec-kit features. Superpowers planning complements speckit for ad-hoc tasks, spikes, or debugging branches.

## Superpowers complements speckit

Use **superpowers-*** for engineering discipline outside (or before) the spec-kit pipeline:

- **brainstorming** - explore alternatives before committing to a spec
- **test-driven-development** - RED-GREEN-REFACTOR for Java/Gradle modules
- **systematic-debugging** - structured root-cause analysis (QEMU stack, Playwright, Gradle)
- **executing-plans** / **subagent-driven-development** - batch execution with review gates
- **using-git-worktrees** - isolated branches (respect: no force-push to main)
- **requesting-code-review** / **receiving-code-review** - structured review loops

## Project constraints (override generic superpowers defaults)

These **always** apply in Korus Messenger:

| Constraint | Rule |
|------------|------|
| **Windows dev runtime** | **QEMU only** - no Docker/Ansible on host. Use `.\scripts\qemu-up.ps1`, `qemu-redeploy.ps1`. API `127.0.0.1:18080`, UI `127.0.0.1:19088`. |
| **User communication** | Russian for user-facing chat; code/logs/identifiers stay as in repo |
| **Commits** | Only when user explicitly asks |
| **Scope** | Minimal diff; do not refactor unrelated code |
| **PR gate** | `./gradlew buildIntegrity` |
| **E2E acceptance** | Inner: `playwright-dev-loop.ps1 -Tier …`; outer: full orchestrator before sign-off |
| **QEMU lifecycle** | Do not run `qemu-down` unless user asks |

When a superpowers skill suggests `docker compose`, `npm install` for full stack, or host Ansible - **adapt** to QEMU guests or Gradle-only host builds.

## Skill locations

| Set | Path |
|-----|------|
| Spec-kit | `.cursor/skills/speckit-*/SKILL.md` (committed) |
| Superpowers | `.cursor/skills/superpowers-*/` (junctions -> `.cursor/superpowers/skills/`) |
| This bridge | `.cursor/skills/korus-agent-workflow/SKILL.md` |
| Web UI | `.cursor/skills/korus-webui/SKILL.md` |

Install/update superpowers: see `AGENTS.md` section "Superpowers (Cursor skills)".
