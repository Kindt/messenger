# E2EE / MLS architecture (engineering baseline)

**Status:** Phase 1 — Bouncy Castle incremental RFC 9420 wire codec (OpenMLS Java deferred — no mature binding).  
**ADR:** `docs/adr/ADR-e2ee-mls-library.md` (`accepted` 2026-06-09).

## Current stack

| Layer | Implementation |
|-------|----------------|
| Legacy E2EE | `E2EEService` — X25519 + AES-GCM |
| MLS wire (phase 1) | `MlsWireCodec` — KMLS structured bytes (Welcome/Commit/Epoch); not full OpenMLS |
| MLS group | `MlsGroupManager` + `MlsWirePublisher` → NATS `mls.*` |
| Crypto stub | `MlsService` — per-chat session row, symmetric key derived from session id |
| Key packages | `KeyPackageRepository` + `CryptoResource` REST |
| Storage | `e2ee_sessions`, `e2ee_key_packages`, `mls_group_state` (V028) |
| Migration | `MlsMigrationService.migrateToMls(chatId)` for legacy chats |

## Library decision matrix

| Option | Maturity | Interop | Verdict |
|--------|----------|---------|---------|
| **OpenMLS Java** | No production-ready binding in ecosystem | Full RFC 9420 with other MLS clients | **Deferred** — revisit when binding stabilizes |
| **Bouncy Castle incremental wire** | BC already in stack; KMLS codec under our control | Self-interop + legacy fallback | **Phase 1 (current)** |
| **Legacy X25519 only** | Stable | Pre-MLS clients | **Fallback** via `e2ee_scheme=legacy` |

## Interop model

1. **Self-interop:** KMLS wire envelopes (`MlsWireCodec`) on NATS `mls.welcome`, `mls.commit`, `mls.epoch`.
2. **Legacy fallback:** Clients without MLS advertise `e2ee_scheme=legacy`; capabilities expose both `legacy` and `mls`.
3. **Message types:** `e2ee-mls-welcome`, `e2ee-mls-commit` (constants in `MlsMessageTypes`).
4. **Send path:** `e2ee_scheme=mls` on `POST .../messages` triggers migration + server encrypt.

## Target (full RFC 9420)

1. Replace KMLS stub with OpenMLS or validated BC MLS state machine when library decision closes.
2. Client-side MLS encrypt/decrypt (web + mobile).
3. External MLS client interop testing.

## Migration path

- New chats: MLS group created on first `e2ee_scheme=mls` send or explicit `migrateToMls()`.
- Existing chats: legacy decrypt remains; pending count in `GET /admin/e2ee/status`.

## Admin / ops

- `GET /admin/e2ee/status` — group count, pending migrations, `mls_status`, `e2ee_schemes`.
- `MLS_STATUS` env (default `active` when wire enabled, else `stub`).
- Benchmark gate: encrypt p50 &lt; 50ms for N≤100 members.

See `docs/plans/06-e2ee-mls.md` for epic checklist.
