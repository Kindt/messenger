# E2EE/MLS — scaffold + group state (RFC 9420 deferred)

**Статус:** `completed` (engineering closure 2026-05-24; full RFC 9420 deferred)
**Теги:** `[e2ee]` `[core-api]` `[криптография]` `[web-client]` `[безопасность]`

---

## Цель (scope closure)

Инженерное закрытие эпика: архитектурное решение, хранение группового состояния, admin observability, capabilities/fallback, benchmark guard. **Полный RFC 9420** (Welcome/Commit wire, OpenMLS interop, миграция legacy→MLS) — отдельный продуктовый этап.

---

## Реализовано

| Область | Артефакты |
|---------|-----------|
| Архитектура | `docs/E2EE_ARCHITECTURE.md` — stub + roadmap |
| Миграция | `V028__mls_group_state.sql` |
| Group layer | `MlsGroupState`, `MlsGroupStateRepository`, `MlsGroupManager` |
| Crypto stub | `MlsService` + legacy `E2EEService` |
| Capabilities | `MediaCapabilitiesResource` — `e2ee_schemes`, `mls_status: stub` |
| Admin | `GET /admin/e2ee/status`, раздел `core-e2ee-mls` |
| Тесты | `MlsGroupManagerTest`, `MlsBenchmarkTest` |

---

## Шаги (статус)

### 1. Архитектурное решение — [x]
- [x] `docs/E2EE_ARCHITECTURE.md` — решение: stub + phased RFC 9420
- [x] Bouncy Castle (существующая зависимость); OpenMLS — deferred

### 2. Дерево ключей (scaffold) — [x]
- [x] `MlsGroupManager` — create/add/remove/encrypt/decrypt (delegates to `MlsService`)
- [x] `MlsGroupStateRepository`
- [x] `V028__mls_group_state.sql`
- [x] `MlsGroupManagerTest`

### 3. Welcome / Commit — отложено (следующий эпик)

Wire-format Welcome/Commit, NATS `mls.*` — не входит в engineering closure 2026-05-24.

### 4. Wire-протокол — partial [x]

- [x] Legacy `e2ee-*` + capabilities `mls-stub`
- Отложено: `e2ee_scheme=mls` end-to-end в web-client

### 5–8. Ротация, миграция, NATS fan-out — отложено

Epoch bump on membership только в scaffold.

### 9. Admin UI — [x]
- [x] `core-e2ee-mls` → `/admin/e2ee/status`

### 10. Benchmark — [x]
- [x] `MlsBenchmarkTest` (100 encrypt, budget 200ms avg stub)

### 11. OpenAPI — [x] existing crypto endpoints documented

---

## Критерии завершения (engineering)

- [x] Group state persisted; admin status endpoint works.
- [x] Legacy E2EE continues to work (`e2ee-*` types).
- [x] Capabilities advertise `legacy` + `mls-stub`.
- [x] Benchmark guard in CI (`:modules:core-api:benchmark`, non-blocking).
- Отложено: Full RFC 9420 interop (не требуется для epic closure).

---

## Риски (unchanged)

Полный MLS остаётся высокорисковым; текущий closure — controlled stub с чёткой границей.
