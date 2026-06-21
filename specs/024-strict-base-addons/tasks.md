# Tasks: Strict Base + Add-ons Conformance

**Input**: `spec.md`, `plan.md`, `research.md`, `data-model.md`, `contracts/`

## Phase 1: Setup

- [x] T001 Update spec-kit current plan references in `.cursor/rules/specify-rules.mdc` and `AGENTS.md`.
- [x] T002 [P] Add/verify plan artifacts in `specs/024-strict-base-addons/plan.md`, `research.md`, `data-model.md`, `quickstart.md`, and `contracts/`.

## Phase 2: Foundational Catalog and Runtime Model

- [x] T003 Replace `modules/core-api/src/main/resources/product-modules.yaml` with catalog v2: Base, substrates, 17 add-ons, feature ownership, migration bundles, runtime metadata, gates and acceptance coverage.
- [x] T004 Extend `ProductModulesCatalog.java` for catalog v2 features, substrates, migration bundles, runtime state inputs and declarative gates.
- [x] T005 Extend `ProductModuleCatalogLoader.java` with conformance validation: unique owners, add-on coverage, gate feature ownership, migration bundle history tables and external stack references.
- [x] T006 Extend `PlatformModuleState.java`, `PlatformModuleReason.java` and `PlatformModuleRegistry.java` with lifecycle dimensions and feature-level effective state.

## Phase 3: Wave 1 - Catalog v2 / Ownership / Taxonomy / Substrates

- [x] T007 [P] [US1] Add catalog conformance tests in `ProductModuleCatalogConformanceTest.java` for Base feature ownership and add-on taxonomy.
- [x] T008 [US1] Implement Base-only capability resolution in `PlatformModuleRegistry.java` and `PlatformCapabilitiesResponse.java`.
- [x] T009 [US1] Verify all non-Base feature keys in spec 024 are owned by add-ons or substrates in `product-modules.yaml`.

## Phase 4: Wave 2 - DB Ownership and Optional Migration Bundles

- [x] T010 [P] [US2] Add migration bundle contract tests in `ProductModuleMigrationBundleTest.java`.
- [x] T011 [US2] Add migration bundle metadata and schema object ownership to `product-modules.yaml`.
- [x] T012 [US2] Implement runtime schema readiness dimensions without runtime optional DDL in `PlatformModuleRegistry.java`.

## Phase 5: Wave 3 - Lifecycle State

- [x] T013 [P] [US3] Add unit tests for selected/installed/schema/runtime/admin/effective state combinations in `PlatformModuleRegistryTest.java`.
- [x] T014 [US3] Implement lifecycle precedence: explicit selected add-ons, legacy shims, install request, schema state, runtime readiness, secrets, health and admin overrides.
- [x] T015 [US3] Extend admin DTOs in `AdminProductModulesResponse.java` and resource mapping in `AdminProductModulesResource.java`.

## Phase 6: Wave 4 - Declarative Gates and Capabilities Feature State

- [x] T016 [P] [US2] Add API gate tests for disabled/installing/degraded/enabled states in `PlatformAddonGateFilterTest.java`.
- [x] T017 [US2] Replace manual path-prefix authority in `PlatformAddonGateFilter.java` with compiled catalog API gates.
- [x] T018 [US2] Expose `features` with owner/state/reason/UI/API behavior in `/platform/capabilities`.
- [x] T019 [US2] Add catalog job and hook gate metadata for scheduler/worker/hook behavior.

## Phase 7: Wave 5 - Add-on Runtime Behavior

- [x] T020 [P] [US4] Add tests for job/hook policies: stop, pause, drain, drop, queue and fallback behavior.
- [x] T021 [US4] Implement registry helpers for job/hook policy lookup by feature key.
- [x] T022 [US4] Ensure Base search and Base messaging remain functional when add-ons are disabled/degraded.
- [x] T023 [US4] Cover hot install and admin re-enable paths without data/schema deletion.

## Phase 8: Wave 6 - UI Gating and Admin Diagnostics

- [x] T024 [P] [US1] Add targeted UI asset tests or static checks for add-on controls gated by feature state.
- [x] T025 [US1] Update webui capability helpers in `modules/web-client/src/main/resources/webui/app.js` to reason by feature key where add-on controls are rendered.
- [x] T026 [US3] Add admin diagnostics fields: secrets, services, workers, schema bundle, gates and acceptance IDs.

## Phase 9: Wave 7 - Deploy/Pre-migration Contract

- [x] T027 [P] [US5] Add deploy/pre-migration selection semantics to docs/contracts and catalog metadata.
- [x] T028 [US5] Validate explicit `KORUS_PRODUCT_ADDONS` precedence over legacy profile shims.
- [x] T029 [US5] Align smoke/deploy profile naming with catalog add-on IDs without adding live-server work.

## Phase 10: Wave 8 - Tests, Docs and Final Polish

- [x] T030 Run focused tests for Product Modules, gates, migration bundles and web UI assets.
- [x] T031 Update `specs/024-strict-base-addons/spec.md` status and out-of-scope notes to match implementation.
- [x] T032 Update `specs/024-strict-base-addons/checklists/requirements.md` with final implementation evidence.
- [x] T033 Run `./gradlew buildIntegrity` and fix failures.
- [x] T034 Commit final green state and push branch.

## Dependencies

- T001-T006 block all runtime waves.
- Wave 1 must complete before migration/gate/runtime waves because ownership is the base invariant.
- Wave 2 blocks schema readiness and hot install behavior.
- Wave 3 blocks admin diagnostics and gate state.
- Wave 4 blocks UI and worker/job behavior.
- Waves 6-8 depend on capabilities and gates being stable.

## Implementation Strategy

Deliver in eight commits matching waves. After each wave, run the smallest relevant test subset, fix failures, mark completed tasks, and commit only files touched by that wave. Final completion requires all tasks checked, checklist green and `./gradlew buildIntegrity` green.
