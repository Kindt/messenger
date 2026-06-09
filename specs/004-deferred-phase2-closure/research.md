# Research: Deferred Phase 2 Post-Backlog Closure

**Date**: 2026-06-09

## R1 — E2EE library strategy (US7)

**Decision**: Hybrid model — server-side MLS state machine in Java (OpenMLS binding or incremental BC per spike); client-side encrypt via WASM MLS library in browser.

**Rationale**: Phase 1 already uses BC wire codec (KMLS) server-side; true E2EE requires client keys never leaving browser. WASM avoids shipping JDK to clients.

**Alternatives considered**:
- Server-only MLS (rejected — not true E2EE per FR-008)
- WASM-only both tiers (rejected — server must validate Welcome/Commit and epoch)
- Full OpenMLS Java only (deferred — spike T130 confirms maturity)

## R2 — TLS certificate lifecycle (US1)

**Decision**: Stage uses Let's Encrypt via certbot in `tls` role; production inventory documents BYO-cert path with optional certbot.

**Rationale**: Stage needs automated renewal for CI-like validation; prod enterprises often supply certs.

**Alternatives considered**:
- Caddy auto-TLS only (rejected — nginx already in role `tls`)
- Manual cert copy only (rejected — poor stage DX)

## R3 — Hex saved-chat dependency (US3)

**Decision**: Implement `SavedChatPort` in US3 after US2 User write; may reuse `ChatRepositoryPort` read if available.

**Rationale**: Original E4 scope excluded saved-chat from first User PR to avoid Chat port coupling.

**Alternatives considered**:
- Fold into US2 C1 (rejected — expands PR scope)
- Leave legacy indefinitely (rejected — blocks C7 cleanup)

## R4 — QEMU vs Playwright gate (US8/US5)

**Decision**: US8 optional; US5 documents QEMU as recommended full-stack environment; API-heavy specs run without QEMU.

**Rationale**: Playwright fixtures already support API setup; DOM gates need stable web upstream.

## R5 — Profiling overlay pattern (US4)

**Decision**: Extend existing `docker-compose.profiling.yml` pattern from E2 (JDK tag swap only).

**Rationale**: Proven for core-api, retention, indexer; zero prod image change.
