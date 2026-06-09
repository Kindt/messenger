# Implementation Plan: Deferred Phase 2 Post-Backlog Closure

**Branch**: `004-deferred-phase2-closure` | **Date**: 2026-06-09 | **Spec**: [spec.md](spec.md)

## Summary

Close post-backlog E0–E10 deferred work via eight user stories: production TLS/Vault (extends spec 003), hexagonal write-path and cleanup, worker JFR profiling overlay, Playwright full-stack gates, governance sign-off, full client-side E2EE MLS, and optional QEMU stability. MVP = US1 + US2 (C1–C3).

## Technical Context

**Language/Version**: Java 25, Ansible 2.14+, TypeScript Playwright, PowerShell/Bash smokes

**Primary Dependencies**: Docker Compose, PostgreSQL, NATS, MinIO, Keycloak, nginx/certbot (tls role), BouncyCastle/OpenMLS (E2EE spike)

**Storage**: PostgreSQL (hex ports, MLS state); MinIO (ObjectStoragePort)

**Testing**: `./gradlew.bat buildIntegrity` (PR); H2 adapter tests; Playwright; smoke scripts

**Target Platform**: Linux stage/prod; Windows dev/QEMU; browser WASM for E2EE client

**Performance Goals**: CoreApiBenchmarkTest write/read budgets; MlsBenchmarkTest p50 < 50ms

**Constraints**: Prod JRE images unchanged; US7 blocked until T130 product sign-off; no secrets in git

**Scale/Scope**: 8 workers profiling; 3 hex write PRs + tail; 9 Playwright specs enhancement

## Constitution Check

*GATE: Must pass before implementation. Re-checked after design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I Spec-First | PASS | spec 004 + contracts; US7 requires ADR/NATS update before crypto |
| II Retention | N/A | No new retention paths |
| III Testability | PASS | H2 per port; Playwright; smokes |
| IV Observability | PASS | MLS metrics in US7; existing worker metrics unchanged |
| V Clean Architecture | PASS | US2 write via application layer |
| VI Infra Parity | PASS | Stage inventory + smokes on live stack |

**Post-design**: No violations; bounded E2EE and TLS sign-offs documented in quickstart.

## Project Structure

```text
specs/004-deferred-phase2-closure/
├── spec.md, plan.md, research.md, data-model.md, tasks.md, quickstart.md
├── contracts/*.md
├── checklists/requirements.md
└── analyze-report.md, acceptance-report.md

deploy/ansible/inventory/prod/
deploy/ansible/roles/tls/, korus_server/, korus_web/
modules/core-api/.../core/port|application|adapter/
docker/Dockerfile.*.profiling, docker-compose.profiling.yml
scripts/profiling/, smoke-tls-redirect.ps1
tests/e2e-web/specs/
deploy/qemu/lib/
```

## Phases

| Phase | US | Deliverable |
|-------|-----|-------------|
| 1–2 | — | Spec-kit artifacts, feature.json |
| 3 | US1 | prod inventory, vault env, TLS CI smoke |
| 4 | US2 | User/Org/File write hex |
| 5 | US3 | saved-chat, public links, cleanup |
| 6 | US4 | 5 profiling Dockerfiles + compose |
| 7 | US5 | Playwright gates |
| 8–9 | US7 | E2EE full + e2ee playwright |
| 10 | US6 | governance + docs |
| 11 | US8 | QEMU DX |
| 12 | — | acceptance-report |

## Complexity Tracking

| Decision | Why | Alternative rejected |
|----------|-----|---------------------|
| Extend spec 003 not rewrite | Phase 8 scaffold done | New deploy stack |
| 3 parallel hex PRs | Independent aggregates | Single mega-PR |
| WASM client MLS | True E2EE in browser | Server-only encrypt |
| JDK profiling overlay | No prod JRE change | jcmd on host only |
