# E2EE/MLS — RFC 9420 phase 1 (wire codec)

**Статус:** Phase 1 `completed` (2026-06-09); Phase 2 hybrid `completed` (engineering, 2026-06-12) — prod `MLS_STATUS=active` blocked on security gate in `specs/004-deferred-phase2-closure/quickstart.md` § US7
**Теги:** `[e2ee]` `[core-api]` `[криптография]` `[web-client]` `[безопасность]`

---

## Цель (phase 1)

Инкрементальный RFC 9420 wire-контур: KMLS codec (BC), NATS `mls.*`, migration API, capabilities/admin, web `e2ee_scheme=mls`. Полный OpenMLS — deferred.

---

## Реализовано (phase 0 + phase 1)

| Область | Артефакты |
|---------|-----------|
| Архитектура | `docs/E2EE_ARCHITECTURE.md` — decision matrix, interop |
| Wire codec | `MlsWireCodec`, `MlsWelcomePayload`, `MlsCommitPayload`, `MlsEpochPayload` |
| NATS | `NatsSubjects.MLS_WELCOME/COMMIT/EPOCH`, `MlsWirePublisher` |
| Group layer | `MlsGroupManager` — welcome on create, commit/epoch on membership |
| Migration | `MlsMigrationService.migrateToMls`, pending count in admin |
| Send path | `SendMessageRequest.e2ee_scheme`, `MlsMessageTypes` |
| Capabilities | `MediaCapabilitiesResource` — `mls_status` from `MLS_STATUS` |
| Admin | `GET /admin/e2ee/status` — real counts |
| Web | `app.js` — `e2ee_scheme=mls` when `mls_status=active` |
| Тесты | `MlsWireCodecTest`, `MlsGroupManagerTest`, `MlsMigrationServiceTest`, `MlsBenchmarkTest` |

---

## Шаги (статус)

### 1. Архитектурное решение — [x]
- [x] `docs/E2EE_ARCHITECTURE.md` — BC incremental wire; OpenMLS deferred
- [x] Decision matrix + self/legacy interop

### 2. Дерево ключей (scaffold) — [x]
- [x] `MlsGroupManager`, `MlsGroupStateRepository`, `V028__mls_group_state.sql`

### 3. Welcome / Commit wire — [x] phase 1
- [x] `MlsWireCodec` (KMLS structured bytes)
- [x] NATS `mls.welcome`, `mls.commit`, `mls.epoch`
- [x] `MlsWirePublisher` from `MlsGroupManager`

### 4. Wire-протокол — [x] partial
- [x] `e2ee_scheme=mls` on send
- [x] Message types `e2ee-mls-welcome`, `e2ee-mls-commit` constants
- [x] Web-client advertises `e2ee_scheme=mls` when active
- Отложено: client-side MLS encrypt (full OpenMLS)

### 5–6. Миграция — [x] partial
- [x] `MlsMigrationService.migrateToMls(chatId)`
- [x] Admin pending migrations count
- Отложено: automatic batch migration job

### 7. Admin UI — [x]
- [x] `core-e2ee-mls` → `/admin/e2ee/status` with real metrics

### 8. Benchmark — [x]
- [x] `MlsBenchmarkTest` — avg budget + p50 &lt; 50ms

---

## Критерии phase 1

- [x] Wire codec round-trip tests pass.
- [x] NATS subjects documented in `NATS_SUBJECTS_INTEROP.md`.
- [x] Legacy E2EE continues (`e2ee_scheme=legacy`).
- [x] Capabilities/admin reflect `MLS_STATUS` / wire flag.
- Отложено: Full OpenMLS interop with external clients.

---

## Риски

Полный MLS остаётся высокорисковым; phase 1 — controlled wire + migration boundary before OpenMLS adoption.

---

## Phase 2 — hybrid MLS (2026-06-09)

| Область | Артефакты |
|---------|-----------|
| ADR | Hybrid: server Java KMLS + browser WASM hook; product sign-off gate |
| Server crypto | `MlsService.syncEpoch`, epoch-aware encrypt/decrypt |
| NATS consumer | `MlsWireSubscriber` + `MlsWireHandler` on `mls.welcome/commit/epoch` |
| Membership | `MlsGroupManager.bumpEpoch` → session epoch rotation + wire publish |
| Migration batch | `MlsMigrationService.batchMigrateToMls`, `POST /admin/e2ee/migrate-batch` |
| Web client | `app.js` — `mlsClientEncrypt/Decrypt`, key package upload, no `/plaintext-preview` when MLS active |
| Security gate | `specs/004-deferred-phase2-closure/quickstart.md` § US7 |
| E2E | `tests/e2e-web/specs/e2ee-capabilities.spec.ts` — browser MLS flow |

### Шаги phase 2

- [x] T130 ADR hybrid + sign-off gate
- [x] T140 `MlsService` real encrypt/decrypt + epoch sync
- [x] T141 NATS consumer `MlsWireSubscriber`
- [x] T142 Membership epoch rotation (add/remove)
- [x] T150–T151 Client MLS hook + key package generation in `app.js`
- [x] T160–T161 Client encrypt send path; restrict plaintext-preview
- [x] T165 Batch migration job
- [x] T169 Security review gate in quickstart
- [x] T170 Playwright e2ee-capabilities extension

### Отложено (phase 3)

- Bundled `KorusMlsWasm` (full client-side encrypt/decrypt without server assist)
- OpenMLS Java binding
- External MLS interop suite

**Cross-ref**: spec 004 US7 (T130–T169) — full OpenMLS client encrypt, batch migration, Playwright browser MLS (`e2ee-capabilities.spec.ts` T170). ADR: `docs/adr/ADR-e2ee-mls-library.md`.
