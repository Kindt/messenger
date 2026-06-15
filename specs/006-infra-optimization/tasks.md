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

## Phase 2: Wave 2 — Cache + scale + replica (US3, US4, US5)

- [x] T201 [US3] Hex `ReadCachePort` + `RedisReadCacheAdapter`
- [ ] T202 [US3] WS invalidation hooks; Prometheus cache hit/miss metrics
- [ ] T203 [US4] `docker/docker-compose.scale.yml`
- [ ] T204 [US4] NATS queue group verification; load script for msg/s
- [ ] T205 [US5] Read replica compose + routing
- [ ] T206 [US5] `scripts/smoke-messaging-e2e.sh` under load

---

## Phase 3–5

See design doc and prior tasks.md revision for Waves 3–4 and docs sync (T301–T503).
