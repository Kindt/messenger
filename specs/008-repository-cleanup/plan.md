# Plan: Repository Cleanup (008)

**Status:** `closed` (2026-06-15)

## Phases

| Phase | Scope | Gate |
|-------|-------|------|
| 0 | gitignore, orphan py/ps1 | buildIntegrity |
| 1 | specs archive, living docs, review archive | link grep |
| 2 | `.cmd` + deprecated `.ps1` removal | CI grep + guest smoke |
| 3 | WorkerHealthHttpServer, hex message write | buildIntegrity + Playwright api |
| 4 | compose pilot/full profiles, korus-web hotswap | pilot + deploy smokes |
| 5 | perf hotspots, Spotless | profiling scripts |

## References

- [`docs/plans/09-code-health-backlog.md`](../../docs/plans/09-code-health-backlog.md)
- [`docs/plans/10-web-client-code-health-backlog.md`](../../docs/plans/10-web-client-code-health-backlog.md)
- [`docs/plans/08-hexagonal-refactoring.md`](../../docs/plans/08-hexagonal-refactoring.md)
- [`scripts/SMOKE_INDEX.md`](../../scripts/SMOKE_INDEX.md)

## Closure

All phases 0–6 delivered; `./gradlew buildIntegrity` green. Platform-module tail → [`specs/009-platform-modules/`](../009-platform-modules/).
