# External Stack Validation Contract

## Global Rules

- Production config must be explicit. Auto-selection is allowed only for dev/lab profiles and must fail on ambiguity.
- One `role=active` manifest is allowed per component per Cell.
- `standby`, `migration_source` and `migration_target` profiles must not serve user traffic.
- Silent fallback is forbidden when it changes security, consistency, retention or cost.
- Candidate profiles must be visible as candidates and must not be marketed as `supported_bundled` options.
- Desired manifest, observed manifest, health status and degraded reason must be represented separately.
- Validation/admin/deploy outputs must redact credentials, tokens, private keys and secret-bearing URLs.
- Compatibility pack catalog must be data-driven from the external stack profile catalog and must keep candidates visible without promoting them.
- Product module capability output must expose backend component/profile requirements so add-on degradation can be tied back to external stack state.
- Profile aliases, promotion evidence and unsupported modes must be validated from the YAML catalog, not hidden in Java-only defaults.
- Search candidate backends may be described and displayed, but must be rejected if configured as the primary production search backend.

## Required Validation By Component

| Component | Required Checks | Failure Policy |
|-----------|-----------------|----------------|
| `relational-db-hot` | JDBC connectivity, version allow-list, encoding/timezone/collation, Flyway privileges, extension requirements, pool sizing, lock timeout | fail closed |
| `relational-db-archive` | JDBC connectivity, schema/Flyway compatibility, archive query smoke, restore drill contract | archive/export degrade |
| `object-storage` | TLS/auth, bucket policy, put/get/head/delete/list, multipart, checksum, lifecycle/object-lock | uploads controlled error; no purge without snapshot |
| `messaging` | auth/TLS, publish/subscribe by subject prefix, queue groups, JetStream if required, max payload, reconnect/drain | workers pause/lag; no silent fallback |
| `idp` | issuer/JWKS TLS, token signature, audience, issuer, clock skew, required claims, admin API only for managed Keycloak | fail closed |
| `cache` | ping/auth/TLS, command subset, TTL, key prefix, cluster/sentinel support flag | read cache fail-open; rate-limit by policy |
| `web-edge` | routing, WebSocket upgrade, upload limits, forwarded headers, TLS chain, CSP/security headers | app/realtime degraded |
| `media` | token issue, room create/join if LiveKit-compatible, ports, VKS API spike for RF candidates | calls disabled; chat core unaffected |
| `turn` | realm/secret, relay reachability, UDP/TCP ports | restricted networks may fail calls |
| `notifications` | VAPID/gateway config, auth/TLS, retry boundary | best effort |
| `dlp` | endpoint/auth/TLS, verdict schema, timeout, payload limits, tenant policy, E2EE boundary | tenant/org fail policy |
| `integrations` | endpoint/auth/TLS, timeout, payload, tenant mapping, retry/audit | integration delivery degraded |
| `bots` | endpoint/auth/TLS, event schema, retry timeout | bot delivery degraded |

## Migration Checkpoints

Stateful migrations must include:

- source profile;
- target profile;
- checkpoint type/value;
- dry-run result;
- validation result;
- rollback profile;
- watch window;
- support boundary.

Minimum checkpoint examples:

- PG: `backup_id`, `flyway_version`, `wal_lsn`.
- S3: `inventory_time`, `object_cursor`, `checksum_manifest`.
- NATS JetStream: `stream_sequence` and `consumer_offset`.
- IdP: realm export revision or claim mapping revision, rollback issuer and token cache watch window.
- Search reindex: `reindex_cursor`, `index_schema_version`, `shadow_target`.

Cutover runbooks must always include preflight, checkpoint, shadow target, validation, rollback and explicit no-silent-fallback policy. Live-server execution, real vault secrets and human sign-off remain outside this repo-local contract until stage/prod becomes available.

Checkpoint automation SHOULD expose a structured report with:

- component;
- pass/fail and severity;
- missing marker keys;
- rollback readiness;
- no-silent-fallback flag;
- redacted validation failures suitable for admin/status display.

Repo-local API:

- `GET /api/v1/platform/external-stack/compatibility-packs`
  - Output: full connector compatibility pack catalog keyed by `profile_id`
  - Constraint: candidate packs remain candidate/integration candidate and must not be presented as supported bundled.
