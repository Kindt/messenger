# Data Model: Strict Base + Add-ons

## ProductBase

- `id`: canonical Base identifier, `korus-messenger-base`.
- `label`: display label.
- `features`: Base-owned feature keys, always available when core is healthy.
- `external_stack_components` / `external_stack_profiles`: required Base runtime dependencies.

Validation:

- Base has no dependency on add-on-owned schema, services, workers or UI controls.
- Every Base feature key has owner `base`.

## Addon

- `id`: stable add-on ID from spec 024.
- `label`: Russian operator/product label.
- `features`: add-on-owned feature keys with labels, gate behavior and optional dependencies.
- `migration_bundle`: optional deploy/pre-migration bundle metadata.
- `runtime`: workers/services/secrets/env/health checks.
- `degradation`: default disabled/degraded/installing behavior.
- `gates`: API/UI/job/hook gates owned by the add-on.
- `acceptance`: positive, disabled and degraded coverage IDs.

Validation:

- Every add-on defines enabled, disabled/degraded/installing behavior.
- Every add-on has at least one positive and one disabled/degraded acceptance item.
- Every feature/gate/db object has exactly one owner.

## Substrate

- `id`: internal substrate ID.
- `label`: technical label.
- `features` and `db_objects`: internal support surface.
- `migration_bundle`: optional substrate migration bundle.

Validation:

- Substrates are not listed as user-facing add-ons.
- Add-ons may depend on substrates without enabling user-facing substrate controls.

## AddonRuntimeState

- `selected`: operator/deploy requested the add-on.
- `installed`: deploy bundle installed for the add-on.
- `schema_installed`: migration bundle is present and contract is valid.
- `runtime_ready`: required services, workers, secrets and health checks are ready.
- `admin_enabled`: no admin soft-disable override is active.
- `effective_state`: `enabled`, `disabled`, `degraded` or `installing`.
- `reason`: stable reason code.

State rules:

- `selected=false` -> `disabled/not_selected`.
- `selected=true`, `installed=false` -> `installing/install_requested`.
- `installed=true`, `schema_installed=false` -> `degraded/schema_missing`.
- `schema_installed=true`, runtime checks fail -> `degraded/<specific reason>`.
- `admin_enabled=false` -> `disabled/admin_override`.
- All dimensions healthy -> `enabled`.

## FeatureState

- `key`: atomic feature key.
- `owner`: `base`, add-on ID or substrate ID.
- `state`: effective feature state.
- `reason`: inherited or feature-specific reason.
- `ui_behavior`: hide/disable/badge/readonly/fallback behavior.
- `api_behavior`: HTTP/fallback behavior when unavailable.
- `dependencies`: feature keys or owners that must also be enabled.

Validation:

- Public `/platform/capabilities` includes feature-level state, owner, reason and UI behavior.
- UI and API gates reason by feature key.

## MigrationBundle

- `id`: bundle ID such as `addon-search`.
- `owner`: Base, add-on or substrate owner.
- `location`: target migration directory.
- `history_table`: dedicated Flyway history table.
- `schema_objects`: owned tables/indexes/seed rows.

Validation:

- Base bundle applies first.
- Selected substrate bundles apply before selected add-on bundles.
- Add-on bundles use dedicated history tables.
