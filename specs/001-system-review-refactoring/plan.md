# Implementation Plan: System Review, Refactoring & Optimization

**Branch**: `main` | **Date**: 2026-05-23 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/001-system-review-refactoring/spec.md`

## Summary

A four-track initiative: (1) profile CPU/memory hotspots across all modules, (2) complete Epic 01 Retention Phase B steps 6-9 (Solr, web-client TTL, metrics, docs), (3) extract candidate services as hot-plug microservices via NATS queue groups, (4) systematic code review and tech debt reduction.

## Technical Context

**Language/Version**: Java 25, Gradle 9.5 KTS
**Primary Dependencies**: embedded Tomcat 11 + Jersey 4.0, NATS 2.10, MinIO SDK 8.5.x, SolrJ 10.x, Lettuce 6.3, Prometheus simpleclient
**Storage**: PostgreSQL 16 (hot + archive), MinIO S3, Redis 7, Apache Solr 10
**Testing**: JUnit 5, H2 in-memory for DB tests, Gradle test runner
**Target Platform**: Linux x86_64 server (Windows dev)
**Project Type**: Modular monolith (core-api + 4 workers + common)
**Performance Goals**: 15% heap reduction on same load; sub-second hot-plug detection; graceful shutdown < 30s
**Constraints**: Zero downtime during hot-plug operations; backward-compatible wire formats; no Spring Boot
**Scale/Scope**: 4 modules + 1 core service; ~25k LOC; 8 PostgreSQL tables; 100+ NATS subjects

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

1. **Spec-First**: ✅ Met — this plan derives from the spec at `specs/001-system-review-refactoring/spec.md`.
2. **Retention & Compliance**: ✅ Met — Epic 01 completion directly addresses retention compliance.
3. **Testability**: ✅ Met — all microservice extraction paths will have unit tests; existing H2 patterns reused.
4. **Observability**: ✅ Met — new Prometheus metrics required for chunking, file-ref skipping, and hot-plug lifecycle.
5. **Clean Architecture**: ⚠️ Microservice extraction reverses modular monolith direction (workers become independent). Justification: workers already communicate via NATS, not direct JVM calls. The NATS layer already provides the interface boundary; extraction just changes the deployment unit. This is NOT a circular dependency — the message flow remains unidirectional: core-api → NATS → worker.
6. **Infrastructure Parity**: ✅ Met — smoke tests for extracted services reuses existing MinIO/NATS/PostgreSQL stack.

**All gates pass.** Complexity is justified for principle V.

## Project Structure

### Documentation (this feature)

```text
specs/001-system-review-refactoring/
├── spec.md                    # This file (/speckit.specify output)
├── plan.md                    # This file (/speckit.plan output)
├── research.md                # Phase 0 output
├── data-model.md              # Phase 1 output
├── quickstart.md              # Phase 1 output
├── contracts/                 # Phase 1 output
│   └── hotplug-contract.md    # NATS subjects & lifecycle contract
├── tasks.md                   # Phase 2 output (/speckit.tasks)
└── checklists/
    └── requirements.md        # Spec quality checklist
```

### Source Code (repository root)
Track 1 — Profiling: no new source; profiling scripts in `scripts/profiling/`.
Track 2 — Epic 01 completion: modifies existing files.
Track 3 — Microservice extraction: new service modules under `services/` or extracted from `modules/workers/`.
Track 4 — Code review: documented in `docs/review/`.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Microservice extraction (breaks modular monolith) | Operational flexibility — scale indexing/retention independently without core restart | In-process threading already exists but doesn't isolate failures or allow independent scaling |

## Research & Design Artifacts

### Phase 0 — Research

See [research.md](research.md) for:
- Profiling tool selection (async-profiler vs JMC vs JFR)
- Microservice extraction patterns (NATS queue groups, shared vs dedicated MinIO buckets)
- Hot-plug lifecycle design (health checks, heartbeat, graceful drain)
- Solr atomic update behavior for empty `content_txt`

### Phase 1 — Design

See [data-model.md](data-model.md) for:
- `BottleneckHotspot` record for profiling findings
- `ServiceCandidate` record for extraction candidates
- `HotPlugLifecycle` states (INIT → ACTIVE → DRAINING → STOPPED)

See [contracts/hotplug-contract.md](contracts/hotplug-contract.md) for:
- NATS subject conventions for heartbeat and lifecycle events
- Health check endpoint contract for extracted services
- Graceful shutdown protocol (SIGTERM → drain → complete → disconnect)
