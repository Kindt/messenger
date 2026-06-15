# Tasks: Repository Cleanup (008)



## Phase 0 — Hygiene



- [x] T001 `.gitignore` `scripts/__pycache__/`, `*.pyc`

- [x] T002 Delete 7 orphan one-shot Python patch scripts

- [x] T003 Delete `scripts/qemu-dev-clean-up.ps1`



## Phase 1 — Specs & docs



- [x] T101 Migrate living docs: `docs/review/ops-signoff-log.md`, `docs/contracts/`, `docs/parity/`, `deploy/ansible/DEPLOY_QUICKSTART.md`

- [x] T102 Archive specs 001–006 → `specs/archive/` + stub READMEs

- [x] T103 Update inbound links (`AGENTS.md`, README, CHANGELOG, Ansible, SMOKE_INDEX)

- [x] T104 Archive `docs/review/` May-2025 snapshots → `docs/review/archive/2025-05/`

- [x] T105 Sync ROADMAP §8, CHANGELOG dead links, `feature.json` → 008



## Phase 2 — Scripts



- [x] T201 Remove all `scripts/*.cmd` after reference migration

- [x] T202 Deprecate thin smoke wrappers: removed `with-file-flow.ps1` alias; Windows `.ps1` kept as operator canonical (`.sh` for CI)

- [x] T203 Reclassify host-Docker docs (`TEST_SERVER_READY.md`, `deploy/ansible/README.md`)



## Phase 3 — Code health



- [x] T301 Extract `WorkerHealthHttpServer` to `modules/common`

- [x] T302 Hex message write-path (`MessageSendCoordinator` + `MessageRepositoryPort.insert`; `MessageService` legacy fallback for stub tests)

- [x] T303 Merge pilot via `full-server.yml` + `pilot-overrides.yml` + profiles; `pilot-stack-up.sh` updated

- [x] T304 Consolidate korus-web hotswap overlays; drop deprecated `qemu-full-hotswap` fallback in QEMU sync scripts

- [x] T305 `ExportReplayWorker` incremental split (`ExportReferencedFilesSql` + prior `ExportMessageLoader` et al.)



## Phase 4 — Perf & format



- [x] T401 `MessageRepository.findByChatId` pre-sized `ArrayList`

- [x] T402 indexer/deep-archiver `/metrics` + `/health` in compose (`9197`, `9196`)

- [x] T403 Spotless Java (`spotless.gradle.kts` applied from root `build.gradle.kts`)



## Phase 5 — Web client (incremental)



- [x] T501 `messageTypeForMime` / `revokeBlobUrls` → `ui-messages-utils.js`



## Phase 6 — Platform modules (spec 009)



- [x] T601 `services:indexer` main → `IndexerWorker`

- [x] T602 `Dockerfile.bot-delivery-worker` + compose service (`bot-delivery-worker`)

