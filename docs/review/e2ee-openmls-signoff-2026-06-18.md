# OpenMLS engineering sign-off checklist (spec 020 T020021)

**Date:** 2026-06-18  
**Scope:** hybrid stub (`openmls-stub-v1`) — not production OpenMLS native binding  
**Stage/prod host:** deferred until Sep 2026+

## Engineering gates (QEMU)

| # | Check | Command / evidence | Status |
|---|-------|-------------------|--------|
| 1 | Vector JUnit green | `./gradlew :modules:core-api:test --tests "*OpenMlsInterop*"` | ☐ |
| 2 | Playwright tier | `.\scripts\playwright-dev-loop.ps1 -Tier e2ee-openmls-interop` | ☐ |
| 3 | Admin status wire profile | `GET /admin/e2ee/status` → `openmls_wire_profile=openmls-stub-v1` | ☐ |
| 4 | Batch migrate idempotent | `.\scripts\smoke-openmls-migration.ps1` (×2) | ☐ |
| 5 | Dev factory flag | UI `?e2ee_openmls_dev=1` → `KorusOpenMlsDevFactory` | ☐ |
| 6 | `buildIntegrity` | `./gradlew buildIntegrity` | ☐ |

## Security / product gates (before prod `mls_status=active`)

| # | Owner | Item | Status |
|---|-------|------|--------|
| S1 | Security | Replace hybrid stub with audited OpenMLS or BC state machine | ☐ |
| S2 | Security | Pen-test external MLS client interop | ☐ |
| S3 | Product | Accept server `/plaintext-preview` policy when MLS active | ☐ |
| S4 | Ops | Stage migrate-batch + NATS `mls.*` consumer 24h soak | ☐ Sep 2026+ |

## References

- `specs/020-openmls-interop/spec.md`
- `docs/adr/ADR-e2ee-mls-library.md`
- `docs/review/e2ee-security-signoff-packet-2026-06-15.md`
