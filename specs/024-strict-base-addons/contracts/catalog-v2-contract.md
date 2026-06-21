# Catalog v2 Contract

`modules/core-api/src/main/resources/product-modules.yaml` is the single source of truth for Product Modules.

## Top-level shape

- `schema_version`: `2`.
- `base`: canonical Base entry.
- `substrates`: internal substrate entries.
- `addons`: product add-on entries.
- `feature_ownership`: optional generated/validated owner index.
- `legacy_deploy_profile_map`: temporary shim. Explicit selected add-ons take precedence.

## Base entry

Required fields:

- `id`
- `label`
- `state: required`
- `features[]`
- `core_infra[]`
- `external_stack_components[]`
- `external_stack_profiles[]`

Each feature requires:

- `key`
- `label`
- `owner: base`
- `ui_behavior`

## Add-on entry

Required fields:

- `id`
- `label`
- `degradation_mode`
- `lifecycle_status`
- `features[]`
- `migration_bundle`
- `runtime`
- `gates`
- `acceptance`

Every add-on MUST define:

- disabled behavior;
- degraded behavior;
- installing behavior;
- runtime state inputs;
- API/UI/job/hook gate metadata, even when one gate list is empty;
- positive and disabled/degraded acceptance IDs.

## Gate contract

API gate:

- `path`
- `methods[]`
- `feature`
- `disabled_behavior`
- `degraded_behavior`
- `installing_behavior`

UI gate:

- `feature`
- `selector` or `control`
- `behavior`

Job gate:

- `job`
- `feature`
- `disabled_behavior`
- `degraded_behavior`

Hook gate:

- `hook`
- `feature`
- `disabled_behavior`
- `degraded_behavior`

Manual path-prefix maps are not authoritative; they may only be compiled from the catalog.
