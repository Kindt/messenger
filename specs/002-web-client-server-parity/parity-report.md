# Parity Report: Web Client Server Parity

**Date**: 2026-05-23  
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

- ✅ `./gradlew.bat :modules:web-client:test`
- ✅ `./gradlew.bat buildIntegrity`
- ⏳ Manual runtime smoke on live stack (deferred):
  - messaging checklist (`T010`)
  - file/export checklist (`T016`)
  - realtime/call checklist (`T022`)

## Deferred Items

Deferred due to environment/runtime gate requirements (stack availability and operator-run browser scenarios):

- `T010`
- `T016`
- `T022`

These do not block code-level parity structure completion, but remain required for full operational sign-off.
