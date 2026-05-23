# Web Client Server Parity (Spec 002)

This directory contains the complete Spec-Kit package for bringing `modules/web-client` to parity with the current non-admin server surface.

## Current Status

- Spec package closure: **completed**
- Local quality gates: **green** (`:modules:web-client:test`, `buildIntegrity`)
- Deferred runtime gates (operator-run): `T010`, `T016`, `T022`

## Artifact Map

- `spec.md` — feature scope, requirements, assumptions
- `plan.md` — phased implementation and closure snapshot
- `tasks.md` — execution checklist with dependencies and deferred runtime references
- `research.md` — design decisions and rationale
- `data-model.md` — parity domain model
- `contracts/web-client-parity-contract.md` — frozen compatibility contracts
- `checklists/requirements.md` — quality and closure checklist
- `quickstart.md` — validation flow and runtime instructions
- `parity-matrix.md` — baseline endpoint-to-flow coverage map
- `parity-report.md` — closure report with deferred items
- `runtime-gate-report.md` — operator-run evidence template for deferred runtime checks
- `IMPLEMENTATION_LOG.md` — commit-level traceability for implementation and closure
- `HANDOFF.md` — minimal operator checklist to close deferred runtime gates

## How to Finish Deferred Runtime Gates

1. Prepare runtime environment (stack available and reachable).
2. Execute manual scenarios for:
   - `T010` messaging parity
   - `T016` file/export parity
   - `T022` realtime/call parity
3. Record evidence in `runtime-gate-report.md`.
4. Mirror results in:
   - `tasks.md` (mark runtime tasks `passed`/`failed`)
   - `parity-report.md` (final operational sign-off note)

## Scope Guardrails

- Admin endpoints are out of scope.
- Route/env contracts stay backward-compatible.
- Structural refactoring is incremental; no framework migration.
