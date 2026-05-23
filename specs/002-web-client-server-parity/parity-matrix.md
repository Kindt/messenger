# Parity Matrix Baseline (T001/T002)

Baseline inventory of non-admin server resources against current `modules/web-client/src/main/resources/webui/app.js` flows.

**Generated**: 2026-05-23  
**Scope**: non-admin user endpoints only  
**Legend**: `covered` / `partial` / `missing`

## Resource Coverage

| Domain | Server Resource | Endpoint Family | Status | Notes |
|---|---|---|---|---|
| auth | `AuthResource` | `/v1/auth/login|register|refresh|logout` | covered | Full auth + refresh session flow present in web-client. |
| chats | `ChatResource` | `/v1/chats/*` | partial | Core chat/member/read/unread/typing covered; needs explicit parity sweep for all member/role/bans edge paths. |
| messages | `MessageResource` | `/v1/chats/{chatId}/messages/*` | partial | Send/edit/delete/reactions/pin/forward present; versions/plaintext-preview require explicit gap verification. |
| files | `FileResource` | `/v1/files/*` | partial | Upload/download/public links present; public auth-link and message-ref flows need parity checklist confirmation. |
| export | `ExportResource` | `/v1/chats/{chatId}/export/*` | partial | Request/status/download integrated; full attachments/cancel state transitions require parity sweep. |
| contacts | `ContactResource` | `/v1/contacts/*` | covered | Contact list/import/delete flows present. |
| search | `SearchResource` | `/v1/search/users|messages` | covered | Sidebar/global search flows present. |
| users | `UserResource` | `/v1/users/me*` | covered | Profile/presence/heartbeat/saved-chat flows present. |
| blocks | `BlocksResource` | `/v1/blocks/*` | partial | Block list/updates appear in settings flow; needs endpoint-level parity confirmation. |
| devices | `DeviceResource` | `/v1/me/devices/*` | covered | Device and push registration/removal mapped. |
| conference | `ConferenceResource` | `/v1/chats/{chatId}/conferences*`, `/v1/conferences/*` | partial | Active conference/call flows present; join/leave/end edge handling needs parity verification. |
| media | `MediaCapabilitiesResource` | `/v1/media/capabilities` | covered | Capability load integrated. |
| health | `HealthResource` | `/v1/health`, `/v1/health/ready` | covered | Version/health checks integrated. |
| crypto/e2ee | `CryptoResource` | `/v1/e2ee/*` | partial | Key package and decrypt helpers present; parity checklist needed for all key-package lifecycle endpoints. |
| metrics | `PrometheusMetricsResource` | `/v1/metrics/prometheus` | missing (out of scope) | Not a user flow target for web-client parity. |

## Explicit Exclusions (frozen)

- `AdminResource`
- `AdminUiResource`
- `AdminConsoleRedirectResource`

These are excluded from this feature scope unless separately requested.

## Priority Gaps for Next Phases

1. Message/chat endpoint-by-endpoint parity sweep (`partial` domains).
2. File/export operational parity sweep (`partial` domains).
3. Realtime/call convergence hardening (`partial` conference + message event races).
