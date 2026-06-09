# Parity Matrix Baseline (T001/T002)

Baseline inventory of non-admin server resources against current `modules/web-client/src/main/resources/webui/app.js` flows.

**Generated**: 2026-05-23  
**Scope**: non-admin user endpoints only  
**Legend**: `covered` / `partial` / `missing`

## Resource Coverage

| Domain | Server Resource | Endpoint Family | Status | Notes |
|---|---|---|---|---|
| auth | `AuthResource` | `/v1/auth/login|register|refresh|logout` | covered | Full auth + refresh session flow present in web-client. |
| chats | `ChatResource` | `/v1/chats/*` | covered | Read receipts REST hydrate + `up_to_message_id` on `/read`; member/ban flows wired. |
| messages | `MessageResource` | `/v1/chats/{chatId}/messages/*` | covered | All REST paths wired; static asserts for `/versions`, `/plaintext-preview`. |
| files | `FileResource` | `/v1/files/*` | covered | Metadata GET, kind-B auth-link URL, upload/download/public links. |
| export | `ExportResource` | `/v1/chats/{chatId}/export/*` | covered | Request/status/cancel/download + `/attachments` preview. |
| contacts | `ContactResource` | `/v1/contacts/*` | covered | Contact list/import/delete flows present. |
| search | `SearchResource` | `/v1/search/users|messages` | covered | Sidebar/global search flows present. |
| users | `UserResource` | `/v1/users/me*` | covered | Profile/presence/heartbeat/saved-chat flows present. |
| blocks | `BlocksResource` | `/v1/blocks/*` | covered | Settings block list/add/remove wired. |
| devices | `DeviceResource` | `/v1/me/devices/*` | covered | Device and push registration/removal mapped. |
| conference | `ConferenceResource` | `/v1/chats/{chatId}/conferences*`, `/v1/conferences/*` | covered | Standalone + in-chat create, by-room lookup, join/leave/end. |
| media | `MediaCapabilitiesResource` | `/v1/media/capabilities` | covered | Capability load integrated. |
| health | `HealthResource` | `/v1/health`, `/v1/health/ready` | covered | Version/health checks integrated. |
| crypto/e2ee | `CryptoResource` | `/v1/e2ee/*` | covered | Key package lifecycle REST wired; decrypt via plaintext-preview. |
| metrics | `PrometheusMetricsResource` | `/v1/metrics/prometheus` | missing (out of scope) | Not a user flow target for web-client parity. |

## Explicit Exclusions (frozen)

- `AdminResource`
- `AdminUiResource`
- `AdminConsoleRedirectResource`

These are excluded from this feature scope unless separately requested.

## Priority Gaps for Next Phases

None blocking spec 002 engineering closure (2026-06-09). Optional: browser operator sign-off per `HANDOFF.md`.
