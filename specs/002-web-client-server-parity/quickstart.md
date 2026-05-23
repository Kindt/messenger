# Quickstart: Web Client Server Parity

## 1) Validate module tests

```powershell
./gradlew.bat :modules:web-client:test
```

## 2) Run full integrity gate

```powershell
./gradlew.bat buildIntegrity
```

## 3) Start local web stack (when runtime validation required)

```powershell
./scripts/korus-web-up.ps1
```

Alternative (bash):

```bash
./scripts/korus-web-up.sh
```

## 4) Execute web smoke checks

```powershell
./scripts/smoke-korus-web.ps1
./scripts/smoke-korus-web.ps1 -CheckApi
```

Alternative (bash):

```bash
./scripts/smoke-korus-web.sh
./scripts/smoke-korus-web.sh --check-api
```

## 5) Manual parity scenarios (minimum set)

- Messaging parity:
  - create/open chat
  - send/edit/delete/reply/reaction/pin/forward
- File parity:
  - upload/download
  - create/revoke public link
- Export parity:
  - request export
  - inspect job status
  - list/download artifacts
- Realtime parity:
  - reconnect WS
  - verify unread/preview convergence
- RTC parity:
  - start/accept/hangup call
  - toggle mic/cam/screen
- PWA/settings parity:
  - check update banner
  - push opt-in (if VAPID configured)

## 6) Completion checklist

- `:modules:web-client:test` green
- `buildIntegrity` green
- parity tasks `T001..T033` in `tasks.md` resolved or explicitly deferred
- `spec.md`/`plan.md`/`tasks.md` status synchronized

## 7) Deferred runtime gates (operator-run)

Run these on an available environment when localhost/lb runtime is ready:

- `T010` (US1 messaging): create chat, add/remove member, send/edit/delete/reply/reaction/pin/forward
- `T016` (US2 file/export): upload/download, create/revoke public link, request export, verify artifacts download
- `T022` (US3 realtime/call): ws reconnect, incoming sync convergence, start/accept/hangup call, participant updates

Record result as `passed`/`failed` in `tasks.md` and mirror final note in `parity-report.md`.
Use `runtime-gate-report.md` as the canonical evidence template for operator-run validation.
