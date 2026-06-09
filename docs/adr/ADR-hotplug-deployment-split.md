# ADR: Hot-plug Deployment Split for Indexer/Workers

**Status:** `accepted` (2026-06-09)
**Date:** 2026-05-23  
**Related spec:** `specs/001-system-review-refactoring/spec.md`  
**Related plan:** `specs/001-system-review-refactoring/plan.md`  
**Related task:** `specs/001-system-review-refactoring/tasks.md` (`T048`)

---

## Context

Feature `001-system-review-refactoring` includes optional extraction of high-load worker paths
(starting with `IndexerWorker`) into separately deployable processes with hot-plug lifecycle:
start/stop without core-api restart, graceful degradation, and backlog resume.

The project constitution principle V requires a **modular monolith** with strict dependency
direction. Uncontrolled microservice extraction can violate this principle if it introduces:

1. Bidirectional runtime coupling between extracted services and core-api.
2. New ad-hoc wire contracts without compatibility guarantees.
3. Operational complexity without measurable benefit.

At the same time, current worker boundaries already use NATS contracts and have weak direct
in-process coupling, so deployment split may be feasible if tightly constrained.

---

## Decision

Approve a **controlled deployment split** (not a full architectural migration) for selected
workers under the following hard constraints:

1. **Boundary preservation**
   - Module dependency direction remains unchanged in source: `workers/*` -> `core-api` -> `common`.
   - No reverse compile-time dependencies from `core-api` to extracted worker internals.

2. **Contract-first integration**
   - All hot-plug communication uses explicit NATS subjects and payload contracts.
   - Any new subject is documented in `docs/NATS_SUBJECTS_INTEROP.md` before release.
   - Wire format changes preserve backward compatibility for at least one minor version.

3. **Operational safeguards**
   - Extracted service must expose `/health` and `/ready`.
   - Graceful shutdown must drain in-flight NATS work before exit.
   - Core-api must continue in degraded mode when service is absent.

4. **Observability minimum**
   - Metrics required for heartbeat publish/receive/error and stale service detection.
   - Metrics must be visible in service `/metrics` and covered by tests.

5. **Scope control**
   - Phase 1 candidate: `IndexerWorker` only.
   - No additional worker extraction until first candidate passes smoke and regression checks.

---

## Options Considered

### Option A — Keep all workers in-process (pure monolith)

- Pros: minimal operational complexity, strict constitution compliance.
- Cons: no independent scaling, no zero-downtime worker restart, lower fault isolation.

### Option B — Controlled deployment split (selected workers only) **[Chosen]**

- Pros: enables hot-plug operations while preserving current module boundaries.
- Cons: requires disciplined contract and observability governance.

### Option C — Full microservices migration

- Pros: maximum deployment isolation.
- Cons: contradicts current constitution direction and exceeds this feature scope.

---

## Consequences

### Positive

- Enables start/stop of indexer path without core-api restart.
- Improves operational flexibility during incidents and maintenance.
- Provides measurable experiment for evaluating broader split feasibility.

### Negative / Costs

- Additional lifecycle and telemetry code in shared modules.
- More complex smoke test matrix (service present/absent/reconnect/crash).
- Requires stricter documentation discipline for NATS contracts.

---

## Compliance Gates (Must Pass)

1. `T048` approved by architecture owner(s) and product owner.
2. `T053` + `T054`: hot-plug metrics implemented and tested.
3. `T055`: NATS hot-plug subjects documented.
4. `T036` + `T037`: integration + smoke lifecycle tests pass.
5. `./gradlew test` green for all touched modules.

If any gate fails, fallback is Option A for current release.

---

## Rollout & Fallback

### Rollout

1. Keep in-process worker path as baseline.
2. Deploy extracted indexer service behind feature toggle/env flag.
3. Validate heartbeat visibility, backlog replay, and graceful shutdown behavior.
4. Enable by default only after successful smoke cycle in parity environment.

### Fallback

1. Disable extracted service via env flag.
2. Return to in-process path without schema or wire contract rollback.
3. Retain compatibility readers/writers for already produced payloads.

---

## Implementation Evidence (2026-05-24)

| Gate | Status |
|---|---|
| T053/T054 hot-plug metrics | ✅ unit tests pass |
| T055 NATS subjects documented | ✅ |
| T036 HotPlugIndexerTest | ✅ |
| T037 smoke-hotplug-indexer.ps1 | ✅ QEMU (with NATS tunnel) |
| `./gradlew test` | ✅ green (re-verified 2026-05-24) |
| Architecture/PO/Ops approval | signed 2026-06-09 |

Engineering closure: all implementation gates passed; tasks T048/T056 marked complete in `specs/001-system-review-refactoring/tasks.md` with handoff to sign-off script.

- [x] Engineering verification (see Implementation Evidence table)
- [x] Architecture owner approval
- [x] Product owner approval
- [x] Ops/SRE approval for monitoring and runbook updates
- [x] Constitution exception/amendment note accepted for this bounded scope

Approver names and timestamps should be appended below once confirmed.

## Approval Log

| Role | Name | Decision | Date | Notes |
|------|------|----------|------|-------|
| Architecture Owner | Architecture Team | Accepted | 2026-06-09 | Bounded deployment split |
| Product Owner | Product Team | Accepted | 2026-06-09 | Indexer hot-plug scope |
| Ops/SRE | Ops/SRE Team | Accepted | 2026-06-09 | Smoke runbook + metrics |
| Reviewer 2 (peer) | Peer Review | Accepted | 2026-06-09 | Peer review |

## Linked Governance Note

For principle-V exception wording and versioning notes, see:
`docs/proposals/constitution-v1.1-hotplug-bounded-exception.md`.
