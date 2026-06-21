# Capabilities Contract

## Public endpoint

`GET /api/v1/platform/capabilities`

Public response MUST include concise state for clients:

- `product.base`
- `product.addons_enabled`
- `modules.<addonId>`
- `features.<featureKey>`
- `infra`
- `base_media`
- `external_stack`

Module state fields:

- `selected`
- `installed`
- `schema_installed`
- `runtime_ready`
- `admin_enabled`
- `state`
- `reason`
- `label`
- `degradation_mode`
- `ui_behavior`

Feature state fields:

- `owner`
- `state`
- `reason`
- `ui_behavior`
- `api_behavior`

The endpoint MUST NOT expose unavailable add-ons as available actions. Disabled add-ons may be present as state metadata, but feature controls must be hidden/disabled according to `ui_behavior`.

## Admin endpoint

`GET /api/v1/admin/ui/product-modules`

Admin response MUST include operator diagnostics:

- lifecycle dimensions for every add-on;
- required services, workers, secrets and schema bundle;
- missing/degraded reason;
- gate counts and acceptance coverage IDs;
- admin override flags.

## Override endpoint

`PUT /api/v1/admin/ui/product-modules/{addonId}/override`

Disablement semantics:

- close gates with `disabled/admin_override`;
- preserve schema, data and configuration;
- allow fast re-enable when runtime remains healthy.
