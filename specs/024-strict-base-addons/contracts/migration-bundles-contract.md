# Migration Bundles Contract

## Bundle layout

Target logical layout:

```text
db/migration/base
db/migration/substrate/<substrate>
db/migration/addons/<addon>
```

The implementation may keep legacy flat migrations during transition only if catalog v2 declares ownership and tests prove Base-only does not require add-on schema at runtime.

## History tables

Each deploy/pre-migration bundle has a dedicated Flyway history table:

- Base: `flyway_schema_history`
- Add-ons: `flyway_schema_history_addon_<addon>`
- Substrates: `flyway_schema_history_substrate_<substrate>`

## Deployment order

1. Base migrations.
2. Selected substrate bundles.
3. Selected add-on bundles.
4. Runtime schema readiness validation.
5. Gate opening after runtime readiness.

Core runtime MUST NOT run optional add-on migrations silently.

## Runtime validation

Runtime reports:

- `schema_missing` when an installed add-on lacks required schema objects.
- `schema_contract_failed` when schema objects exist but do not satisfy the contract.
- `install_requested` / `migration_running` while hot install is in progress.

Admin disablement MUST NOT drop optional schema or data.
