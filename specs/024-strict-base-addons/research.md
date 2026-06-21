# Research: Strict Base + Add-ons Conformance

## Decision: Catalog v2 is the single source for ownership, gates and lifecycle

**Rationale**: The existing `product-modules.yaml` already drives capabilities and deploy metadata. Extending it avoids a second entitlement catalog and lets runtime, tests, UI and deploy checks converge on the same add-on IDs.

**Alternatives considered**:

- Hardcoded Java maps for gates: rejected because spec 024 requires declarative gates.
- Separate UI catalog: rejected because it would drift from runtime states.

## Decision: Feature-level state is derived from add-on state plus feature overrides

**Rationale**: Some add-ons have independent feature health, for example `addon-engage` push vs link previews and `addon-ai` assist vs captions. The runtime should expose `features` with owner, state, reason and UI behavior instead of forcing clients to infer from add-on IDs.

**Alternatives considered**:

- Add-on-only public capabilities: rejected because it cannot hide one feature while keeping another feature from the same add-on usable.

## Decision: Optional migrations are deploy/pre-migration bundles, not runtime DDL

**Rationale**: Core API must not silently create optional schema. Runtime records expected bundle and schema contract metadata and reports `schema_missing` / `schema_contract_failed` when a selected add-on is not prepared.

**Alternatives considered**:

- Flyway auto-run from core runtime for all bundles: rejected because Base-only would create optional schema and violate FR-031.

## Decision: Base-only acceptance is contract-enforced in unit/H2 tests

**Rationale**: A full live-stack Base-only smoke may require QEMU availability. The core conformance can be proven repo-locally by validating catalog ownership, disabled gates, Base migration bundle isolation and fallback behavior. QEMU smoke remains optional evidence, not a stage/prod gate.

**Alternatives considered**:

- Host Docker smoke: rejected by project rules.

## Decision: Admin disablement changes effective state only

**Rationale**: Disablement must preserve schema, data and configuration. The override repository remains the persistence point; runtime translates it to `disabled/admin_override` and gate closure.

**Alternatives considered**:

- Uninstall by removing selected add-on: rejected because hot re-enable would lose operator intent and installed state.
