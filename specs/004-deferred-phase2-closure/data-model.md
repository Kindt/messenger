# Data Model: Deferred Phase 2 Post-Backlog Closure

**Date**: 2026-06-09

## DeployTarget + TlsConfig (US1)

| Field | Type | Notes |
|-------|------|-------|
| inventory_group | string | `stage`, `prod`, `local` |
| korus_tls_enabled | boolean | gates tls role |
| korus_tls_domain | string | cert CN / server_name |
| korus_tls_use_letsencrypt | boolean | certbot vs BYO paths |
| vault_secrets | map | db, minio, keycloak, jwt, coturn |

**Relationships**: DeployTarget → env templates (`korus-server.env`, `korus-web.env`)

## HexWritePort contracts (US2–US3)

### UserRepositoryPort (write extension)

| Method | Params | Returns |
|--------|--------|---------|
| updateProfile | userId, displayName, phone | boolean |
| updatePresence | userId, status | boolean |
| updatePrivacy | userId, settings | boolean |
| touchHeartbeat | userId | void |

### OrganizationRepositoryPort (write extension)

| Method | Params | Returns |
|--------|--------|---------|
| listAll | limit | List\<Organization\> |
| create | name, retentionPolicy | OrganizationId |
| deleteIfUnused | orgId | boolean |
| setUserOrg | userId, orgId | boolean |

### FileMetadataPort + ObjectStoragePort (US2)

| Port | Method | Notes |
|------|--------|-------|
| FileMetadataPort | insert, delete | JDBC adapter |
| ObjectStoragePort | put, get, delete | MinIO adapter |

### PublicLinkPort (US3)

| Method | Notes |
|--------|-------|
| createLink | fileId, ownerId, expiry |
| revokeLink | linkId |
| listByFile | fileId, ownerId |

### SavedChatPort (US3)

| Method | Notes |
|--------|-------|
| getSavedChatId | userId |
| setSavedChatId | userId, chatId |

## ProfilingTarget (US4)

| Field | Example |
|-------|---------|
| worker_module | message-pipeline |
| dockerfile | Dockerfile.message-pipeline.profiling |
| compose_service | message-pipeline |
| jfr_output | profiling/message-pipeline-*.jfr |

## PlaywrightScenario (US5)

| Field | Example |
|-------|---------|
| spec_file | files-export.spec.ts |
| parity_row | files, export |
| gate_status | pass / waiver / skip-documented |

## MlsGroupState (US7)

Existing table `mls_group_state`; extensions may add columns per spike.

| Concept | Notes |
|---------|-------|
| epoch | bumps on membership change |
| key_packages | per device RFC 9420 |
| wire_types | welcome, commit, application |

## GovernanceSignoff (US6)

| Field | Example |
|-------|---------|
| role | Architecture Owner |
| name | (real person) |
| decision | Accepted |
| date | ISO date |
