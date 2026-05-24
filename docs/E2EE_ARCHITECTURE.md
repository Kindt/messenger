# E2EE / MLS architecture (engineering baseline)

**Status:** Phase 0 — stub MLS in production; full RFC 9420 deferred.

## Current stack

| Layer | Implementation |
|-------|----------------|
| Legacy E2EE | `E2EEService` — X25519 + AES-GCM |
| MLS stub | `MlsService` — per-chat session row, symmetric key derived from session id (not RFC 9420) |
| Key packages | `KeyPackageRepository` + `CryptoResource` REST |
| Storage | `e2ee_sessions`, `e2ee_key_packages`, `mls_group_state` (V028 scaffold) |

## Target (RFC 9420)

1. **Library decision:** evaluate OpenMLS Java vs incremental Bouncy Castle wire format (TBD — product sign-off).
2. **Group lifecycle:** Welcome / Commit / epoch rotation via `MlsGroupManager`.
3. **Wire:** `e2ee_scheme=mls` on send; message types `e2ee-mls-welcome`, `e2ee-mls-commit`.
4. **Fallback:** `e2ee_scheme=legacy` for older clients; advertised in `GET /v1/media/capabilities`.

## Migration path

- New chats: optional MLS group creation on first E2EE message.
- Existing chats: legacy decrypt remains; `migrateToMls()` on first MLS-capable send (not implemented).

## Admin / ops

- `GET /admin/e2ee/status` — planned (group count, pending migrations).
- Benchmark gate: encrypt p50 &lt; 50ms for N≤100 members.

See `docs/plans/06-e2ee-mls.md` for full epic checklist.
