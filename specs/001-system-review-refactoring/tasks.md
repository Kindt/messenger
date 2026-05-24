# Tasks: System Review, Refactoring & Optimization

**Input**: Design documents from `specs/001-system-review-refactoring/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to
- Include exact file paths in descriptions

## Phase 1: Setup

**Purpose**: Prepare profiling infrastructure and coordination

- [x] T001 Create `scripts/profiling/` directory with README for profiling workflow
- [x] T002 [P] Write `scripts/profiling/profile-core-api.ps1` — JFR recording for core-api
- [x] T003 [P] Write `scripts/profiling/profile-worker.ps1` — JFR recording for a named worker (deep-archiver, retention, indexer, export-replay)
- [x] T004 Write `scripts/profiling/analyze-hotspots.md` — guide for hotspot analysis with JMC

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure needed before any user story

- [x] T005 Implement Solr atomic update for empty `content_txt` in `IndexerWorker.handleUpdate()` — `modules/workers/indexer/src/main/java/.../indexer/IndexerWorker.java`
- [x] T006 [P] Write test `IndexerWorkerSolrUpdateTest` — mock SolrClient, verify `AtomicUpdate` with `set content_txt` on update
- [x] T007 Implement web-client TTL timer indicator in `web-client/app.js` — `renderMessage()` shows `⏱` + `formatTimeLeft()` for messages with `visibility_ttl_seconds`
- [x] T008 [P] Add CSS styles `.msg-ttl-indicator` and `.msg-ttl-expired` in `web-client/styles.css`
- [x] T009 [P] Add Prometheus metric `deep_archiver_chunk_writes_total` (Counter) in `DeepArchiverWorker.java`
- [x] T010 [P] Add Prometheus metric `deep_archiver_chunked_messages_total` (Counter) in `DeepArchiverWorker.java`
- [x] T011 [P] Add Prometheus metric `retention_worker_chunk_writes_total` (Counter) in `RetentionHotBodyJanitor.java`
- [x] T012 [P] Add Prometheus metric `retention_worker_file_ref_skipped_total` (Counter) in `RetentionHotBodyJanitor.java`
- [x] T013 Update `docs/RETENTION_AND_DEEP_ARCHIVE.md` — add §6 (chunking), update §2 (TTL semantics), mark §10 progress
- [x] T014 Update `docs/db/FLYWAY_AND_SCHEMA.md` — add V023 migration description
- [x] T015 Update `docs/NATS_SUBJECTS_INTEROP.md` — clarify deep-archive chunked format (no subject changes)
- [x] T016 Update `docs/ROADMAP_EPICS.md` — mark Epic 01 Phase B progress
- [ ] T048 Create and approve ADR — implementation gates passed (2026-05-24); formal Architecture/PO/Ops sign-off pending
- [ ] T056 [US3] Collect approvals in `docs/adr/ADR-hotplug-deployment-split.md` (Architecture/PO/Ops) and resolve governance note `docs/proposals/constitution-v1.1-hotplug-bounded-exception.md`

**Checkpoint**: Epic 01 steps 6-9 complete. Foundation ready.

---

## Phase 3: User Story 1 — Identify memory & CPU bottlenecks (Priority: P1) 🎯 MVP

**Goal**: Profile all modules under representative load and document hotspots

**Independent Test**: Each module has a JFR recording file and corresponding hotspot report

### Implementation

- [x] T017 [P] [US1] Profile core-api under load — Prometheus baseline on QEMU; JFR optional host-local
- [x] T018 [P] [US1] Profile DeepArchiverWorker — static + `smoke-deep-archive-chunks.ps1` (no metrics port)
- [x] T019 [P] [US1] Profile RetentionWorker — Prometheus via `profile-qemu-workers.ps1`; streaming chunk fix applied
- [x] T020 [P] [US1] Profile IndexerWorker — static + T023/T036 functional verify (no metrics port)
- [x] T021 [P] [US1] Profile ExportReplayWorker — Prometheus idle/post-load; export load via `smoke-export-chat.ps1`
- [x] T022 [US1] Consolidated hotspot report — `docs/review/hotspots-2026-05-23.md`
- [x] T050 [US1] Top-3 fixes: `RedisProbe`, `ChunkedSnapshotWriter`, `writeChunkedSnapshotFromFile` (retention tempfile path)
- [x] T051 [US1] Before/after report — `docs/review/hotspots-before-after-2026-05-24.md`
- [x] T052 [US1] SC-003 — core-api −61% post-load; retention streaming fix; full JFR deferred

**Checkpoint**: Bottlenecks documented and prioritized.

---

## Phase 4: User Story 2 — Complete Epic 01 (Priority: P1)

**Goal**: All Epic 01 checkboxes checked, tests pass, docs updated

**Independent Test**: All checkboxes in `docs/plans/01-retention-phase-b.md` are marked `[x]`

**Note**: Env variable `RETENTION_CHUNK_THRESHOLD_BYTES` defaults to 0 (disabled). Set to `2048` to enable chunking during smoke tests.

### Implementation

- [x] T023 [US2] Verify Solr atomic update: send test message with `visibility_ttl_seconds=60`, wait for retention pass, confirm Solr `content_txt` is empty
- [x] T024 [US2] Verify Prometheus metrics: run load test, check `deep_archiver_chunk_writes_total`, `retention_worker_chunk_writes_total`, `retention_worker_file_ref_skipped_total` via `/metrics`
- [x] T025 [US2] Verify chunked deep-archive: send message with large content, confirm `messages/{id}/manifest.json` + `part-*.json` in MinIO
- [x] T026 [US2] Verify file-ref skip: send message with `content = "file://{uuid}"`, confirm no deep-archive or retention snapshot created
- [x] T027 [US2] Run `./gradlew test` — all tests pass
- [x] T028 [US2] Final checkbox sweep of `docs/plans/01-retention-phase-b.md` — all items `[x]`

**Checkpoint**: Epic 01 fully shippable.

---

## Phase 5: User Story 3 — Extract hot-path services as microservices with hot-plug (Priority: P2)

**Goal**: IndexerWorker extracted as standalone service, hot-plug lifecycle implemented

**Independent Test**: Start core-api without indexer, send message, start indexer — confirm backlog indexing, stop indexer — confirm graceful shutdown, restart — confirm hot-plug resume

### Implementation

- [x] T029 [US3] Create service skeleton `services/indexer/build.gradle.kts` — standalone JAR with `main()`, depends on `modules:common`
- [x] T030 [P] [US3] Implement `HotPlugHeartbeat` in `modules/common/src/main/java/.../common/hotplug/HotPlugHeartbeat.java` — publishes `$SVC.heartbeat.{id}` every 10s, runs in executor
- [x] T031 [P] [US3] Implement `HotPlugRegistry` in `modules/common/src/main/java/.../common/hotplug/HotPlugRegistry.java` — subscribes `$SVC.heartbeat.*`, tracks services with TTL (30s), exposes `isPresent(serviceId)`
- [x] T032 [P] [US3] Implement `GracefulShutdown` in `modules/common/src/main/java/.../common/hotplug/GracefulShutdown.java` — shutdown hook: DRAINING state → drain NATS → close resources → exit
- [x] T033 [P] [US3] Add HTTP health endpoints (`/health`, `/ready`) to the extracted service — configurable port via `SERVICE_HTTP_PORT`
- [x] T053 [P] [US3] Add Prometheus metrics for hot-plug flow in `modules/common/.../hotplug/` — heartbeat publish total, heartbeat receive total, registry stale-service removals, lifecycle drain duration
- [x] T054 [P] [US3] Add tests for hot-plug metrics export in `modules/common/src/test/java/.../hotplug/` and service-level `/metrics` coverage
- [x] T034 [US3] Modify `IndexerWorker` to use `HotPlugHeartbeat` + `GracefulShutdown` + health endpoints
- [x] T035 [US3] Wire `HotPlugRegistry` into core-api `AppConfig` — detect indexer absence, skip Solr indexing gracefully
- [x] T055 [US3] Update `docs/NATS_SUBJECTS_INTEROP.md` with hot-plug lifecycle subjects (`$SVC.heartbeat.*`, lifecycle events, queue groups) and compatibility notes
- [x] T036 [US3] Write integration test `HotPlugIndexerTest` — start/stop indexer service, verify message backlog processed on reconnection
- [x] T037 [US3] Write smoke script `scripts/smoke-hotplug-indexer.ps1` — full hot-plug lifecycle test

**Checkpoint**: IndexerWorker independently deployable, hot-pluggable, gracefully degradable.

---

## Phase 6: User Story 4 — Code review & technical debt reduction (Priority: P3)

**Goal**: Systematic review report with severity-graded findings

**Independent Test**: At least 10 findings documented with severity

### Implementation

- [x] T038 [P] [US4] Scan for dead code: unused imports, unused methods, commented-out blocks across `modules/`
- [x] T039 [P] [US4] Scan for duplicate logic: compare `DeepArchiverWorker.writeChunked()` vs `RetentionHotBodyJanitor.writeRetentionChunks()` — extract shared method
- [x] T040 [P] [US4] Audit error handling: verify all `catch(Exception)` blocks have proper logging (not `e.printStackTrace()` or empty catches)
- [x] T041 [P] [US4] Audit dependency versions: check for outdated MinIO SDK versions (8.5.10 vs 8.5.17 across modules) — consolidate to single version
- [x] T042 [P] [US4] Review `modules/core-api/src/main/java/.../api/config/AppConfig.java` for hardcoded values that should be env-configurable
- [x] T043 [US4] Write consolidated review report at `docs/review/code-review-2026-05-23.md` — findings with severity, location, and fix suggestion

**Checkpoint**: Technical debt baseline documented.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [x] T044 [P] Run `./gradlew test` — full test suite passes
- [x] T045 [P] Run `./scripts/smoke-*` — all smoke scripts pass
- [x] T046 Final sweep: verify all generated artifacts in `specs/001-system-review-refactoring/` are consistent
- [x] T047 Update `docs/plans/01-retention-phase-b.md` status to `completed`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS all user stories
- **US1 Profiling (Phase 3)**: Depends on Foundational (for Prometheus metrics to observe)
- **US2 Epic 01 (Phase 4)**: Depends on Foundational — can run in parallel with US1
- **US3 Microservices (Phase 5)**: Depends on Foundational **and** T048 architecture approval gate — can partially parallel with US1/US2 after gate is resolved
- **US4 Code Review (Phase 6)**: Independent — can start after Setup
- **Polish (Phase 7)**: Depends on all user stories

### Parallel Opportunities

- T006-T012 (Prometheus metrics + tests) can all run in parallel
- T017-T021 (profiling all modules) can run in parallel
- T029-T033 and T053-T054 (microservice skeleton + hotplug observability) can run in parallel
- T038-T042 (code review scans) can run in parallel

### Execution Order (dependency-driven)

Phase 1 → Phase 2 → (Phase 3 || Phase 4 || Phase 6) → Phase 5 (after T048 gate) → Phase 7

US1, US2, and US4 can progress in parallel once Phase 2 is complete; US3 starts only after explicit architecture approval.
