# Hot-Plug Governance Handoff — 2026-05-24

Tasks: **T048** (ADR approval), **T056** (constitution exception)

## Summary

US3 hot-plug implementation is **code-complete and runtime-verified on QEMU**. Remaining work is **formal governance sign-off**, not engineering.

## Evidence Pack

| Artifact | Status |
|---|---|
| ADR draft | `docs/adr/ADR-hotplug-deployment-split.md` |
| Constitution proposal | `docs/proposals/constitution-v1.1-hotplug-bounded-exception.md` |
| NATS contract | `docs/NATS_SUBJECTS_INTEROP.md` (hot-plug subjects) |
| Integration test | `modules/core-api/.../HotPlugIndexerTest.java` |
| Smoke | `scripts/smoke-hotplug-indexer.ps1` (QEMU + NATS tunnel) |
| US2 observability | `scripts/smoke-us2-epic01-qemu.ps1` — T024 + hot-plug green 2026-05-24 (post-redeploy) |
| Metrics | `modules/common/.../hotplug/HotPlugMetrics*.java` |
| Full tests | `./gradlew test` green (2026-05-24) |

## Sign-Off Checklist (manual)

Copy into ADR **Approval Checklist** when signed:

- [ ] **Architecture** — bounded split preserves module dependency direction; no reverse deps
- [ ] **Product** — operational value (indexer hot-plug) accepted for current release scope
- [ ] **Ops/SRE** — `/health`, `/ready`, heartbeat metrics, smoke runbook sufficient

When all three are checked, update:

1. `docs/adr/ADR-hotplug-deployment-split.md` → `Status: accepted`
2. `docs/proposals/constitution-v1.1-hotplug-bounded-exception.md` → `Status: accepted`
3. `specs/001-system-review-refactoring/tasks.md` → T048, T056 `[x]`

## Rollback (if not approved)

Keep constitution v1.0.0; disable hot-plug via env:

- `HOTPLUG_INDEXER_PRESENCE_REQUIRED=false`
- run indexer in-process via existing docker compose profile

No wire-format or schema rollback required.
