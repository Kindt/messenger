# Spec 008: Repository Cleanup

**Status:** `closed` (engineering sign-off 2026-06-15)  
**Parent:** total cleanup program  
**Successor:** platform tail → [`specs/009-platform-modules/`](../009-platform-modules/)

## Goal

Тотальная гигиена репозитория: scripts, docs, dead code, дубли, hex tail, perf — без prod backward-compat.

## In scope

- Scripts: `.cmd` removal, deprecated `.ps1`, SMOKE_INDEX sync
- Docs: review archive, ROADMAP/CHANGELOG, host-Docker reclassify (Linux/CI vs QEMU Windows)
- Code: hex message write-path, WorkerHealthHttpServer extract, compose profiles merge
- Perf: `findByChatId` projection, indexer/deep metrics ports
- Formatting: Spotless incremental (Java)

## Out of scope

- Ops deploy on real stage/prod hosts → **spec 007**
- `services:indexer` + `bot-delivery` deploy → **spec 009**
- E2EE `legacy` scheme removal (product contour)

## Success criteria

- `./gradlew buildIntegrity` green after each PR — ✅ 2026-06-15
- Inner Playwright `all-inner` green for UI/API-touching changes — ✅ outer gate 2026-06-15 (spec 007 T701)
- 0 `scripts/*.cmd`, 0 orphan one-shot Python patch scripts — ✅ phase 0–2
- `MessageService.send` delegated to hex `MessageApplicationService` — ✅ phase 3 (T302)

## Closure

All tasks in [`tasks.md`](tasks.md) complete. Phase 6 deliverables (indexer/bot-delivery skeleton) tracked under **spec 009**.
