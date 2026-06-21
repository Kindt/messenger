# Quickstart: Customizable External Stack

## Читать Сначала

1. [`spec.md`](spec.md)
2. [`plan.md`](plan.md)
3. [`data-model.md`](data-model.md)
4. [`contracts/external-stack-validation-contract.md`](contracts/external-stack-validation-contract.md)
5. [`../../docs/external-stack-profiles.yaml`](../../docs/external-stack-profiles.yaml)

## Локальный Контур Проверки

Эта фича не требует live stage/prod. Используйте repo-local checks, unit/H2 tests и QEMU/lab smokes только когда runtime stack уже доступен.

На Windows host не запускать Docker. Для live-stack проверок использовать документированный QEMU/deploy workflow.

## Ожидаемая Первая Волна Реализации

1. Добавить read-only DTO/model для `ComponentBackendManifest` и validation outcomes.
2. Добавить `ManifestObservation` для раздельного desired manifest, observed manifest, health status и degraded reason.
3. Добавить `ImpactModel` для supported profiles: performance, resilience, resources, price/TCO, administration.
4. Загружать или генерировать desired manifests из explicit config/catalog.
5. Показывать observed manifest и degraded reason через admin/platform status с redaction secrets.
6. Добавить validation helpers для single-active, lifecycle/support-boundary и promotion gates.
7. Добавить docs/tests, доказывающие, что candidate profiles не трактуются как `supported_bundled`.

## Verification

Рекомендуемые проверки по мере появления runtime-реализации:

```powershell
./gradlew buildIntegrity
```

Для docs-only изменений:

```powershell
git diff --check
```

Для runtime validation slices сначала запускать focused module tests, затем `buildIntegrity`.

## Repo-Local Cutover / Reindex Runbooks

Эти runbooks фиксируют engineering contract для будущего cutover. Они не запускают live-server/stage/prod операции и не требуют customer secrets.

### PG / Relational DB

1. Preflight: проверить profile pack, JDBC URL redaction, Flyway validate, required extensions, encoding/timezone/collation.
2. Checkpoint: зафиксировать `backup_id`, `flyway_version`, `wal_lsn`, rollback profile и watch window.
3. Shadow target: восстановить backup в lab/QEMU target и прогнать representative query smoke.
4. Validation: `MigrationCheckpointValidator` должен пройти, затем H2/repository tests и Flyway validation.
5. Rollback: старый primary остаётся rollback profile до закрытия watch window; silent fallback запрещён.

### S3 / Object Storage

1. Preflight: проверить bucket policy, multipart, checksum, lifecycle/object-lock совместимость с retention.
2. Checkpoint: зафиксировать `inventory_time`, `object_cursor`, `checksum_manifest`.
3. Shadow target: копировать sample prefix и retention snapshot в test bucket.
4. Validation: sample reads, checksum diff и deep-archive reader smoke.
5. Rollback: old bucket read-only до watch window; purge без подтверждённого snapshot запрещён.

### NATS / JetStream

1. Preflight: subject prefix contract, auth/TLS, queue groups, max payload, drain behavior.
2. Checkpoint: зафиксировать `stream_sequence` и `consumer_offset`.
3. Shadow target: mirror/replay в lab stream, workers paused или drained.
4. Validation: subject contract tests, fan-out tests, replay/ack smoke.
5. Rollback: вернуть endpoint и offsets; silent bridge to Kafka запрещён.

### IdP / OIDC

1. Preflight: issuer/JWKS TLS, audience/issuer/clock skew, required claims mapping.
2. Checkpoint: realm export revision или claim mapping revision, rollback issuer, token cache watch window.
3. Shadow target: validate tokens from test realm/client without admin hot path changes.
4. Validation: negative token tests and admin/user role mapping checks.
5. Rollback: restore previous issuer/client mapping; auth fail-open запрещён.

### Search Reindex

1. Preflight: selected backend binding, ACL filtering contract, schema/version compatibility.
2. Checkpoint: зафиксировать `reindex_cursor`, `index_schema_version`, `shadow_target`.
3. Shadow target: build shadow index and compare query samples against SQL/Solr baseline.
4. Validation: search contract tests, membership/ACL filtering, versioned cursor resume.
5. Rollback: keep previous backend as rollback profile until watch window; silent fallback allowed only as explicitly reported degraded SQL search.

`MigrationCheckpointValidator.report(...)` возвращает structured report для UI/automation: component, severity, missing markers, rollback readiness и no-silent-fallback flag. Старый `validate(...)` сохранён как компактный pass/fail contract.

## Runtime Status Polish

