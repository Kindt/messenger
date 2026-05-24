# Hotspots Before/After — 2026-05-24

Status: `partial-core-api-verified`  
Source plan task: `T051`

## Measurement Setup

| Parameter | Value |
|---|---|
| Environment | QEMU `core-api` @ `http://127.0.0.1:18080` |
| Load | `scripts/profiling/load-core-api.ps1 -DurationSeconds 15 -Concurrency 3` |
| Metric | Prometheus `jvm_memory_bytes_used{area="heap"}` (avg of 3 samples post-load) |
| Change | `RedisProbe` — shared Redis ping instead of per-request `RedisClient.create()` |

## Baseline vs Optimized (core-api)

| Module | Metric | Before | After | Delta | Notes |
|---|---|---|---|---|---|
| core-api | Idle heap MB | 38.47 | 35.17 | −8.6% | pre-fix vs post-fix redeploy |
| core-api | Post-load heap MB (avg) | 126.90 | 48.96 | **−61.4%** | warm run #2 (648 req) |
| core-api | Post-load heap MB (avg) | 126.90 | 60.81 | −52.1% | first post-fix run (cold) |

## Top-3 Fixes Applied

1. `RedisProbe` — **verified**
2. `ChunkedSnapshotWriter` (Phase B)
3. Retention streaming — **deferred**

## Worker Modules (QEMU Prometheus — 2026-05-24)

| Module | Idle heap MB | Post-load heap MB | Load scenario |
|---|---|---|---|
| retention-worker | 43.2 | 45.9 | 2× archive_ttl messages (8KB) |
| export-replay-worker | 25.0 | 26.7 | idle during archive pass |
| deep-archiver-worker | n/a | n/a | no `/metrics` port on compose; functional via chunk smoke |
| indexer-worker | n/a | n/a | no `/metrics` port; functional via T023/T036 |

Script: `scripts/profiling/profile-qemu-workers.ps1`

### Post-redeploy (streaming fix, fresh containers — 2026-05-24)

| Module | Idle heap MB | Post-load heap MB |
|---|---|---|
| retention-worker | 16.4 | 19.6 |
| export-replay-worker | 32.0 | 34.0 (incl. 1× export job) |

Workers rebuilt on QEMU: `retention-worker`, `deep-archiver-worker` (includes `writeChunkedSnapshotFromFile`).


`ChunkedSnapshotWriter.writeChunkedSnapshotFromFile` — avoids `Files.readAllBytes` on large temp snapshots (`RetentionHotBodyJanitor.uploadThroughTempFile`).

