# Spec 009: Platform Modules

**Status:** active (2026-06-15)

## Goal

Довести skeleton-модули до deploy/runtime parity (не удалять).

## In scope

- **US1** `services:indexer` — hot-plug deployable, один canonical path vs `modules/workers/indexer`
- **US2** `modules:workers:bot-delivery` — Dockerfile, compose, smoke; Bot API (presentation-gaps P2-1)

## Out of scope

- Repository hygiene → **spec 008**
- Stage/prod ops → **spec 007**

## Success criteria

- `services:indexer` в compose с health/metrics; ADR обновлён
- `bot-delivery` worker в full-server compose; smoke green
- `./gradlew buildIntegrity` green
