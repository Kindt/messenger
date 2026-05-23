# Implementation Plan: Web Client Server Parity

**Branch**: `main` | **Date**: 2026-05-23 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/002-web-client-server-parity/spec.md`

## Summary

Deliver full web-client parity to the current non-admin server surface through a phased, contract-safe rollout: (1) capability inventory and parity matrix, (2) UI parity completion by domain (chat/message, files/export, realtime/calls, pwa/settings), (3) servlet and module boundary hardening, (4) final quality gates and documentation closure.

## Technical Context

**Language/Version**: JavaScript (browser ES2020 style), Java 25

**Primary Dependencies**: embedded Tomcat 11, Jakarta Servlet 6, native WebSocket API, Service Worker API, PushManager, existing backend REST/WS contracts

**Storage**: Browser local/session storage + IndexedDB (web-client); server side unchanged (PostgreSQL, MinIO, Redis, Solr)

**Testing**: JUnit 5 for servlet tests; Gradle module tests (`:modules:web-client:test`); manual/operational smoke scripts for runtime flows

**Target Platform**: Browser client served by `modules/web-client` with upstream `core-api` and `ws-gateway`

**Project Type**: Modular monolith module enhancement (no framework migration)

**Performance Goals**: Preserve current UX responsiveness; no regressions in WS reconnect behavior; no additional page-load blocking scripts beyond modular utility split

**Constraints**: Backward compatibility for `/`, `/health`, `/api/*`, `/web-client-env.js`; no change in backend endpoint contracts; incremental delivery only

**Scale/Scope**: One web module + three servlet boundary classes + cross-domain parity against all non-admin user resources

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

1. **Spec-first planning**: ✅ This plan is derived from `spec.md` and organized by independent user stories.
2. **Incremental delivery**: ✅ Work is sliced into independently shippable domain phases with explicit gates.
3. **Contract safety**: ✅ Route/env/output contract compatibility is mandatory across all phases.
4. **Testability and rollback**: ✅ Each phase defines minimal tests and rollback strategy.
5. **Operational readiness**: ✅ Includes reconnect/pwa/export/call scenario gates and final build integrity check.

## Project Structure

### Documentation (this feature)

```text
specs/002-web-client-server-parity/
├── checklists/requirements.md
├── contracts/web-client-parity-contract.md
├── data-model.md
├── parity-matrix.md
├── parity-report.md
├── quickstart.md
├── research.md
├── spec.md
├── plan.md
└── tasks.md
```

### Source Code (repository root)

```text
modules/web-client/
├── src/main/resources/webui/
│   ├── index.html
│   ├── app.js
│   ├── sw.js
│   └── ui-*.js (incremental utility modules)
└── src/main/java/com/avandocmsg/messenger/web/
    ├── WebClientApplication.java
    ├── UpstreamProxyServlet.java
    └── WebClientEnvServlet.java
```

**Structure Decision**: Keep existing architecture and continue modular extraction from `app.js` into focused utility scripts; servlet boundary remains in-place with readability/hardening refactors only.

## Phase Plan

### Phase 0 — Parity Inventory Baseline

- Build endpoint-to-UI parity matrix against non-admin resources in `core-api`.
- Classify each capability as `covered`, `partial`, or `missing`.
- Freeze parity scope for this iteration (admin APIs excluded).

**Baseline Artifact**: `specs/002-web-client-server-parity/parity-matrix.md`

### Phase 1 — Core Messaging and Chat Parity (P1)

- Ensure all message and chat operations are represented in web-client UI flow.
- Complete any remaining lifecycle gaps (edit/delete/forward/pin/reaction/thread/read/unread/typing/member controls).
- Stabilize preview/thread synchronization under WS event races.

### Phase 2 — File and Export Parity (P1)

- Ensure file upload/download/public-links flows fully align with server endpoints.
- Ensure user chat export lifecycle is complete in web-client (request/status/attachments/download/cancel where applicable).
- Normalize user-facing errors for file/export actions.

### Phase 3 — Realtime Calls and Presence Reliability (P2)

- Complete RTC/call/pariticipant lifecycle parity and reconnection safety.
- Improve event-driven convergence between optimistic UI patches and server events.
- Preserve wire formats and event contracts.

### Phase 4 — PWA, Notifications, and Settings Parity (P2)

- Consolidate service worker, push subscription, and update banner behavior under env-driven controls.
- Ensure settings panel actions map to stable implementation boundaries.
- Verify offline/update interactions do not break auth shell behavior.

### Phase 5 — Servlet Boundary Hardening (P3)

- Keep servlet contracts unchanged while improving boundary readability and maintainability.
- Clarify helper responsibilities for request build/header filtering/env mapping.
- Preserve compatibility for existing scripts and deployment assumptions.

### Phase 6 — Final Quality Gates and Closure

- Re-run web-client test suite and full integrity gate.
- Validate parity matrix completion and close remaining gaps or explicitly defer them.
- Update docs/plans references and final status markers.

## Risk Register

- **R1: Hidden parity gaps due to endpoint drift**  
  Mitigation: start from explicit parity matrix generated from `core-api` resources.

- **R2: Realtime race regressions in mixed optimistic/event updates**  
  Mitigation: isolate timeline helpers and enforce deterministic merge/patch helpers.

- **R3: SW/push environment variability across browsers**  
  Mitigation: keep env guards + graceful no-op paths + settings reset controls.

- **R4: Over-large refactor in single pass**  
  Mitigation: hard limit one responsibility per PR phase, with fallback retained.

## Validation Strategy

- **Mandatory per phase**: `./gradlew.bat :modules:web-client:test`
- **Mandatory final**: `./gradlew.bat buildIntegrity`
- **Runtime validation**: manual/smoke scenarios for messaging, file/export, rtc, pwa/settings on an available stack
- **Contract validation**: no public route/env field changes unless separately approved

## Rollout & Rollback

- Rollout is progressive by phase; each phase can ship independently.
- Rollback is file-scope revert for the phase utility module + delegation wiring.
- Servlet boundary rollback is full-file revert with no migration dependency.

## Closure Snapshot

- **Parity baseline**: `specs/002-web-client-server-parity/parity-matrix.md`
- **Final report**: `specs/002-web-client-server-parity/parity-report.md`
- **Final gate**: `./gradlew.bat buildIntegrity` (green, local run)
- **Deferred runtime checks**: `T010`, `T016`, `T022` (manual operator-run checks on available environment)
