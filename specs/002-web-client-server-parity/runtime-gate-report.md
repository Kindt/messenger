# Runtime Gate Report Template (Operator-Run)

Use this template to record environment-dependent runtime checks for deferred tasks:

- `T010` (US1 messaging)
- `T016` (US2 file/export)
- `T022` (US3 realtime/call)

## Execution Context

- **Date**:
- **Operator**:
- **Environment**:
- **Build/Commit**:
- **Stack start method**: `scripts/korus-web-up.ps1` / `scripts/korus-web-up.sh` / other

## T010 — Messaging parity smoke

- **Status**: `passed` / `failed`
- **Scenarios covered**:
  - create chat
  - add/remove member
  - send/edit/delete/reply/reaction/pin/forward
- **Notes / deviations**:
- **Evidence (logs/screenshots/links)**:

## T016 — File/export parity smoke

- **Status**: `passed` / `failed`
- **Scenarios covered**:
  - upload/download file
  - create/revoke public link
  - request export
  - inspect status and artifact download
- **Notes / deviations**:
- **Evidence (logs/screenshots/links)**:

## T022 — Realtime/call parity smoke

- **Status**: `passed` / `failed`
- **Scenarios covered**:
  - ws reconnect
  - incoming sync convergence
  - start/accept/hangup call
  - participant updates
- **Notes / deviations**:
- **Evidence (logs/screenshots/links)**:

## Final Decision

- **Operational sign-off**: `approved` / `blocked`
- **Blocking items (if any)**:
- **Follow-up tasks**:
