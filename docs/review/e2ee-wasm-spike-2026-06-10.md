# E2EE WASM spike — 2026-06-10

## Decision

**Client-side MLS encrypt/decrypt** via **Web Crypto API** (AES-GCM + HKDF-SHA256), not a separate WASM binary.

Rationale per `docs/adr/ADR-e2ee-mls-library.md` phase 2 hybrid: browser hook `window.KorusMlsWasm` with server KMLS wire; full OpenMLS WASM deferred until ecosystem binding matures.

## Compatibility with server `MlsService`

| Step | Server | Browser (`korus-mls-wasm.js`) |
|------|--------|-------------------------------|
| Session key | HKDF(seed=`sessionId:chatId`, info=`mls-session-key`, 32 bytes) | Same via `crypto.subtle` HKDF |
| AAD | `chatId:epoch` UTF-8 | Same |
| Wire format | nonce (12) + AES-GCM ciphertext+tag → Base64 | Same |

Session metadata: `GET /api/v1/e2ee/mls/session/{chatId}` → `session_id`, `epoch`.

## Artifacts

- `modules/web-client/src/main/resources/webui/korus-mls-wasm.js`
- `modules/web-client/src/main/resources/webui/ui-e2ee-mls.js`
- `modules/core-api/.../CryptoResource.getMlsSession`

## Out of scope (phase 3+)

- OpenMLS / external MLS interop
- Bundled `.wasm` module (`KorusMlsWasm` name retained for API stability)

## Security gate

Prod `MLS_STATUS=active` requires checklist in `specs/004-deferred-phase2-closure/quickstart.md` § US7.
