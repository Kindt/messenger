# Quickstart: System Review, Refactoring & Optimization

## Prerequisites

- JDK 25
- JDK Mission Control + async-profiler (optional, for profiling)
- Running dev infrastructure: PostgreSQL, NATS, MinIO, Redis, Solr (see `scripts/smoke-*`)

## Track 1 — Profiling

```bash
# Record 60s JFR of core-api
jcmd $(jps | grep Tomcat | cut -d' ' -f1) JFR.start duration=60s filename=core-api.jfr

# Record 60s JFR of a worker
jcmd $(jps | grep DeepArchiverWorker | cut -d' ' -f1) JFR.start duration=60s filename=deep-archiver.jfr

# Open in JDK Mission Control
jmc core-api.jfr
```

## Track 2 — Epic 01 Completion

See `docs/plans/01-retention-phase-b.md` for detailed tasks:
- Step 6: `IndexerWorker` — Solr atomic update on content clear
- Step 7: Web-client TTL indicator (`app.js`, `styles.css`)
- Step 8: Prometheus metrics (`deep_archiver_chunk_writes_total`, `retention_worker_file_ref_skipped_total`)
- Step 9: Docs update (`RETENTION_AND_DEEP_ARCHIVE.md`, `FLYWAY_AND_SCHEMA.md`)

```bash
# After changes
./gradlew test
```

## Track 3 — Microservice Extraction

```bash
# Build current indexer worker module
./gradlew :modules:workers:indexer:build

# Run current indexer worker
./gradlew :modules:workers:indexer:run
```

## Track 4 — Code Review

```bash
# Find dead code
rg "import.*\n(?!.*\1)" modules/ --type java | sort

# Baseline integrity gate
./gradlew buildIntegrity

# Fast local regression gate
./gradlew test
```
