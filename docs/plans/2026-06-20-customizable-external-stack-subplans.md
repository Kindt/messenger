# Customizable External Stack Subplans

Дата: 2026-06-20
Статус: design draft
Связано: [`external-stack-profiles.yaml`](../external-stack-profiles.yaml), [`DEV_STACK_PROFILES.md`](../DEV_STACK_PROFILES.md), [`NATS_SUBJECTS_INTEROP.md`](../NATS_SUBJECTS_INTEROP.md), [`RETENTION_AND_DEEP_ARCHIVE.md`](../RETENTION_AND_DEEP_ARCHIVE.md)

## Цель

Перевести внешний стек Korus Messenger из модели «фиксированные контейнеры в коробке» в модель профилей:

- `supported bundled` — Korus поставляет и валидирует сервис;
- `supported external/BYO` — Korus поддерживает коннектор и validation, а заказчик/вендор владеет сервисом;
- `candidate` — есть основания для поддержки, но нужны spike, legal/security/vendor checks;
- `integration candidate` — внешний продукт может интегрироваться с Korus, но не заменяет backend напрямую;
- `rejected` — явно не подходит.

Общий инвариант: в одном Cell для каждого компонента существует ровно один `role=active`. `standby`, `migration_source` и `migration_target` допустимы только без пользовательского traffic.

## Общий Manifest

Каждый компонент описывается `ComponentBackendManifest`:

```yaml
component: relational-db-hot
backend_family: postgres
connector: postgres-16
version: "16"
role: active
endpoint: jdbc:postgresql://postgres-hot:5432/avandocmsg_hot
resource_name_or_alias: avandocmsg_hot
schema_or_protocol_version: flyway-current
compatibility_profile: postgres-16-default
topology: single-node
config_revision: "deploy-generated"
capabilities:
  - flyway
  - jdbc
data_classification: hot-personal-data
support_boundary: korus-managed-lab-or-boxed
```

`desired manifest` создаётся deploy registry/config artifact. Runtime публикует `observed manifest` в admin status, но не меняет desired state самовольно.

## Runtime Scaffold

Текущая реализационная волна добавляет read-only contract слой в `modules/core-api/.../platform/stack`: manifest/profile DTO, single-active и promotion validation, secret redaction, component validation contracts, migration checkpoints и policy checks. Публичная форма status уже закреплена endpoint-ами `GET /api/v1/platform/external-stack/status` и `GET /api/v1/platform/external-stack/profiles`; фактический deploy/runtime source manifests подключается отдельной волной без silent fallback.

## Repo-Local Cutover Contract

Каждый stateful cutover/reindex runbook фиксируется как engineering contract, а не как live-server инструкция: preflight, checkpoint, shadow target, validation, rollback, no-silent-fallback. Минимальные marker groups: PG `backup_id`/`flyway_version`/`wal_lsn`; S3 `inventory_time`/`object_cursor`/`checksum_manifest`; NATS `stream_sequence`/`consumer_offset`; search `reindex_cursor`/`index_schema_version`/`shadow_target`; IdP realm export or claim mapping revision plus rollback issuer/watch window.

Live stage/prod cutover, real vault/customer secrets and human sign-off are deferred to the live-server ops registry. Repo-local validation is limited to unit/H2 tests, lab/QEMU smokes when available, generated manifests and docs/contracts.

## 1. PG / Relational Storage

### Scope

- `postgres-hot`: чаты, сообщения, пользователи, организации, audit, retention metadata.
- `postgres-archive`: archive DB add-on.
- Read replica, HA, backup/restore, Flyway.

### Profiles

- `postgres-16-bundled`: текущий контейнерный default.
- `postgres-16-external`: customer-managed PostgreSQL.
- `postgres-pro-*`: RF external/BYO или bundled candidate после legal/support/deploy gate.
- `tantor-*`, `arenadata-postgres-*`, `jatoba-*`, `pangolin-*`: RF candidates; не supported до SQL/Flyway/backup validation.

### Validation

- Version allow-list.
- Encoding, timezone, collation.
- Required extensions.
- Flyway privileges: create/alter/index/lock rights.
- Max connections vs pools across `core-api`, workers, `ws-gateway`, and Keycloak if shared.
- Migration lock timeout policy.
- Read replica lag threshold if enabled.

### Migration / Cutover

- Authoritative source: current primary backup/snapshot.
- Checkpoint: backup ID, Flyway version, WAL/LSN if replication is used.
- Dry-run: connectivity, Flyway validate, representative query smoke.
- Cutover: maintenance window, stop writers or drain, restore/sync target, validate, switch JDBC endpoint, start services.
- Rollback: previous primary remains standby until watch window closes.

### Degradation

- Hot DB down: messaging core unavailable; fail closed.
- Archive DB down: archive/export/deep-retention features degrade or return `503`; hot path stays up.
- Read replica down: fallback to primary only if explicitly allowed and capacity preflight passes.

### Tests

- Flyway validate/migrate on every supported profile.
- H2 remains unit/repository baseline, not compatibility proof.
- Integration tests for SQL predicates, retention/archive, export readers.
- Restore drill contract for supported bundled/external profiles.

