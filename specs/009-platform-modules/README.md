# Spec 009 — Platform Modules

**Status:** active

| US | Deliverable |
|----|-------------|
| US1 Indexer | `services:indexer` → `IndexerWorker` main; production image `Dockerfile.indexer-worker` |
| US2 Bot delivery | `Dockerfile.bot-delivery-worker` + `bot-delivery-worker` in `docker-compose.full-server.yml` (`--profile full` or `push`) |

Run full stack: `./scripts/full-stack-up.sh --profile full` (includes bot-delivery with `push` profile).
