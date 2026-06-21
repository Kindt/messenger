# Implementation Plan: Strict Base + Add-ons Conformance

**Branch**: `024-strict-base-addons` | **Date**: 2026-06-21 | **Spec**: [`spec.md`](spec.md)

**Input**: Feature specification from `/specs/024-strict-base-addons/spec.md`

## Summary

Довести Product Modules до строгой модели `Base + atomic add-ons + substrates`: каталог v2 становится единым источником ownership, lifecycle, optional migration bundles, declarative gates and acceptance coverage; runtime вычисляет feature-level state и закрывает API/UI/jobs/hooks до готовности add-on-а. Base-only должен оставаться рабочим без optional schemas/workers/services, а hot install/admin disablement должны быть представлены как явный lifecycle без удаления данных и без runtime DDL.

## Technical Context

<!--
  ACTION REQUIRED: Replace the content in this section with the technical details
  for the project. The structure here is presented in advisory capacity to guide
  the iteration process.
-->

**Language/Version**: Java 25 for runtime; YAML/Markdown for catalog/contracts; vanilla JavaScript for web UI.

**Primary Dependencies**: Jersey/JAX-RS 4.0, Jackson YAML, Flyway, H2/JUnit 5, existing `AppConfig`, `PlatformModuleRegistry`, `PlatformAddonGateFilter`, admin UI and webui assets.

**Storage**: PostgreSQL/Flyway in production; H2 migration/contract tests. Runtime may validate optional schema readiness but MUST NOT create optional add-on schema.

**Testing**: JUnit 5 unit/contract tests, H2 migration layout tests, targeted JS asset checks where practical, final `./gradlew buildIntegrity`.

**Target Platform**: Core API, web client assets, deploy/Ansible contract metadata and lab/QEMU runtime acceptance only if live-stack checks are needed.

**Project Type**: Modular monolith with optional worker processes and declarative deployment composition.

**Performance Goals**: Capability resolution is read-mostly and bounded by catalog size; request gating must use precomputed catalog maps and avoid user-path filesystem reads.

**Constraints**: Pre-release `0.0.1-SNAPSHOT`; no legacy compatibility requirement; no host Docker/Compose; stage/prod/live-server work is out of scope; explicit selected add-ons override legacy profile shims; disabled add-ons must not expose available user actions.

**Scale/Scope**: Covers all add-ons listed in spec 024, Base feature ownership, substrate ownership, optional migration model, declarative gates, lifecycle dimensions and acceptance metadata.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Spec-first, contract-driven**: PASS. Spec 024 is the source; this plan adds explicit contracts for capabilities, gates and migration bundles before implementation.
- **Retention & compliance by design**: PASS. Retention/export/archive/deep-archive/DLP/E2EE are add-on-owned with disablement preserving schema/data.
- **Testability**: PASS. New catalog/runtime logic is unit tested; migration layout is H2/contract tested; filters and feature states have focused tests.
- **Observability & operability**: PASS. Admin and public views expose effective state/reason; worker/job behavior is declarative in the catalog.
- **Clean Architecture**: PASS. Product module resolution remains in platform service classes; JAX-RS resources only expose DTOs.
- **Infrastructure parity**: PASS. Runtime stack checks, if needed, use lab/QEMU; optional migrations are deploy/pre-migration owned.

## Project Structure

### Documentation (this feature)

```text
specs/024-strict-base-addons/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── catalog-v2-contract.md
│   ├── capabilities-contract.md
│   └── migration-bundles-contract.md
├── checklists/
│   └── requirements.md
└── tasks.md
```

### Source Code (repository root)
<!--
  ACTION REQUIRED: Replace the placeholder tree below with the concrete layout
  for this feature. Delete unused options and expand the chosen structure with
  real paths (e.g., apps/admin, packages/something). The delivered plan must
  not include Option labels.
-->

```text
modules/core-api/src/main/resources/
├── product-modules.yaml
└── db/migration/

modules/core-api/src/main/java/com/avandocmsg/messenger/api/platform/
├── ProductModulesCatalog.java
├── ProductModuleCatalogLoader.java
├── PlatformModuleRegistry.java
├── PlatformAddonGateFilter.java
├── AdminProductModulesResource.java
└── dto/

modules/core-api/src/test/java/com/avandocmsg/messenger/api/platform/
├── PlatformModuleRegistryTest.java
├── ProductModuleCatalogConformanceTest.java
├── PlatformAddonGateFilterTest.java
└── ProductModuleMigrationBundleTest.java

modules/web-client/src/main/resources/webui/
├── app.js
└── related UI helper assets

deploy/ansible/
└── group_vars/templates that consume selected add-ons
```

**Structure Decision**: Extend the existing Product Modules platform package instead of introducing a second catalog or entitlement service. Catalog v2 remains packaged in `core-api` resources and is consumed by runtime, tests, deploy metadata and UI capability checks.

## Phase Strategy

1. **Wave 1**: Catalog v2, owner taxonomy and substrates.
2. **Wave 2**: Optional migration bundle model and schema readiness contract.
3. **Wave 3**: Lifecycle dimensions (`selected`, `installed`, `schema_installed`, `runtime_ready`, `admin_enabled`, `effective_state`, `reason`).
4. **Wave 4**: Declarative API/UI/job/hook gates and feature-level capabilities.
5. **Wave 5**: Runtime behavior for Base-only safety, hot install, admin disablement and worker/job policy.
6. **Wave 6**: UI gating and admin diagnostics.
7. **Wave 7**: Deploy/pre-migration selection semantics and legacy profile precedence.
8. **Wave 8**: Tests, smokes, docs/spec/tasks/checklist polish and final `buildIntegrity`.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| None | N/A | N/A |
