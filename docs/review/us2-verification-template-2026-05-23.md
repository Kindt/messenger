# US2 Verification Template — 2026-05-23

Purpose: capture runtime evidence for tasks `T023`, `T024`, `T025`, `T026`, `T028`, `T047`.

Status: `pending-runtime-stack`

## Environment

| Field | Value |
|---|---|
| Stack start command | `./scripts/full-stack-up.ps1 -ExportSmoke -WaitReady -SkipEnsure` |
| Stack stop command | `./scripts/full-stack-down.ps1 -ExportSmoke` |
| Host date/time | pending |
| Operator | pending |
| Commit/branch | pending |

## T023 — Solr atomic update verification

Goal: verify retention clears Solr `content_txt` after body cleanup.

| Check | Result | Evidence |
|---|---|---|
| Test message sent with `visibility_ttl_seconds=60` | pending | pending |
| Retention pass processed message | pending | pending |
| Solr document `content_txt` becomes empty | pending | pending |

Notes: pending

## T024 — Prometheus metrics verification

Goal: verify `deep_archiver_chunk_writes_total`, `retention_worker_chunk_writes_total`, `retention_worker_file_ref_skipped_total`.

| Metric | Before | After | Delta | Evidence |
|---|---|---|---|---|
| `deep_archiver_chunk_writes_total` | pending | pending | pending | pending |
| `retention_worker_chunk_writes_total` | pending | pending | pending | pending |
| `retention_worker_file_ref_skipped_total` | pending | pending | pending | pending |

## T025 — Chunked deep-archive verification

Goal: verify `manifest.json` + `part-*.json` layout in MinIO.

| Check | Result | Evidence |
|---|---|---|
| Large message produced chunked archive | pending | pending |
| `messages/{id}/manifest.json` exists | pending | pending |
| `messages/{id}/part-*.json` exists | pending | pending |
| Manifest metadata matches expected payload | pending | pending |

## T026 — File-ref skip verification

Goal: verify `file://{uuid}` content does not create retention/deep-archive snapshot objects.

| Check | Result | Evidence |
|---|---|---|
| Message with `content=file://{uuid}` sent | pending | pending |
| Retention snapshot not created | pending | pending |
| Deep-archive snapshot not created | pending | pending |
| `retention_worker_file_ref_skipped_total` incremented | pending | pending |

## T028 — Epic checkbox sweep

Target document: `docs/plans/01-retention-phase-b.md`.

| Section | Status |
|---|---|
| Step 6 (Solr) | pending |
| Step 7 (web-client TTL) | pending |
| Step 8 (metrics/smoke) | pending |
| Step 9 (docs sync) | pending |

Decision: pending (`all [x]` required for close)

## T047 — Final status update

Target: set `docs/plans/01-retention-phase-b.md` status to `completed` after successful `T023–T028`.

| Check | Result |
|---|---|
| `T023–T026` passed | pending |
| `T028` completed with all required checkboxes `[x]` | pending |
| Plan status switched to `completed` | pending |

## Final Sign-off

- Runtime US2 block complete: pending
- Ready to mark tasks in `specs/001-system-review-refactoring/tasks.md`: pending
