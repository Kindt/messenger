# 1C bridge family — design spike (spec 014 T01418)

**Status:** spike only — no production bridge in this phase.

## Scope

- OData/HTTP services for 1C:Enterprise 8.3
- Read-only catalog + document status in v1
- On-prem only; credentials in org plugin policy vault

## Planned worker

`modules/workers/1c-bridge/` — hot-plug worker mirroring `connector-runtime` Plugin Runtime API.

## Demo path

Mock fixtures under `integrations/_mock-servers/fixtures/1c/` (future).