- `GET /api/v1/platform/external-stack/compatibility-packs/{profileId}`
  - Output: one compatibility pack by profile id, including YAML aliases.
  - Constraint: unknown profiles return not-found semantics.
- `GET /api/v1/platform/external-stack/status/{component}`
  - Output: one component status row with desired/observed connector, health, validation and support boundary.
  - Constraint: no secret-bearing endpoint output.
- `GET /api/v1/platform/external-stack/component-contracts`
  - Output: component validation contract catalog keyed by component id.
  - Constraint: read-only contract surface; no runtime probes or endpoint switches.
- `GET /api/v1/platform/external-stack/component-contracts/{component}`
  - Output: one component contract with required checks and failure policy.
  - Constraint: unknown component contracts return not-found semantics.
- `GET /api/v1/platform/external-stack/catalog-health`
  - Output: catalog drift report with counts, failures and candidate warnings.
  - Constraint: report is read-only and must not hide candidate profiles.
- `GET /api/v1/platform/external-stack/component-profile-summary`
  - Output: readiness summary by component with supported/candidate/rejected counts.
  - Constraint: candidate counts remain visible and are not treated as supported capacity.
- `GET /api/v1/platform/external-stack/component-profile-summary/{component}`
  - Output: one component readiness summary.
  - Constraint: unknown components return not-found semantics.
- `POST /api/v1/platform/external-stack/preflight/manifests`
  - Input: `{ "manifests": [ComponentBackendManifest...] }`
  - Output: manifest `ValidationResult`
  - Constraint: validates desired state only; no deploy, no secret exposure, no runtime endpoint switch. Unknown profile ids, component/profile mismatch and active candidate profiles fail validation.
- `POST /api/v1/platform/external-stack/preflight/manifests/report`
  - Input: `{ "manifests": [ComponentBackendManifest...] }`
  - Output: manifest explain report with severity (`ok`, `warning`, `blocked`), totals, `remediation_actions` and per-component summaries including `missing_required_checks`.
  - Constraint: uses redacted metadata only; no endpoint switch.
- `POST /api/v1/platform/external-stack/preflight/checkpoint`
  - Input: `MigrationCheckpoint`
  - Output: structured checkpoint report
  - Constraint: no live-server side effects, no customer secrets, no endpoint switch.
- `POST /api/v1/platform/external-stack/preflight/profile`
  - Input: `{ "profile_id": "..." }`
  - Output: redacted `ValidationResult`
  - Constraint: candidate/integration candidate profiles fail production preflight until explicitly promoted.
- `POST /api/v1/platform/external-stack/preflight/profile/report`
  - Input: `{ "profile_id": "...", "evidence": [...] }`
  - Output: profile evidence readiness report with severity, missing promotion evidence/unsupported-mode counts, remediation actions and unsupported modes.
  - Constraint: supported profiles may pass with `warning` severity when evidence is incomplete; candidate profiles remain blocked.

## Acceptance

Validation is accepted when:

1. Every active component manifest passes its required checks or is explicitly degraded by policy.
2. Security-critical components have no implicit fail-open path.
3. Stateful migration targets have checkpoint and rollback data before cutover.
4. `external_byo` and `managed_by_customer` profiles state customer/vendor ownership.
5. Candidate/RF profiles remain labeled as candidate or integration candidate until promoted by tests, support boundary and legal/security gates.
6. Supported profiles include an impact model for performance, resilience, resources, price/TCO and administration.
7. Product Modules catalog has no dangling external stack component/profile references.
8. Capabilities output includes warnings when enabled/degraded add-ons require degraded external stack components.
9. Every catalog component default profile is represented by an explicit profile entry, including optional disabled/none states.
10. Every compatibility profile referenced by a desired manifest resolves to the same component and is production-supported before active traffic is allowed.
11. Active external/BYO manifests warn about customer support-boundary evidence and unsupported modes even when validation passes.
12. Active manifests warn when they do not provide evidence for all component contract required checks.
13. Preflight report severity is `blocked` for failures, `warning` for warning-only evidence gaps and `ok` only when no failures/warnings remain.
14. Profile evidence preflight must expose missing promotion evidence and unsupported modes without promoting candidate profiles.
15. Manifest preflight report must expose deterministic `remediation_actions` for single-active, serve-traffic, profile mismatch/support and missing evidence failures.
16. Profile evidence preflight report must expose deterministic `remediation_actions` for unsupported profiles, missing promotion evidence and unsupported modes.
