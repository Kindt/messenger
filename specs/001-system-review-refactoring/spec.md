# Feature Specification: System Review, Refactoring & Optimization

**Feature Branch**: `001-system-review-refactoring`

**Created**: 2026-05-23

**Status**: Ready for Implementation

**Input**: User description: "Запустить Spec-kit с ревью, исправлениями, рефакторингом по памяти и процессору... не закрытые доработки по функциям. возможно, выделение чего-то в виде микросервисов с горячим подключением к работающему серверу"

## User Scenarios & Testing

### User Story 1 - Identify memory & CPU bottlenecks (Priority: P1)

As a system operator, I want to understand where memory and CPU are being consumed across all services (core-api, deep-archiver, retention, export-replay, indexer, Solr), so that I can prioritize optimization efforts.

**Why this priority**: Memory/CPU efficiency directly impacts infrastructure cost and scalability. Without data, all other optimizations are guesses.

**Independent Test**: Can be verified by running a profiling session with a known message load and comparing before/after metrics.

**Acceptance Scenarios**:

1. **Given** a running system with representative load, **When** a profiling/observation window is executed, **Then** each module reports CPU and heap usage with top 3 hotspots identified.
2. **Given** the hotspots are identified, **When** analyzed, **Then** each has a documented root cause and estimated impact percentage.

---

### User Story 2 - Complete Epic 01 unfinished items (Priority: P1)

As a developer, I want to complete all remaining tasks in Epic 01 Retention Phase B (steps 6-9: Solr, web-client TTL, metrics, docs), so that the retention feature is fully shippable.

**Why this priority**: Unfinished work blocks shipping. These items are already specified in `docs/plans/01-retention-phase-b.md`.

**Independent Test**: All Epic 01 checkboxes are marked done and corresponding tests pass.

**Acceptance Scenarios**:

1. **Given** the retention worker clears message content, **When** the content is cleared, **Then** Solr index is also updated (content_txt removed) via IndexerWorker.
2. **Given** a message with TTL, **When** rendered in web-client, **Then** a timer indicator shows remaining visibility time.
3. **Given** the system runs with chunking or file-ref skipping, **When** those operations occur, **Then** corresponding Prometheus metrics are emitted.
4. **Given** Epic 01 work is complete, **When** documentation is reviewed, **Then** docs/RETENTION_AND_DEEP_ARCHIVE.md and docs/db/FLYWAY_AND_SCHEMA.md reflect the current state.

---

### User Story 3 - Extract hot-path services as microservices with hot-plug (Priority: P2)

As an operations engineer, I want to selectively extract high-throughput or high-memory services (e.g., Solr indexing, retention processing) into standalone microservices that can be started/stopped without restarting the core server.

**Why this priority**: Hot-plug capability allows operational flexibility — scale individual components without downtime — but requires careful interface design and graceful connection handling.

**Independent Test**: Can be verified by starting/stopping a candidate microservice while the core server continues serving requests.

**Acceptance Scenarios**:

1. **Given** a candidate module (e.g., IndexerWorker) is extracted as a standalone service, **When** it is started, **Then** it connects to NATS, registers, and begins processing without affecting already-running connections.
2. **Given** the same extracted service is running, **When** it is gracefully stopped (SIGTERM), **Then** it completes in-flight work, drains its NATS queue, and disconnects cleanly.
3. **Given** the core server is running without the extracted service, **When** a message is sent, **Then** the message is stored and queued; when the service is reconnected, **Then** it processes the backlog without data loss.
4. **Given** an extracted service crashes unexpectedly, **When** the core server detects the absence, **Then** it continues operating with reduced functionality (graceful degradation) and logs the disconnection.

---

### User Story 4 - Code review & technical debt reduction (Priority: P3)

As a developer, I want a systematic review of the codebase for dead code, duplicate logic, inconsistent error handling, and outdated dependencies, so that the codebase is cleaner and easier to maintain.

**Why this priority**: Lower priority because current functionality works, but debt compounds over time.

**Independent Test**: Can be verified by checking that review items are documented and addressed in subsequent sprints.

**Acceptance Scenarios**:

1. **Given** the codebase is reviewed, **When** the review is complete, **Then** a report documents all findings with severity levels.
2. **Given** findings are identified, **When** high-severity items are fixed, **Then** tests continue to pass and no regressions are introduced.

---

### Edge Cases

- What happens when a microservice is extracted and its NATS queue group already has consumers?
- How does hot-plug work when multiple instances of the same extracted service are deployed?
- What if the extracted service depends on local state (in-memory caches, temp files)?
- How does graceful shutdown handle long-running batch operations (retention passes)?

## Requirements

### Functional Requirements

- **FR-001**: System MUST provide observability into memory and CPU usage per module (heap dumps, GC logs, profiler output).
- **FR-002**: Epic 01 steps 6-9 MUST be completed: Solr content clearing on retention, web-client TTL indicator, Prometheus metrics for chunking/file-ref, documentation updates.
- **FR-003**: Extracted microservices MUST connect to the existing NATS message bus using queue groups for workload distribution.
- **FR-004**: Extracted microservices MUST support graceful shutdown with in-flight work completion and queue drain.
- **FR-005**: Core server MUST detect the presence/absence of extracted microservices and operate with graceful degradation.
- **FR-006**: Hot-plug (start/stop without restarting core server) MUST be supported for each extracted microservice.
- **FR-007**: Code review findings MUST be documented with severity (critical/high/medium/low) and estimated effort.
- **FR-008**: Hot-plug heartbeat and service presence detection flows MUST expose Prometheus metrics for publish/consume/error states.
- **FR-009**: Any new NATS subject introduced for hot-plug lifecycle MUST be documented in `docs/NATS_SUBJECTS_INTEROP.md` before release.
- **FR-010**: Performance optimization work MUST include a measurable implementation loop (apply fixes + rerun profiling) and a before/after comparison for SC-003.

### Key Entities

- **ServiceCandidate**: A module identified for potential microservice extraction, with attributes for NATS subjects, resource profile (CPU/memory), and dependency graph.
- **HotPlugContract**: The interface (NATS subjects, health endpoints, lifecycle signals) that each extracted microservice must implement.
- **BottleneckHotspot**: A documented memory or CPU hotspot with location, estimated impact, and proposed fix.

## Success Criteria

### Measurable Outcomes

- **SC-001**: Epic 01 steps 6-9 are 100% complete with all checkboxes checked and corresponding tests passing.
- **SC-002**: At least one candidate microservice is extracted and can be started/stopped independently without restarting the core server, verified by an automated smoke test.
- **SC-003**: The system processes the same load with at least 15% less memory (heap) after optimization, or the optimization report documents why this target is not achievable.
- **SC-004**: Code review identifies and resolves or documents at least 10 findings across all severity levels.
- **SC-005**: Graceful shutdown and hot-plug are verified by automated tests that start/stop the extracted service while simulating message flow.

## Assumptions

- NATS queue groups are the mechanism for workload distribution to extracted microservices; no additional service discovery layer is needed.
- The existing health check endpoints (`/health`, `/ready`) at core-api are sufficient for detecting core server health.
- Extracted microservices will use the same MinIO, PostgreSQL, and Redis instances as the core server (shared infrastructure).
- Graceful shutdown will rely on JVM shutdown hooks and NATS connection drain (existing pattern in current workers).
- The user has a profiling tool available (JDK Mission Control, async-profiler, or similar).
- Performance optimization targets are based on the current load profile; actual targets will be validated during implementation.
