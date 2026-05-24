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
./scripts/smoke-web-parity-api.ps1 -BaseUrl http://127.0.0.1:18080
./scripts/smoke-web-parity-ws.ps1 -BaseUrl http://127.0.0.1:18080 -WebBaseUrl http://127.0.0.1:19088
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

## 7) Runtime gates (engineering closure)

Tasks `T010`, `T016`, `T022` are closed with:

- `./gradlew.bat :modules:web-client:test` (`WebUiParityAssetsTest`)
- `./gradlew.bat :modules:core-api:test` (pin path)
- `scripts/smoke-web-parity-api.ps1` (messaging + file/export API)
- `scripts/smoke-web-parity-ws.ps1` (WS reconnect + rtc_signal envelope)

Optional operator browser sign-off when stack is available: see `HANDOFF.md`.
Record operator outcomes in `runtime-gate-report.md` if re-run on live stack.
