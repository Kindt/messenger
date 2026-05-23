# Tasks: Web Client Server Parity

**Input**: Design documents from `specs/002-web-client-server-parity/`

**Prerequisites**: plan.md, spec.md

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (`US1`..`US4`)
- Include exact file paths in descriptions

---

## Phase 1: Setup (Parity Baseline)

**Purpose**: Build explicit scope and gap baseline before implementation.

- [x] T001 Build non-admin endpoint inventory from `modules/core-api/src/main/java/com/avandocmsg/messenger/api/**/*Resource.java` and record parity mapping in `specs/002-web-client-server-parity/plan.md` appendix or companion markdown.
- [x] T002 [P] Classify each endpoint capability as `covered` / `partial` / `missing` for `modules/web-client/src/main/resources/webui/app.js`.
- [x] T003 Freeze parity scope and exclusions (admin-only) in `specs/002-web-client-server-parity/spec.md` assumptions.

**Checkpoint**: Parity matrix accepted as execution baseline.

---

## Phase 2: User Story 1 — Message and chat parity in main UI (P1)

**Goal**: Full parity of core chat/message flows for browser-first usage.

**Independent Test**: In web-client UI, user can create/manage chats and complete message lifecycle without unsupported action paths.

### Implementation

- [x] T004 [US1] Complete chat lifecycle coverage in `modules/web-client/src/main/resources/webui/app.js` for `/v1/chats/*` operations (create/update/delete/members/role/mute/filter/read/unread/typing).
- [x] T005 [P] [US1] Complete message lifecycle coverage in `modules/web-client/src/main/resources/webui/app.js` for `/v1/chats/{chatId}/messages/*` operations (send/edit/delete/versions/reactions/pin/forward/plaintext-preview where applicable).
- [x] T006 [P] [US1] Extend timeline helpers in `modules/web-client/src/main/resources/webui/ui-messages-utils.js` for deterministic merge/patch/reaction/pin/thread behavior.
- [x] T007 [US1] Wire remaining message/timeline delegates from `app.js` to `ui-messages-utils.js` with fallback preservation.
- [x] T008 [US1] Ensure chat preview/unread/read indicators stay consistent under WS events in `modules/web-client/src/main/resources/webui/app.js`.

### Validation

- [x] T009 [US1] Run `./gradlew.bat :modules:web-client:test`.
- [ ] T010 [US1] Execute messaging parity smoke checklist on running stack (manual): create chat, add/remove member, send/edit/delete/reply/reaction/pin/forward.

**Checkpoint**: Core messaging/chat parity complete.

---

## Phase 3: User Story 2 — Files, links, export, and compliance user flows (P1)

**Goal**: End-to-end file and export parity in browser UI.

**Independent Test**: User can complete file public-link and chat export lifecycle from web-client.

### Implementation

- [x] T011 [US2] Audit and complete `/v1/files/*` user flow coverage in `modules/web-client/src/main/resources/webui/app.js` (upload/download/link list/create/revoke/public auth-link navigation).
- [x] T012 [P] [US2] Improve file/public-link UI state handling in `modules/web-client/src/main/resources/webui/ui-messages-utils.js` or dedicated file helper module if needed.
- [x] T013 [US2] Audit and complete `/v1/chats/{chatId}/export/*` coverage in `modules/web-client/src/main/resources/webui/app.js` (request/status/attachments/download/cancel where available).
- [x] T014 [P] [US2] Normalize file/export error mapping and status labels in `app.js` for parity with server response semantics.

### Validation

- [x] T015 [US2] Run `./gradlew.bat :modules:web-client:test`.
- [ ] T016 [US2] Execute file+export smoke checklist on running stack (manual): upload/download file, create/revoke public link, request export, inspect status and artifact download.

**Checkpoint**: File and export parity complete.

---

## Phase 4: User Story 3 — Realtime, calls, and push/PWA reliability parity (P2)

**Goal**: Stable realtime and call behavior under reconnect/update conditions.

**Independent Test**: WS reconnect and RTC/call state recover without stale UI or broken controls.

