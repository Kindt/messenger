# Feature Specification: Web Client Server Parity

**Feature Branch**: `002-web-client-server-parity`

**Created**: 2026-05-23

**Status**: Draft

**Input**: User description: "с помощю Spec-kit разработать план полной доработки веб клиента до текущего состояния сервера"

## User Scenarios & Testing

### User Story 1 - Message and chat parity in main UI (Priority: P1)

As an end user, I want all core chat/message flows exposed in web-client with behavior parity to current server endpoints, so that I can use the browser client as the primary daily client without feature gaps.

**Why this priority**: Messaging is the product core. Without complete parity for message/chat flows, other improvements do not deliver production value.

**Independent Test**: In a web-only flow, a user can create chats, manage members, send/edit/delete/forward/pin/react to messages, and see correct unread/read state.

**Acceptance Scenarios**:

1. **Given** an authenticated user in web-client, **When** they perform chat/member operations supported by `/v1/chats/*`, **Then** UI behavior and API mapping remain contract-consistent.
2. **Given** a message timeline in a chat, **When** the user edits/deletes/replies/forwards/pins/reacts, **Then** the timeline, preview, and pinned/reaction states remain consistent with backend state.
3. **Given** unread state and typing/read events, **When** WebSocket events arrive, **Then** counters and thread status update without manual refresh.

---

### User Story 2 - Files, links, export, and compliance user flows (Priority: P1)

As an end user, I want all file and export-related server capabilities available in web-client, so that attachment lifecycle and data export can be completed end-to-end from the browser.

**Why this priority**: File handling and export are high-value operational features already available on server APIs.

**Independent Test**: A user can upload/download files, manage public links, and run chat export lifecycle (create job, inspect status, list attachments, download artifacts).

**Acceptance Scenarios**:

1. **Given** a file upload/download flow, **When** a user shares and retrieves files, **Then** all `/v1/files/*` user endpoints work from web-client with correct error handling.
2. **Given** a public link created for a file, **When** it is revoked, **Then** UI reflects revocation and server state is updated.
3. **Given** a chat export request, **When** the job progresses, **Then** web-client shows status, attachments list, and download options aligned with `/v1/chats/{chatId}/export/*`.

---

### User Story 3 - Realtime, calls, and push/PWA reliability parity (Priority: P2)

As an end user, I want realtime messaging, conference/call signals, and push/PWA behavior to match server capabilities and remain stable after reconnects and updates.

**Why this priority**: Realtime and call reliability is critical for production use, but can follow once baseline feature parity is complete.

**Independent Test**: After reconnects/reloads, WS delivery, conference state, call controls, and push notifications continue working without losing context.

**Acceptance Scenarios**:

1. **Given** temporary network loss, **When** connection restores, **Then** WS reconnect, heartbeat, and thread refresh recover state without data loss.
2. **Given** an active conference/call context, **When** participants join/leave or signals arrive, **Then** call panel and RTC state stay consistent with server events.
3. **Given** service worker/web-push enabled environment, **When** push/update events occur, **Then** notification and update flows are reliable and controllable from settings.

---

### User Story 4 - Security, boundary, and maintainability hardening (Priority: P3)

As a maintainer, I want web-client boundary code (servlets, env bridge, proxy) and UI modules to be maintainable and auditable, so that future parity delivery is predictable and low-risk.

**Why this priority**: Long-term sustainability and safer change velocity after parity baseline is achieved.

**Independent Test**: Boundary tests pass, structure is modularized by responsibility, and no public HTTP/runtime contracts regress.

**Acceptance Scenarios**:

1. **Given** proxy/env/bootstrap code changes, **When** test suite runs, **Then** servlet contract tests pass unchanged.
2. **Given** modularized UI helpers, **When** features evolve, **Then** changes are localized to one subsystem without cross-cutting regressions.

---

### Edge Cases

- How should web-client behave when server supports a message type not yet rendered by UI?
- What is the fallback path when WS reconnect succeeds but thread hydration endpoint temporarily fails?
- How should UI handle partial export artifacts (job status ready but attachment manifest missing/corrupt)?
- What if service worker is disabled via env while old SW is already installed on a client?
- How should UI resolve concurrent updates from local optimistic patch + WS event + full reload?

## Requirements

### Functional Requirements

- **FR-001**: Web-client MUST support all non-admin end-user chat/message operations currently exposed by server chat/message APIs.
- **FR-002**: Web-client MUST support full user file lifecycle: upload, message reference, download, public-link create/list/revoke, and personal public-link listing.
- **FR-003**: Web-client MUST support user export lifecycle for chat exports, including request, status, attachments listing, and artifact download.
- **FR-004**: Web-client MUST maintain consistent unread/read/typing/reaction/pin state under WS realtime updates and reconnect.
- **FR-005**: Web-client MUST preserve session continuity using refresh-token retry semantics for protected API requests.
- **FR-006**: Web-client MUST expose stable call/conference behavior aligned with current server conference and rtc signal contracts.
- **FR-007**: PWA/web-push behavior MUST remain configurable from server env script (`/web-client-env.js`) and UI settings.
- **FR-008**: Servlet boundary (`WebClientApplication`, `UpstreamProxyServlet`, `WebClientEnvServlet`) MUST remain backward-compatible for route mapping and env output fields.
- **FR-009**: Implementation MUST be delivered as incremental Spec-Kit tasks with explicit test gates and rollback notes.

### Key Entities

- **ParityCapability**: A server-backed capability mapped to web-client flow, with fields for endpoint coverage, UI entry points, and test status.
- **ParityGap**: A capability mismatch record (`missing`, `partial`, `unstable`) with severity and planned PR phase.
- **ClientModuleBoundary**: UI or servlet subsystem boundary with explicit responsibility and non-goals.
- **ScenarioGate**: A required verification checkpoint (unit/integration/manual smoke) attached to a capability group.

## Success Criteria

### Measurable Outcomes

- **SC-001**: 100% of scoped non-admin user endpoints in current server API map to documented and testable web-client flows.
- **SC-002**: Web-client module tests pass on every parity phase (`:modules:web-client:test` green).
- **SC-003**: `buildIntegrity` remains green after parity completion.
- **SC-004**: At least one end-to-end scenario per parity domain (messaging, files/export, realtime/calls, pwa/settings) is documented and validated.
- **SC-005**: Web-client parity plan status is marked completed with all phase trackers resolved.

## Assumptions

- Admin-only APIs remain out of scope for this parity initiative unless explicitly requested.
- Server contracts in `core-api` represent source of truth for parity.
- Existing web stack scripts and smoke scripts are reused; no new browser automation framework is mandatory for this iteration.
- Progressive modularization of `webui/app.js` continues instead of framework rewrite.
- Parity scope is frozen against baseline matrix `specs/002-web-client-server-parity/parity-matrix.md` (snapshot date: 2026-05-23).
