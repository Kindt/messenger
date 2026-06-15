# Operator Handoff (Spec 002)

This handoff is the shortest path for optional browser re-validation after engineering closure.

## Pre-check

- Confirm target commit/build to validate.
- Ensure runtime environment is up and reachable.
- Confirm operator has access to browser + logs.

## Run Order

1. Follow `quickstart.md` sections for runtime setup.
2. **US9 inner loop** (stack already up via QEMU):
   - `.\scripts\playwright-dev-loop.ps1 -Tier api|ui-messaging|ui-conference|...`
   - `.\scripts\playwright-dev-loop.ps1 -Tier all-inner` when all tiers should be green
   - Read `deploy/qemu/run/plan-failure-analysis.json` on failure
3. **Outer gate once** per fix batch:
   - `.\scripts\qemu-plan-orchestrator.ps1 -SkipVmUp`
4. Execute optional browser re-validation (when stack is up):
   - messaging DOM (`T010` scenarios in quickstart)
   - file upload UI + export artifact download (`T016`)
   - RTC UI controls (`T022`)
5. Fill evidence in `runtime-gate-report.md`.
6. Mirror outcomes:
   - `tasks.md` (gate statuses)
   - `parity-report.md` (final operational note)

## Acceptance Rule

- Engineering closure is complete (all `tasks.md` items checked).
- QEMU golden path: `deploy/qemu/README.md`; auto report: `.\scripts\write-runtime-gate-report.ps1 -WebBaseUrl http://127.0.0.1:19088 -ApiBaseUrl http://127.0.0.1:18080`
- Optional: if browser re-validation passes on live stack, update `runtime-gate-report.md` operator section.
- If browser re-validation fails, create follow-up fix task(s); do not reopen closed engineering gates without cause.

## Reference Artifacts

- `README.md`
- `parity-matrix.md`
- `parity-report.md`
- `IMPLEMENTATION_LOG.md`