### Implementation

- [x] T017 [US3] Extend RTC helper boundaries in `modules/web-client/src/main/resources/webui/ui-rtc-utils.js` (signal helpers, peer lifecycle helpers, hangup/cleanup helpers).
- [x] T018 [US3] Delegate remaining rtc/call flow segments in `modules/web-client/src/main/resources/webui/app.js` to `ui-rtc-utils.js` while keeping wire format unchanged.
- [x] T019 [P] [US3] Harden reconnect convergence logic in `app.js` (WS reconnect + thread refresh + unread updates + heartbeat).
- [x] T020 [P] [US3] Validate conference participant and active-conference sync behavior under event updates in `app.js`.

### Validation

- [x] T021 [US3] Run `./gradlew.bat :modules:web-client:test`.
- [ ] T022 [US3] Execute realtime/call checklist on running stack (manual): ws reconnect, incoming message sync, start/accept/hangup call, participant updates.

**Checkpoint**: Realtime and calls reliability parity complete.

---

## Phase 5: User Story 4 — Security, boundary, and maintainability hardening (P3)

**Goal**: Maintainable UI/servlet boundaries with stable contracts.

**Independent Test**: Boundary test suite passes; no route/env contract changes.

### Implementation

- [x] T023 [US4] Complete shell/settings extraction in `modules/web-client/src/main/resources/webui/ui-shell-utils.js` and delegate remaining storage/settings paths from `app.js`.
- [x] T024 [US4] Complete transport extraction in `modules/web-client/src/main/resources/webui/ui-transport-utils.js` and delegate remaining api/ws helpers from `app.js`.
- [x] T025 [US4] Complete pwa/settings extraction in `modules/web-client/src/main/resources/webui/ui-pwa-settings-utils.js` and delegate SW/push/update helpers from `app.js`.
- [x] T026 [P] [US4] Keep script load order explicit in `modules/web-client/src/main/resources/webui/index.html` for deterministic initialization.
- [x] T027 [US4] Refine servlet boundary helpers in:
  - `modules/web-client/src/main/java/com/avandocmsg/messenger/web/UpstreamProxyServlet.java`
  - `modules/web-client/src/main/java/com/avandocmsg/messenger/web/WebClientApplication.java`
  - `modules/web-client/src/main/java/com/avandocmsg/messenger/web/WebClientEnvServlet.java`
  with zero contract drift.

### Validation

- [x] T028 [US4] Run `./gradlew.bat :modules:web-client:test`.
- [x] T029 [US4] Verify servlet boundary tests still pass:
  - `ClasspathWebUiServletTest`
  - `OverlayWebUiServletTest`
  - `WebClientEnvServletTest`

**Checkpoint**: Maintainability hardening complete.

---

## Phase 6: Polish & Closure

- [x] T030 [P] Update parity status and completion notes in `docs/plans/10-web-client-code-health-backlog.md`.
- [x] T031 [P] Sync index status in `docs/plans/README.md`.
- [x] T032 Run `./gradlew.bat buildIntegrity`.
- [x] T033 Final parity report: list covered capabilities and explicit deferred items in `specs/002-web-client-server-parity/plan.md` or companion report.

---

## Dependencies & Execution Order

### Phase Dependencies

- Setup (Phase 1) is mandatory before implementation phases.
- US1 and US2 are both P1; US2 may start after matrix freeze but should follow core message/chat stabilization.
- US3 depends on stable US1 transport/event handling.
- US4 runs after major parity flows are stabilized (US1-US3).
- Polish phase depends on all previous phases.

### Parallel Opportunities

- T005 + T006 can run in parallel (message lifecycle + helper extraction).
- T011 + T013 can run in parallel (files and export audits).
- T019 + T020 can run in parallel (reconnect and conference sync hardening).
- T023 + T024 + T025 can run in parallel if each touches separate helper modules first.

### Recommended Autopilot Order

T001 → T003 → (T004..T010) → (T011..T016) → (T017..T022) → (T023..T029) → (T030..T033)
