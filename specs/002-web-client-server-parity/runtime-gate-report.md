# Runtime Gate Report — 2026-05-24 (QEMU partial)

Tasks: `T010`, `T016`, `T022` (spec 002)

## Execution Context

- **Date**: 2026-05-24
- **Operator**: automated API smoke + prior manual QEMU runs
- **Environment**: QEMU two-VM (`API http://127.0.0.1:18080`, web `http://127.0.0.1:19088`)
- **Build/Commit**: `e56557f` branch `001-system-review-refactoring` (local, unpushed)
- **Stack start method**: existing QEMU (`scripts/qemu-up.ps1 -KeepDisks`)

## T010 — Messaging parity smoke

- **Status**: `partial` (API green, pin 500, UI manual pending)
- **Scenarios covered**:
  - create chat — OK (`scripts/smoke-web-parity-api.ps1`)
  - add/remove member — OK
  - send/edit/delete/reply/reaction/pin/forward — OK except **pin POST → 500**
- **Notes / deviations**:
  - `POST .../messages/{id}/pin` returns 500 on QEMU; other message ops pass.
  - Web UI DOM parity not exercised in this run (browser checklist still open).
- **Evidence**: `scripts/smoke-web-parity-api.ps1 -BaseUrl http://127.0.0.1:18080`

## T016 — File/export parity smoke

- **Status**: `partial` (export API green; file upload UI manual pending)
- **Scenarios covered**:
  - request export — OK (via `smoke-export-chat.ps1` inside parity API smoke)
  - inspect status — OK (`export_v1`)
  - upload/download file — not run (API/UI manual)
  - create/revoke public link — not run (manual)
  - artifact download — skipped (`-SkipDownload`)
- **Notes / deviations**: export path verified; file/public-link flows need UI or dedicated API smoke.
- **Evidence**: job `0664d716-067c-45ae-9fad-d75d59c4fa67` on chat `d30b0062-...`

## T022 — Realtime/call parity smoke

- **Status**: `pending` (manual browser)
- **Scenarios covered**: none in this run
- **Notes / deviations**: requires WS reconnect + RTC checklist in browser on `19088`.

## Final Decision

- **Operational sign-off**: `blocked` (pin 500 + UI/WS gates open)
- **Blocking items**:
  1. Investigate/fix pin API 500 on QEMU
  2. Manual UI passes for T010/T016/T022 per `HANDOFF.md`
- **Follow-up tasks**: fix pin; optional `smoke-web-parity-files-api.ps1` for upload/public-link
