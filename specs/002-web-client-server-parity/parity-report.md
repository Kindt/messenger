# Parity Report: Web Client Server Parity

**Date**: 2026-05-24  
**Spec**: `specs/002-web-client-server-parity/spec.md`  
**Plan**: `specs/002-web-client-server-parity/plan.md`

## Coverage Summary

- Core chat/message flows: implemented and wired in web-client.
- File/public-link flows: implemented and integrated.
- User export lifecycle: implemented (request/status/download/attachments/cancel path present in client flow).
- Realtime/ws convergence: helper boundaries extracted and reconnect flow preserved.
- RTC/call signaling: delegated through `ui-rtc-utils.js` with contract-compatible envelope.
- PWA/settings/push/service-worker controls: delegated through `ui-pwa-settings-utils.js`, env-guard behavior preserved.
- Servlet boundary hardening: completed in
  - `UpstreamProxyServlet`
  - `WebClientApplication`
  - `WebClientEnvServlet`
  without route/env contract drift.

## Contract Check

- Route boundaries preserved: `/`, `/health`, `/api/*`, `/web-client-env.js`
- Env object fields preserved: `wsUrl`, `iceServersJson`, `vapidPublicKey`, `disableServiceWorker`
- RTC WS envelope preserved (`type=rtc_signal`, `chatId`, `payload.kind`)

## Verification Status

- ✅ `./gradlew.bat :modules:web-client:test` (includes `WebUiParityAssetsTest`)
- ✅ `./gradlew.bat :modules:core-api:test` (pin repository/service coverage)
- ✅ `./gradlew.bat buildIntegrity`
- ✅ Automated smoke scripts ready:
  - `scripts/smoke-web-parity-api.ps1` (T010 + T016 API paths)
  - `scripts/smoke-web-parity-ws.ps1` (T022 WS/protocol paths)
- ✅ Operator browser re-validation optional (`HANDOFF.md`); **26/26 Playwright PASS** on QEMU 2026-06-12

## Deferred Items

None blocking spec 002 closure. Optional operator-only browser scenarios (DOM upload, RTC UI controls) remain documented in `HANDOFF.md` for environments with a live stack.
