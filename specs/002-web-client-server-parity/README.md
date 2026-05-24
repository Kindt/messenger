# Web Client Server Parity (Spec 002)

This directory contains the complete Spec-Kit package for bringing `modules/web-client` to parity with the current non-admin server surface.

## Current Status

- Spec package closure: **completed** (2026-05-24)
- Local quality gates: **green** (`:modules:web-client:test`, `:modules:core-api:test`, `buildIntegrity`)
- Runtime gates `T010` / `T016` / `T022`: **closed** (API/WS smoke scripts + unit/static tests)
- Optional operator browser sign-off: `HANDOFF.md`

## Artifact Map

- `spec.md` — feature scope, requirements, assumptions
- `plan.md` — phased implementation and closure snapshot
- `tasks.md` — execution checklist (all tasks complete)
- `research.md` — design decisions and rationale
- `data-model.md` — parity domain model
- `contracts/web-client-parity-contract.md` — frozen compatibility contracts
- `checklists/requirements.md` — quality and closure checklist
- `quickstart.md` — validation flow and smoke script commands
- `parity-matrix.md` — baseline endpoint-to-flow coverage map
- `parity-report.md` — closure report
- `runtime-gate-report.md` — engineering closure evidence
- `IMPLEMENTATION_LOG.md` — commit-level traceability for implementation and closure
- `HANDOFF.md` — optional operator browser checklist

## Smoke Scripts

- `scripts/smoke-web-parity-api.ps1` — messaging + file/export API (T010, T016)
- `scripts/smoke-web-parity-ws.ps1` — WS reconnect + rtc_signal (T022)

## Scope Guardrails

- Non-admin user surface only; admin APIs excluded.
- No backend contract changes; servlet/env route compatibility frozen.
