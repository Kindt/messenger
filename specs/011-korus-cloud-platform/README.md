# Spec 011: Korus Cloud Platform

Платформа **«своё облако»** на базе Korus Messenger: hosted Cells (B), internal private cloud (C), infra platform (D), managed SaaS (A — позже).

| Artifact | Description |
|----------|-------------|
| [`spec.md`](spec.md) | User stories, FR, success criteria |
| [`plan.md`](plan.md) | Phases 0–4, architecture summary |
| [`tasks.md`](tasks.md) | Actionable checklist T01101–T01134 |
| [`design/cloud-platform-design.md`](design/cloud-platform-design.md) | **Полный design** (секции 1–7, approved) |
| [`contracts/cell-platform-contract.md`](contracts/cell-platform-contract.md) | Acceptance per phase |
| [`contracts/cell-manifest-contract.md`](contracts/cell-manifest-contract.md) | Manifest schema contract |
| [`quickstart.md`](quickstart.md) | Phase 0–1 commands (QEMU / staging) |
| [`research.md`](research.md) | Decision log (brainstorming outcomes) |

**Status:** Phase 0–1 **engineering closed** (2026-06-16); Phase 2+ blocked on commercial host (Sep 2026+).  
**Related:** spec 003 (Ansible deploy), spec 007/010 (stage TLS), [`docs/DEV_STACK_PROFILES.md`](../../docs/DEV_STACK_PROFILES.md)

**Constraint:** Windows dev host — **QEMU only** for runtime smokes ([`.cursor/rules/qemu-host-isolation.mdc`](../../.cursor/rules/qemu-host-isolation.mdc)).
