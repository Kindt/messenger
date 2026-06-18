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
| Web UI, i18n, Tailwind | **korus-webui** |
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

Current active plan: `specs/011-korus-cloud-platform/plan.md` (Phase 0–1 closed). **Live-server ops:** `specs/015-live-server-ops-backlog/` (deferred registry).

Do **not** replace speckit with superpowers `writing-plans` for formal spec-kit features.

## Project constraints (override generic superpowers defaults)

| Constraint | Rule |
|------------|------|
| **User communication** | Russian for user-facing chat; code/logs/identifiers stay as in repo |
| **Commits** | Only when user explicitly asks |
| **Scope** | Minimal diff; do not refactor unrelated code |
| **PR gate** | `./gradlew buildIntegrity` |
| **Live stack** | Docker Compose + Ansible — [`deploy/ansible/DEPLOY_QUICKSTART.md`](../../deploy/ansible/DEPLOY_QUICKSTART.md). QEMU scripts — **not in Git** (`.gitignore`, local optional) |
| **E2E** | Playwright — [`tests/e2e-web/README.md`](../../tests/e2e-web/README.md) against running API/UI |
| **Stage/prod** | **No stage until September 2026.** Deferred ops → [`specs/015-live-server-ops-backlog/`](../../specs/015-live-server-ops-backlog/). Do not list LSO/T601+ in agent backlogs. |

When a superpowers skill suggests host Docker for full stack — use **deploy/ansible** or documented compose scripts on Linux/VM.

## Skill locations

| Set | Path |
|-----|------|
| Spec-kit | `.cursor/skills/speckit-*/SKILL.md` (committed) |
| Superpowers | `.cursor/skills/superpowers-*/` (junctions -> `.cursor/superpowers/skills/`) |
| This bridge | `.cursor/skills/korus-agent-workflow/SKILL.md` |
| Web UI | `.cursor/skills/korus-webui/SKILL.md` |

Install/update superpowers: see `AGENTS.md` section "Superpowers (Cursor skills)".
