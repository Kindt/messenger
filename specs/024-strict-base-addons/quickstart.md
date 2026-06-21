# Quickstart: Strict Base + Add-ons Checks

## Repo-local checks

Run focused checks after each relevant wave:

```powershell
./gradlew :modules:core-api:test --tests "*ProductModule*"
./gradlew :modules:core-api:test --tests "*PlatformAddonGateFilterTest"
./gradlew :modules:web-client:test
```

Final gate:

```powershell
./gradlew buildIntegrity
```

## Base-only scenario

Configuration:

```text
KORUS_PRODUCT_ADDONS=
KORUS_DEPLOY_PROFILE=pilot
```

Expected:

- Base features are enabled.
- All add-ons are `disabled/not_selected`.
- Add-on feature controls are hidden/disabled.
- Add-on API gates reject or fallback according to catalog.
- Optional add-on schema is not required by Base workflows.

## Single add-on scenario

Configuration example:

```text
KORUS_PRODUCT_ADDONS=addon-search
```

Expected:

- `addon-search` is selected.
- Full-text feature is enabled only when schema/runtime checks pass.
- SQL search feature remains Base.
- Disabled add-ons remain hidden/closed.

## Hot install scenario

1. Start from Base-only.
2. Mark add-on selected / install requested.
3. Deploy/pre-migration applies required substrate and add-on bundles.
4. Runtime reports `installing` or `degraded/schema_missing` until schema readiness is true.
5. Gates open after schema, runtime, secrets and admin state are healthy.

## Admin disablement scenario

1. Start with installed and ready add-on.
2. Set admin override disabled.
3. Verify module state is `disabled/admin_override`.
4. Verify API/UI/jobs/hooks close according to catalog.
5. Re-enable override and verify state returns without migration or data deletion.
