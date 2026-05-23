# Operator Handoff (Spec 002)

This handoff is the shortest path to close deferred runtime gates.

## Pre-check

- Confirm target commit/build to validate.
- Ensure runtime environment is up and reachable.
- Confirm operator has access to browser + logs.

## Run Order

1. Follow `quickstart.md` sections for runtime setup.
2. Execute deferred manual gates:
   - `T010` (messaging parity)
   - `T016` (file/export parity)
   - `T022` (realtime/call parity)
3. Fill evidence in `runtime-gate-report.md`.
4. Mirror outcomes:
   - `tasks.md` (gate statuses)
   - `parity-report.md` (final operational note)

## Acceptance Rule

- If all three gates pass, mark operational sign-off as approved.
- If any gate fails, mark blocked and create follow-up fix task(s).

## Reference Artifacts

- `README.md`
- `parity-matrix.md`
- `parity-report.md`
- `IMPLEMENTATION_LOG.md`
