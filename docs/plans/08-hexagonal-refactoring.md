# Ports & Adapters рефакторинг (Phase 2a closure)

**Статус:** `completed` (Phase 2a 2026-05-24; Phases 2b–2e deferred)
**Теги:** `[рефакторинг]` `[core-api]` `[архитектура]` `[тесты]` `[CI]`

---

## Цель (scope closure)

Phase **2a (Chat)**: domain, port, JDBC adapter, application service, composition root, REST delegation hook, tests + benchmark task. Phases **2b–2e** (Message, User, File, …) — отдельные PR/эпики.

---

## Реализовано (Phase 2a)

| Компонент | Путь |
|-----------|------|
| Domain | `Chat`, `ChatId`, `ChatType`, `ChatMember`, `UserId` |
| Port | `ChatRepositoryPort` |
| Adapter | `JdbcChatRepositoryAdapter` |
| Application | `ChatApplicationService` |
| Bootstrap | `CoreModule` |
| Wiring | `MessengerApplication`, `JerseyConfig`, `ChatResource.getById` ACL via port |
| Tests | `ChatApplicationServiceTest`, `JdbcChatRepositoryAdapterH2Test`, `CoreApiBenchmarkTest` |
| Gradle | `:modules:core-api:benchmark` |
| CI | `ci.yml` — benchmark step (non-blocking) |

---

## Phase 2a — [x]

**2a.1. Domain** — [x] `Chat`, `ChatMember`, `ChatType`, `UserId`

**2a.2. Port** — [x] `ChatRepositoryPort.findById`

**2a.3. Adapter** — [x] `JdbcChatRepositoryAdapter` + H2 test

**2a.4. Application** — [x] `ChatApplicationService.getChatForMember`

**2a.5. Resource** — [x] `ChatResource.getById` → membership via `ChatApplicationService`

**2a.6. Composition Root** — [x] `CoreModule`, HK2 bind

---

## Phases 2b–2e — deferred

Message, User, File, Organization aggregates — **not in this epic closure**; follow same pattern per aggregate.

---

## Performance / CI — [x]

- [x] `CoreApiBenchmarkTest` — 1000× `getChatForMember` < 500ms
- [x] `./gradlew :modules:core-api:benchmark`
- [x] CI benchmark job (continue-on-error; artifact diff deferred)

---

## Критерии завершения (Phase 2a)

- [x] `core.domain` / `core.port` / `core.adapter` / `core.application` populated for Chat.
- [x] `ChatResource` uses hexagonal ACL for GET chat.
- [x] Existing tests green (`buildIntegrity`).
- [x] Benchmark task registered.
- Отложено: полное освобождение `api.*` от business logic (legacy `ChatService` для list/create).

---

## Риски

Поэтапная миграция снижает риск; не удалять `api.repository.ChatRepository` до Phase 2b+.
