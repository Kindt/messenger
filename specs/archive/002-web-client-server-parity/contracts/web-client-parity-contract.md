# Contract: Web Client Parity Boundaries

## 1. HTTP Route Compatibility (must not change)

Web-client module keeps these public boundaries:

- Static UI root: `/`
- Health: `/health`
- API proxy: `/api/*` (upstream passthrough)
- Runtime env script: `/web-client-env.js`

No path remapping is allowed in parity phases unless explicitly approved.

## 2. Runtime Env Script Contract (`/web-client-env.js`)

The browser bootstrap object remains:

```js
window.__WEB_CLIENT__ = {
  wsUrl: "<string>",
  iceServersJson: null | "<json-string>",
  vapidPublicKey: null | "<string>",
  disableServiceWorker: <boolean>
};
```

### Rules

- Field names are stable and case-sensitive.
- `iceServersJson` remains JSON-string-or-null (not parsed object).
- `disableServiceWorker` remains boolean.

## 3. API Proxy Header Policy

`UpstreamProxyServlet` must keep current behavior:

- Filters hop-by-hop request headers:
  - `connection`, `keep-alive`, `proxy-authenticate`, `proxy-authorization`,
  - `te`, `trailers`, `transfer-encoding`, `upgrade`, `host`, `content-length`
- Filters hop-by-hop response headers:
  - `connection`, `keep-alive`, `transfer-encoding`, `trailer`

No semantic changes to auth/header passthrough without dedicated approval.

## 4. Realtime and RTC Event Shape

WebSocket message envelope consumed by web-client remains compatible with current server events.

RTC signal envelope remains:

```json
{
  "type": "rtc_signal",
  "chatId": "<uuid>",
  "payload": {
    "kind": "offer|answer|candidate|hangup",
    "...": "..."
  }
}
```

No change in `kind` semantics in parity phases.

## 5. Export and File User Flows

User-side web-client integrations must remain aligned with existing server endpoint semantics:

- Files: `/api/v1/files/*`
- Chat export: `/api/v1/chats/{chatId}/export/*`

Parity work may improve UI flow, but must not reinterpret server status contracts.
