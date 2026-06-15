# Tasks: Infrastructure Optimization (FR-OPT)

**Input**: `specs/006-infra-optimization/`  
**Design**: [`docs/plans/2026-06-15-infra-optimization-design.md`](../../docs/plans/2026-06-15-infra-optimization-design.md)

## Format: `[ID] [P?] [Story] Description`

---

## Phase 0: Spec-Kit (complete)

- [x] T001 Create spec.md, plan.md, research.md, data-model.md
- [x] T002 Create contracts/, quickstart.md, checklists/requirements.md
- [x] T003 Create tasks.md

---

## Phase 1: Wave 1 — Pilot compose + Keycloak prod (US1, US2) **COMPLETE**

### Compose & scripts

- [x] T101 [US1] Create `docker/docker-compose.pilot.yml`
- [x] T102 [US1] Create `docker/docker-compose.keycloak-prod.yml` + `docker/Dockerfile.keycloak-prod`
- [x] T103 [US1] Create `scripts/pilot-stack-up.sh`
- [x] T104 [US1] Create `scripts/smoke-pilot-stack.sh`
- [x] T105 [P] [US1] Add entry to `scripts/SMOKE_INDEX.md`

### Ansible & docs

- [x] T106 [US1] `korus_deploy_profile` in Ansible; `korus_server` role wires pilot vs full-stack
- [x] T107 [US1] Update `deploy/qemu/RESOURCES.md` — Pilot sizing
- [x] T108 [US2] Keycloak dev vs prod in RESOURCES.md + TZ §10.2.1 footnote

### Optional (Wave 1 stretch)

- [x] T109 [P] [US1] `SEARCH_MODE=sql|solr` in AppConfig

### Verification gate (Wave 1)

- [x] T110 [US1] `./gradlew buildIntegrity` green on host
- [x] T111 [US1] `smoke-pilot-stack.sh` green (QEMU server guest)
- [x] T112 [US1] `smoke-auth.sh` green
- [x] T113 [US1] `playwright-dev-loop.ps1 -Tier api` green
- [x] T114 [US1] RAM guest documented in RESOURCES.md (~2 GiB used / 9.7 GiB)

---

## Phase 2: Wave 2 — Cache + scale + replica (US3, US4, US5) **COMPLETE**

- [x] T201 [US3] Hex `ReadCachePort` + `RedisReadCacheAdapter`
- [x] T202 [US3] WS invalidation hooks; Prometheus cache hit/miss metrics
- [x] T203 [US4] `docker/docker-compose.scale.yml`
- [x] T204 [US4] NATS queue group verification; load script for msg/s
- [x] T205 [US5] Read replica compose + routing
- [x] T206 [US5] `scripts/smoke-messaging-e2e.sh` under load

### Verification gate (Wave 2)

- [x] T207 [US4] Guest smokes on QEMU: `scale-stack-up`, `verify-nats-queue-group`, `load-message-pipeline`, `smoke-messaging-e2e --load-rounds 3`

## Phase 3: Wave 3 — zstd deep-archive + batch Solr + Wave 2 closure **COMPLETE**

### zstd deep-archive (stage 6)

- [x] T301 `SnapshotPartCodec` + `SnapshotCompression` (KDA1 + zstd/gzip)
- [x] T302 `ChunkedSnapshotWriter` / `DeepArchiveReader` compress+decompress
- [x] T303 `deep_archive_bytes_saved_total` metric; env `DEEP_ARCHIVE_COMPRESSION`

### Batch Solr indexing (stage 7)

- [x] T304 `IndexerBatchBuffer` + env `INDEXER_BATCH_SIZE` / `INDEXER_BATCH_FLUSH_MS`
- [x] T305 Batch metrics `indexer_batch_flush_total`

### Wave 2 debts

- [x] T306 NATS `msg.cache.invalidate` (pipeline publish + core-api subscriber)
- [x] T307 `ChatService` chat-list invalidation on create/join/leave
- [x] T308 `MessageRepository.findById` read routing; `replica-stack-up.sh`
- [x] T309 Ansible `enterprise` profile + `enterprise-stack-up.sh`
- [x] T310 `nginx.conf.scale.template` (ip_hash WS sticky)

---
