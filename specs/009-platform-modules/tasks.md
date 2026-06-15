# Tasks: Platform Modules (009)

## US1 — Indexer

- [x] T101 Canonical deployable: `modules/workers/indexer` + `Dockerfile.indexer-worker`; `services:indexer` delegates main to `IndexerWorker`
- [x] T102 Dockerfile + compose wiring (`indexer-worker` in full-server, profile `solr`/`full`)
- [x] T103 NATS queue-group + Solr in `IndexerWorker` (existing)
- [x] T104 `smoke-hotplug-indexer.ps1` green on QEMU guest (operator)

## US2 — Bot delivery

- [x] T201 `docker/Dockerfile.bot-delivery-worker`
- [x] T202 Compose `bot-delivery-worker` in `docker-compose.full-server.yml` (profile `push`/`full`)
- [x] T203 Bot API MVP (REST register/webhook/subscribe/sendMessage; MENTIONS_ONLY filter in bot-delivery worker)
- [x] T204 Smoke + `SMOKE_INDEX.md` entry (operator) — `smoke-bot-delivery-worker.ps1`, `smoke-bot-api.ps1`/`.sh`

---

**Engineering closure:** 2026-06-15 — US1–US2 complete; `buildIntegrity` green; spec **closed**. Ops tail → spec **007**.
