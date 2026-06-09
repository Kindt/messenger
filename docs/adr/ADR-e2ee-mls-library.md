# ADR: E2EE / MLS library strategy (RFC 9420)

**Status:** `accepted` (2026-06-09, phase 2 hybrid update)  
**Deciders:** Engineering (deferred backlog E8 spike)  
**Related:** `docs/E2EE_ARCHITECTURE.md`, `docs/plans/06-e2ee-mls.md`

## Context

Korus Messenger needs group E2EE aligned with RFC 9420 (MLS). The codebase already ships legacy
X25519 + AES-GCM (`E2EEService`), MLS scaffold tables (`mls_group_state`), and API types
(`e2ee-*`). A production MLS stack requires a library choice: full OpenMLS interop vs
incremental wire under our control.

## Decision

**Phase 1 (accepted):** Bouncy Castle–based incremental **KMLS wire codec** (`MlsWireCodec`)
with NATS fan-out (`mls.welcome`, `mls.commit`, `mls.epoch`), server-side group orchestration
(`MlsGroupManager`, `MlsMigrationService`), and legacy fallback via `e2ee_scheme=legacy`.

**Phase 2 (accepted — hybrid):**

| Layer | Choice | Rationale |
|-------|--------|-----------|
| **Server** | Java KMLS wire + `MlsService` epoch-aware encrypt/decrypt | Reuses BC stack; NATS consumer (`MlsWireSubscriber`) syncs multi-instance group state |
| **Browser** | WASM/JS MLS hook (`window.KorusMlsWasm`) with server-assisted fallback | Client encrypt when WASM bundled; until then `e2ee_scheme=mls` + server encrypt; `/plaintext-preview` disabled when `mls_status=active` |

**Deferred:** OpenMLS Java bindings until a stable, maintained binding exists in the ecosystem.
External MLS interop test suite deferred until OpenMLS or validated BC state machine replaces KMLS stub.

## Product sign-off gate

Before enabling production MLS (`MLS_STATUS=active` in prod inventory):

1. **T130 / ADR sign-off** — Product owner + security acknowledge hybrid model and plaintext-preview restriction.
2. **Security review** — checklist in `specs/004-deferred-phase2-closure/quickstart.md` § US7.
3. **Ops** — `GET /admin/e2ee/status` green; batch migration (`POST /admin/e2ee/migrate-batch`) run in staging.

Implementation tasks T140+ require T130 sign-off recorded in deployment/governance log.

## Alternatives considered

| Option | Pros | Cons | Outcome |
|--------|------|------|---------|
| OpenMLS Java | Full RFC 9420 interop | No production-ready binding | **Deferred** |
| BC incremental wire | Reuses existing BC stack; controlled rollout | Not interoperable with external MLS clients yet | **Phase 1–2 server** |
| Full client-only MLS | True E2EE | WASM maturity + key recovery | **Phase 2 hybrid** (hook + fallback) |
| Legacy only | Stable today | No group MLS semantics | **Fallback** (`e2ee_scheme=legacy`) |

## Consequences

- **Positive:** Wire envelope, migration API, capabilities (`mls_status=active`), admin metrics,
  NATS consumer, epoch rotation on membership, web send path (`e2ee_scheme=mls`), client MLS hooks ship.
- **Negative:** External MLS clients cannot join until OpenMLS replaces KMLS stub; security review required before prod MLS rollout.
- **Interop:** Self-interop via KMLS + NATS; legacy clients unchanged.

## Verification

- `MlsWireCodecTest`, `MlsGroupManagerTest`, `MlsMigrationServiceTest`, `MlsWireHandlerTest`, `MlsBenchmarkTest` green.
- `buildIntegrity` green.
- `GET /admin/e2ee/status` reports real group/pending counts.
- Capabilities expose `mls` + `legacy` schemes.
- Playwright `e2ee-capabilities.spec.ts` covers MLS flow gates.

## Follow-up (out of scope for phase 2)

1. OpenMLS spike re-evaluation when Java binding stabilizes.
2. Bundle `KorusMlsWasm` in web-client (replace server-assisted encrypt fallback).
3. External MLS interop test suite.
