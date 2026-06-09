# Ports & Adapters рефакторинг (Phase 2a–2e)

**Статус:** `completed` (Phase 2a 2026-05-24; Phase 2b message read 2026-06-09; Phases 2c–2e user/file/org read 2026-06-09)
**Теги:** `[рефакторинг]` `[core-api]` `[архитектура]` `[тесты]` `[CI]`

---

## Цель (scope closure)

Phase **2a (Chat)**: domain, port, JDBC adapter, application service, composition root, REST delegation hook, tests + benchmark task. Phases **2b–2e** (Message, User, File, Organization read paths) — same pattern per aggregate.

---

## Реализовано

| Phase | Aggregate | Domain | Port | Adapter | Application | REST hook |
|-------|-----------|--------|------|---------|-------------|-----------|
| 2a | Chat | `Chat`, `ChatId`, … | `ChatRepositoryPort` | `JdbcChatRepositoryAdapter` | `ChatApplicationService` | `ChatResource.getById` |
| 2b | Message | `Message`, `MessageId` | `MessageRepositoryPort` | `JdbcMessageRepositoryAdapter` | `MessageApplicationService` | `MessageResource.get` |
| 2c | User | `UserProfile` | `UserRepositoryPort` | `JdbcUserRepositoryAdapter` | `UserApplicationService` | `UserResource.me`, `getById` |
| 2d | File | `StoredFile`, `FileId` | `FileMetadataPort` | `JdbcFileMetadataAdapter` | `FileApplicationService` | `FileResource.getInfo` |
| 2e | Organization | `Organization`, `OrganizationId` | `OrganizationRepositoryPort` | `JdbcOrganizationRepositoryAdapter` | `OrganizationApplicationService` | `AdminResource` GET org retention |

**Bootstrap:** `CoreModule`, `MessengerApplication`, `JerseyConfig` (HK2 bind).

**Tests:** application service tests, JDBC adapter H2 tests per aggregate; `CoreApiBenchmarkTest` — chat + user read benchmarks.

**Gradle / CI:** `:modules:core-api:benchmark`; CI benchmark job (non-blocking).

---

## Phase 2a — [x]

**2a.1. Domain** — [x] `Chat`, `ChatMember`, `ChatType`, `UserId`

**2a.2. Port** — [x] `ChatRepositoryPort.findById`

**2a.3. Adapter** — [x] `JdbcChatRepositoryAdapter` + H2 test

**2a.4. Application** — [x] `ChatApplicationService.getChatForMember`

**2a.5. Resource** — [x] `ChatResource.getById` → membership via `ChatApplicationService`

**2a.6. Composition Root** — [x] `CoreModule`, HK2 bind

---

## Phase 2b — [x] Message read

`MessageApplicationService`, `JdbcMessageRepositoryAdapter`, `MessageResource.get` delegation.

---

## Phase 2c — [x] User read

`UserApplicationService.getProfileForViewer` (public profile for others; full profile for self; hidden users invisible to others).

---

## Phase 2d — [x] File metadata read

`FileApplicationService.getMetadataForUser` (owner or shared non-E2EE message access via legacy `MessageRepository`).

---

## Phase 2e — [x] Organization read

`OrganizationApplicationService.exists` / `findById`; `AdminResource` GET `organizations/{orgId}/retention` org check.

---

## Performance / CI — [x]

- [x] `CoreApiBenchmarkTest` — 1000× `getChatForMember` < 500ms
- [x] `CoreApiBenchmarkTest` — 1000× `getProfileForViewer` < 500ms
- [x] `./gradlew :modules:core-api:benchmark`
- [x] CI benchmark job (continue-on-error; artifact diff deferred)

---

## Критерии завершения (Phases 2a–2e read paths)

- [x] `core.domain` / `core.port` / `core.adapter` / `core.application` populated for Chat, Message, User, File, Organization reads.
- [x] Listed REST resources use hexagonal ACL for GET read paths.
- [x] Existing tests green (`buildIntegrity`).
- [x] Benchmark task registered.
- Отложено: полное освобождение `api.*` от business logic (legacy repositories/services for write/list paths).

---

## Риски

Поэтапная миграция снижает риск; не удалять `api.repository.*` до завершения write-path migration.