## 2. Object Storage / MinIO / S3

### Scope

- File uploads/downloads.
- Public file links.
- Retention snapshots.
- Deep archive chunks.
- Future export artifacts if moved to object storage.

### Profiles

- `s3-minio-bundled`: current default.
- `s3-compatible-external`: customer-managed S3 endpoint.
- RF candidates: `yadro-tatlin-object`, `pc-storage-s3`, `pc-depot`, `vk-cloud-storage`, other S3-compatible RF providers.

### Validation

- Endpoint, TLS, auth credentials.
- Bucket exists or can be created, depending on deployment mode.
- `put/get/head/delete/list` smoke.
- Multipart upload smoke for large snapshots/chunks.
- Checksum support.
- Path-style vs virtual-hosted-style.
- Lifecycle/object-lock policy does not violate Korus retention.

### Migration / Cutover

- Authoritative source: current bucket inventory.
- Checkpoint: inventory generation time, object-key cursor, checksum manifest.
- Migration copies objects with checksum verification and resumable cursor.
- Cutover switches bucket/endpoint only after sample reads and retention snapshot reads pass.
- Rollback keeps old bucket read-only until watch window closes.

### Degradation

- Uploads fail with controlled error.
- Existing downloads may show “file temporarily unavailable”.
- Retention/deep-archive queues or pauses; never purge hot body unless snapshot/write succeeded.

### Tests

- S3 contract suite: `put/get/head/delete/list`, multipart, prefix listing, checksum, retry.
- Retention/deep-archive tests against mock S3 and one live non-host runtime profile.
- Migration dry-run and checksum verification tests.

## 3. NATS / Messaging

### Scope

- `msg.event.*`, `msg.deliver.*`, `rtc.signal`.
- Worker events: retention, export, indexer, push, preview.
- Optional JetStream mode.

### Profiles

- `nats-2.10-bundled`: current default.
- `nats-2.x-external`: customer-managed NATS/JetStream.
- `kafka-bridge-candidate`: adapter/bridge only, not a direct replacement.
- `arenadata-streaming-kafka`: RF integration/bridge candidate, not NATS drop-in.

### Validation

- Connect, auth, TLS.
- Publish/subscribe smoke per required subject prefix.
- Queue group behavior.
- JetStream availability if profile requires it.
- Max payload.
- Reconnect and drain behavior.

### Migration / Cutover

- Core NATS without persisted streams: maintenance/drain window, switch endpoint, smoke, resume workers.
- JetStream: checkpoint stream sequence/consumer offsets, mirror or replay if needed.
- Kafka bridge: separate product feature with changed semantics.

### Degradation

- Messaging down: workers pause, fan-out degrades, indexing/export/retention events lag.
- Core send path must either persist DB first and queue later or return controlled error, depending on domain path.
- Backpressure: bounded publish retries, lag metrics, DLQ/parking where applicable.

### Tests

- Subject contract tests.
- Fan-out and queue group tests.
- Reconnect/drain tests.
- JetStream ack/replay tests if enabled.

## 4. Auth / IdP

### Scope

- Keycloak issuer/JWKS.
- Login/token validation.
- Role/group/org mapping.
- Admin realm bootstrap if Korus-managed.
- Generic OIDC and AD/LDAP boundary.

### Profiles

- `keycloak-24-bundled`: current default.
- `keycloak-external`: customer-managed Keycloak realm.
- `oidc-generic`: token validation only; no realm management.
- `blitz-idp`: RF candidate for OIDC/SAML/MFA after claims/protocol spike.

### Validation

- Issuer/JWKS reachable over TLS.
- Token signature, audience, issuer, clock skew.
- Required claims for user ID, org, roles.
- Admin API availability only for managed Keycloak profiles.
- Logout, refresh and session behavior documented.

### Migration / Cutover

- Bundled to external Keycloak: export realm/client mapping, customer imports or maps roles, validate tokens, switch issuer.
- Generic OIDC: no realm migration; Korus maps claims to local authorization model.
- Rollback must respect token/session lifetime and cached JWKS.

### Degradation

- Fail-open forbidden.
- New auth fails if IdP/JWKS unavailable and cache invalid.
- Existing sessions may continue only if token validation cache policy explicitly allows it.
- Admin status distinguishes login outage from authorization mapping failure.

### Tests

- Token validation with JWKS rotation.
- Claims mapping.
- Negative OIDC cases: missing audience, missing role, wrong issuer.
- External Keycloak smoke with test realm.

## 5. Redis / Cache / Rate Limit

### Scope

- Auth rate limiter.
- Read cache.
- Future presence/session cache.

### Profiles

- `redis-7-bundled`: current default.
- `redis-compatible-external`: customer-managed Redis-compatible endpoint.
- `valkey-*`: preferred open Redis-protocol candidate.
- `keydb-*`: multithreaded Redis-compatible candidate.
- `dragonfly-*`: high-performance candidate; check license/command coverage.
- `tarantool-*`: RF integration candidate only with proven compatibility layer.