- `/api/v1/platform/external-stack/status` показывает desired/observed connector, bounded `health_status`, `degraded_reason`, validation failures/warnings and redacted endpoint metadata.
- `/api/v1/platform/external-stack/profiles` дополняет profile lifecycle данными compatibility pack: required checks, promotion evidence and unsupported modes.
- `/api/v1/platform/external-stack/compatibility-packs` возвращает полный catalog supported/external/candidate packs, включая profiles, которых нет в текущем runtime manifest; `/compatibility-packs/{profileId}` возвращает один pack.
- `/api/v1/platform/external-stack/status/{component}` возвращает один component status для drill-down/API automation.
- `/api/v1/platform/external-stack/component-contracts` и `/component-contracts/{component}` возвращают read-only catalog required checks/failure policies для repo-local gates и Admin UI.
- `/api/v1/platform/external-stack/catalog-health` возвращает drift report по component/profile counts, candidate count, failure/warning counts, failures, warnings and `remediation_actions`.
- `/api/v1/platform/external-stack/cutover/readiness` возвращает repo-local lab readiness для dry-run cutover: severity, blockers/warnings, smoke command and merged remediation actions. Это не live-server cutover.
- `/api/v1/platform/external-stack/component-profile-summary` и `/component-profile-summary/{component}` возвращают readiness summary по профилям component: supported/candidate/rejected counts, `readiness_severity`, promotion warning and `remediation_actions`.
- Compatibility pack catalog загружается из `docs/external-stack-profiles.yaml` и упаковывается в core-api resources; aliases, promotion evidence and unsupported modes живут в YAML и проверяются repo-local gate.
- `POST /api/v1/platform/external-stack/preflight/manifests` принимает `{ "manifests": [...] }` and returns manifest `ValidationResult` for deploy-generated or hand-authored desired state before applying it. Validation checks single-active, role traffic, endpoint redaction, unknown compatibility profiles, profile/component mismatch and candidate active usage; active external/BYO profiles add warnings for support-boundary evidence and unsupported modes. Active manifests also warn when `capabilities` do not provide evidence for all component contract required checks.
- `POST /api/v1/platform/external-stack/preflight/manifests/report` возвращает explain report: severity (`ok`/`warning`/`blocked`), `failure_count`, `warning_count`, `missing_required_check_count`, `remediation_actions`, original validation, per-component manifest/active counts, failures/warnings, structured `missing_required_checks` and redacted endpoint metadata.
- `POST /api/v1/platform/external-stack/preflight/checkpoint` принимает `MigrationCheckpoint` JSON и возвращает structured report без запуска live cutover.
- `POST /api/v1/platform/external-stack/preflight/profile` проверяет один `profile_id` на production support и возвращает redacted `ValidationResult`.
- `POST /api/v1/platform/external-stack/preflight/profile/report` принимает `{ "profile_id": "...", "evidence": [...] }` and returns profile evidence readiness: severity, missing promotion evidence/unsupported-mode counts, `remediation_actions` and unsupported modes.
- Admin panel показывает catalog health remediation counters, component profile readiness severity/remediation, groups/lifecycle filters, support badges, desired/observed diff, downloadable JSON report, compatibility pack catalog, component validation contracts and drill-down; raw JSON остаётся ниже для диагностики.
- Admin panel содержит repo-local Manifest/Checkpoint/Profile preflight forms с sample JSON selector, profile evidence report and copy-curl buttons.
- `scripts/smoke-external-stack-lab-cutover.ps1` остаётся preflight-only smoke для QEMU/local API: checkpoint + profile report без переключения runtime endpoint.
- Search Provider SPI покрывает текущие SQL/Solr paths. OpenSearch/Elasticsearch представлены disabled candidate backends с `describe()/status()` metadata, guard against primary binding и без live client dependency.
- Product Modules capabilities возвращают `external_stack_components` / `external_stack_profiles` / `external_stack_warnings` для base и add-ons, чтобы UI/ops видели backend dependencies и degradation boundary.
- Repo-local catalog drift gates проверяют, что `default_profile` существует в profile map и Product Modules не ссылаются на отсутствующие components/profiles.

## Completion Gate

Нельзя считать feature завершённой, пока не закрыты или явно approved-deferred:

- все `T023-*` tasks;
- polish pass по spec/tasks/contracts/docs;
- self-review и runtime code review/security-style review при изменениях кода;
- refactoring pass для новых runtime зон;
- performance analysis для validation path;
- memory analysis для catalog/manifest/validation/checkpoint данных;
- thread-safety analysis для validators/cache/status readers;
- UI/admin usability review при изменении Product Modules/Admin status/degraded messages;
- focused tests и финальный `./gradlew buildIntegrity`, кроме docs-only изменений с явной пометкой.

## Out Of Scope

- Live-server TLS/load/stage gates до сентября 2026.
- Развёртывание third-party RF candidates через Korus scripts, пока отдельная задача явно не повысит profile.
- Direct Kafka replacement для NATS semantics.
- Direct VKS replacement для LiveKit без отдельного integration API spike.
