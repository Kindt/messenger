# Plan: Platform Modules (009)

## US1 — Indexer hot-plug

1. Audit `services:indexer` vs `modules/workers/indexer` — выбрать canonical deployable
2. Dockerfile + compose service + NATS queue-group consumer parity
3. Update ADR-hotplug + `scripts/stop-local-indexer.ps1` if needed
4. Smoke: `smoke-hotplug-indexer.ps1`

## US2 — Bot delivery

1. Dockerfile + compose entry in `docker-compose.full-server.yml`
2. Public Bot API surface (minimal MVP per presentation-gaps P2-1)
3. Smoke script + SMOKE_INDEX entry

## References

- [`docs/adr/ADR-hotplug-deployment-split.md`](../../docs/adr/ADR-hotplug-deployment-split.md)
- [`docs/plans/2026-06-16-presentation-gaps-implementation-plan.md`](../../docs/plans/2026-06-16-presentation-gaps-implementation-plan.md)