### Validation

- Ping, auth, TLS.
- Required command subset: `GET`, `SET`, `DEL`, `EXPIRE`, counters, TTL.
- Key-prefix isolation.
- Cluster/Sentinel unsupported unless explicitly added.

### Degradation

- Read cache fail-open.
- Auth rate-limit policy profile-specific: dev fail-open, regulated production fail-closed or controlled degraded.
- Cache loss must not lose authoritative data.

### Tests

- Command subset contract tests.
- Rate-limit fail-open/fail-closed policy tests.
- TTL behavior tests.

## 6. Web / LB / TLS

### Scope

- `korus-web` static/proxy.
- External reverse proxy/LB.
- TLS termination.
- WebSocket upgrade and upload limits.

### Profiles

- `nginx-bundled` / current `korus-web`.
- `external-lb`: customer-managed nginx/HAProxy/ingress/WAF.
- `angie` / `angie-pro`: RF nginx-compatible candidate.
- `angie-adc` / `angie-ingress`: enterprise/Kubernetes candidates.

### Validation

- Health endpoints and routing.
- WebSocket upgrade.
- Upload/download size limits.
- `X-Forwarded-*` and public URL correctness.
- TLS cert chain, HSTS/CSP/security headers.

### Degradation

- Edge down: app unavailable.
- Misconfigured WebSocket: chat may load but realtime fails.
- Misconfigured upload limit: file upload fails without affecting text chat.

### Tests

- HTTP routing smoke.
- WebSocket smoke.
- Large upload smoke.
- Security headers check.

## 7. Live / Media / STUN/TURN

### Scope

- LiveKit SFU add-on.
- Jitsi URL integration.
- STUN/TURN for WebRTC.
- Future streaming/ingress.

### Profiles

- `livekit-1.8-bundled`: current dev/full-server profile.
- `livekit-external`: customer-managed LiveKit.
- `coturn-external` / `coturn-bundled-candidate`: standard TURN/STUN.
- RF integration candidates: `clevermeet`, `iva-mcu`, `iva-one`, `videomost`, `kontur-talk`, `vinteo`.

### Validation

- Token issue smoke.
- Room create/join smoke if LiveKit-compatible.
- TURN credentials and relay reachability.
- UDP/TCP/TLS ports.
- RF VKS products require API spike; not LiveKit drop-in by default.

### Degradation

- Calls hidden/disabled if media unavailable.
- Chat/message core unaffected.
- TURN unavailable may degrade connectivity only for restricted networks.

### Tests

- Unit token tests.
- API smoke for LiveKit-compatible profiles.
- Browser/WebRTC smoke in QEMU/live stack only when runtime is available.

## 8. Push / Notifications

### Scope

- Web Push/VAPID.
- Push worker.
- Future mobile/internal notification gateways.

### Profiles

- `webpush-vapid-bundled-config`: current browser/PWA path.
- `external-notification-gateway`: future candidate.
- RF/SMS/push gateways: integration candidates only.

### Validation

- VAPID key pair.
- VAPID subject ownership.
- Gateway auth/TLS if external.

### Degradation

- Push is best-effort.
- Messaging delivery must not depend on push.
- UI can show push disabled/unavailable.

### Tests

- VAPID config validation.
- Push worker event handling.
- Non-blocking behavior when gateway unavailable.

## 9. Integrations / DLP / Bots

### Scope

- Webhooks and integration profiles.
- DLP scanning.
- Bot delivery/API.
- External L1-L3 plugins.

### Profiles

- `http-webhook-generic`.
- `icap-dlp-generic`: baseline DLP protocol.
- RF DLP candidates: `infowatch`, `searchinform`, `solar-dozor` via ICAP/vendor API.
- `bot-api-internal`, future `bot-api-external-gateway`.

### Validation

- Endpoint, auth, TLS.
- Timeout and verdict mapping.
- File/message payload limits.
- Tenant/org policy mapping.
- E2EE compatibility statement.

### Degradation

- DLP fail-open/fail-closed must be org/tenant policy.
- Synchronous DLP can block send path; async mode needs quarantine/audit.
- Integration outage must not break core messaging unless policy requires blocking.

### Tests

- ICAP/vendor API mock tests.
- Timeout/fail-policy tests.
- E2EE/plaintext boundary tests.
- Audit event tests.

## Cross-Plan Acceptance

- Each component has target profiles and lifecycle statuses.
- Each profile has support boundary and impact model.
- Stateful components have migration/backup/rollback story.
- Security-critical components have explicit fail-open/fail-closed policy.
- External/BYO profiles do not imply Korus deployment ownership.
- Product Modules/Admin status/deploy validation can represent every active profile.

## Implementation Notes

1. `docs/external-stack-profiles.yaml` is the initial design-time catalog.
2. Runtime implementation should not parse this document directly until a schema and validation task is created.
3. The first implementation wave should add read-only manifest/admin status before enabling any external cutover behavior.
4. Search remains the reference implementation for connector depth and migration controls.
