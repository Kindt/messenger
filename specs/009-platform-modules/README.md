# Spec 009 — Platform Modules

**Status:** `closed` (engineering 2026-06-15)

| US | Deliverable |
|----|-------------|
| US1 Indexer | `modules/workers/indexer` + `Dockerfile.indexer-worker`; `services:indexer` → `IndexerWorker` |
| US2 Bot delivery | `Dockerfile.bot-delivery-worker` + compose; Bot API MVP (`V032`, REST `/v1/bots`, `/v1/bot/send`) |

**Smokes:** `smoke-hotplug-indexer.ps1`, `smoke-bot-delivery-worker.ps1`, `smoke-bot-api.ps1` (see [`scripts/SMOKE_INDEX.md`](../../scripts/SMOKE_INDEX.md)).

Full stack: `./scripts/full-stack-up.sh --profile full` (bot-delivery under `push`/`full`).
