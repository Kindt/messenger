# Korus Messenger — Constitution

## Core Principles

### I. Spec-First, Contract-Driven
Every feature starts with an OpenAPI spec or NATS subject contract before implementation. API changes MUST be documented in OpenAPI 3.x (`@Schema`, `@JsonProperty`) and wire formats MUST maintain backward compatibility via Jackson `@JsonAlias` for at least one minor version. No endpoint or event payload ships without a contract peer-reviewed by at least one other team member.

### II. Retention & Compliance by Design
All features touching message persistence MUST consider the dual-TTL model (`visibility_ttl_seconds` / `archive_ttl_seconds`) and deep-archive lifecycle. Data deletion or archival paths MUST be auditable via `audit_events`. Export compliance (JSON replay, full-chat export) is a first-class concern — any new storage format MUST have a corresponding reader in `DeepArchiveReader`.

### III. Testability is Non-Negotiable
Every module MUST have unit tests (JUnit 5). Repository-layer changes MUST include H2 in-memory integration tests that verify SQL predicates. Workers MUST have tests for their core logic (JSON shaping, chunking, filtering). TDD is strongly encouraged: red-green-refactor for bug fixes.

### IV. Observability & Operability
Every NATS consumer/publisher MUST have at least one Prometheus metric (counter, histogram, or gauge). SQL query timeouts MUST be configurable via env variables. All worker passes (retention scan, deep-archive) MUST expose duration, count, and error metrics. Health endpoints (`/health`, `/ready`) are required for every HTTP service.

### V. Clean Architecture, Modular Monolith
The project follows a modular monolith with strict dependency direction: `workers/*` → `core-api` → `common`. No circular module dependencies. Business logic lives in services, not in JAX-RS resources. Persistence goes through repositories, never direct JDBC in resources. Shared DTOs live in `modules/common`.

### VI. Infrastructure Parity
Production-like infrastructure (PostgreSQL, MinIO, NATS, Redis, Solr) is required for manual smoke tests. CI runs unit + integration tests against H2 / embedded servers. Smoke scripts in `scripts/` MUST pass before any release. Environment variables with sensible defaults are the universal configuration mechanism — no hardcoded secrets or URLs in source.

## Technology Stack & Constraints

**Runtime**: Java 25, embedded Tomcat 11 + Jersey (JAX-RS) 4.0, not Spring Boot.
**Databases**: PostgreSQL 16 (hot + archive), H2 for tests. Flyway for schema migration.
**Messaging**: NATS 2.10 with optional JetStream.
**Cache**: Redis 7 via Lettuce 6.3.
**Object storage**: MinIO S3.
**Search**: Apache Solr 10 via SolrJ.
**Auth**: Keycloak 24 + JWT (Nimbus JOSE).
**Build**: Gradle Kotlin DSL.
**Metrics**: Prometheus simpleclient (same stack across all modules).

## Development Workflow & Review Process

All changes follow the spec-kit workflow: `/speckit.specify` → `/speckit.plan` → `/speckit.tasks` → `/speckit.implement`.

Every PR MUST include:
1. Link to the spec/plan that drove the change.
2. Evidence of test execution (CI green or local `./gradlew test`).
3. Documentation updates for any new env variables (noted in `application.properties` or worker comments).
4. If adding/modifying an API field: OpenAPI annotation + Jackson `@JsonProperty`.
5. If adding a NATS subject: documented in `docs/NATS_SUBJECTS_INTEROP.md`.

Code review gates:
- **Constitutional compliance**: Does the change follow the principles above?
- **Test coverage**: Are H2 tests or unit tests included for new logic?
- **Backward compatibility**: Is the wire format or storage format compatible with existing data?
- **Observability**: Are Prometheus metrics added for new operations?

## Governance

This constitution supersedes ad-hoc practices. Amendments require:
1. A discussion (at least one other team member).
2. A documented proposal (Markdown in `docs/`).
3. A migration plan for any competing existing practices.
4. A version bump following semver (MAJOR for removed/redefined principles, MINOR for new sections, PATCH for clarifications).

All PRs and reviews MUST verify compliance with this constitution. Complexity MUST be justified — simpler alternatives considered and documented.

**Version**: 1.0.0 | **Ratified**: 2026-05-23 | **Last Amended**: 2026-05-23
