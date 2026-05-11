# Retention worker (hot-body)

## Purpose

The retention worker moves eligible **hot** PostgreSQL message bodies to **MinIO** (JSON snapshot per message), clears `messages.content`, publishes **NATS** index and retention events, and optionally records **audit** rows. It runs a periodic scan driven by retention policy (platform defaults, org, chat) with **legal hold** and **deep archive** gates.

For architecture, SQL semantics, NATS payloads, and operational risks, see **[`docs/RETENTION_AND_DEEP_ARCHIVE.md`](../../docs/RETENTION_AND_DEEP_ARCHIVE.md)** (project root, relative to this file: `../../docs/RETENTION_AND_DEEP_ARCHIVE.md`).

Укрупнённые фазы планирования (**A / B / C**) и правило «автономных» правок без раунда согласования — **§13** того же документа.

Индексы под выборку кандидатов hot-body — **Flyway `V015`** (см. **`docs/db/FLYWAY_AND_SCHEMA.md`**).

## Environment variables

| Variable | Role | Default / notes |
| --- | --- | --- |
| `RETENTION_WORKER_ENABLED` | Master switch; if `false`, process idles (no DB/NATS/MinIO required). | `false` |
| `RETENTION_SCAN_INTERVAL_SECONDS` | Seconds between hot-body passes (worker enforces minimum **5** s). | `3600` |
| `RETENTION_INITIAL_DELAY_SECONDS` | Sleep before the **first** pass after start (0–86400). | `0` |
| `RETENTION_DRY_RUN` | If `true`: same candidate **SELECT** and pass metrics/logs; **no** `UPDATE`, MinIO writes, `retention_hot_body_applied`, `audit_events`, or NATS publishes on the mutation path. | `false` |
| `RETENTION_USE_ADVISORY_LOCK` | If `true` and `DB_JDBC_URL` starts with `jdbc:postgresql:`: one JDBC connection per pass holds a PostgreSQL **session** `pg_try_advisory_lock` (fixed keys in `RetentionAdvisoryLockIds`) for the whole pass—including dry-run—then `pg_advisory_unlock` in `finally` when acquired. If the try-lock fails, the pass is skipped with **no** candidate `SELECT`, `INFO` log, and metric `retention_worker_pass_skipped_advisory_lock_total`. | `false` |
| `RETENTION_BATCH_LIMIT` | Max candidates per pass. | `25` (clamped **1…500**) |
| `RETENTION_REQUIRE_MINIO` | If `true`, skips work when MinIO is not configured; **readiness** requires MinIO + retention bucket when metrics port is on. | `true` |
| `RETENTION_USE_APPLIED_LOG` | Use `retention_hot_body_applied` dedup / bookkeeping. | `true` |
| `RETENTION_AUDIT_ENABLED` | Insert per-message `audit_events` on successful clear. | `true` |
| `RETENTION_BULK_AUDIT_MIN_CLEARED` | If cleared count **≥** threshold after a pass, insert one summary audit row (`message.retention.bulk_cleared`). `0` disables. | `0` |
| `RETENTION_SKIP_SNAPSHOT_IF_DEEP_EXISTS` | Before upload, `statObject` deep-archive / retention keys to skip duplicate JSON when object already exists (same bucket rules as code). | `false` |
| `RETENTION_ENSURE_MINIO_BUCKET` | On startup, ensure retention write bucket exists (`bucketExists` / `makeBucket`). | `true` |
| `RETENTION_MINIO_BUCKET` | Bucket for retention snapshots; if unset, uses `MINIO_BUCKET`. | falls back to `MINIO_BUCKET` |
| `RETENTION_MINIO_OBJECT_PREFIX` | Object key prefix inside bucket (normalized, trailing `/`). | `retention/body/` |
| `RETENTION_DEFAULT_HOT_BODY_MAX_AGE_DAYS` | Platform default for SQL overlay (nullable → SQL `NULL`). | unset |
| `RETENTION_DEFAULT_DEEP_ARCHIVE_ENABLED` | Platform default for deep-archive gate in SQL. | `true` |
| `RETENTION_DEFAULT_LEGAL_HOLD` | Platform default legal hold in SQL. | `false` |
| `RETENTION_METRICS_PORT` | If **1…65535**, serves Prometheus **`/metrics`** and **`GET /health`** on the same port. `0` disables HTTP metrics. | `0` |
| `RETENTION_JDBC_QUERY_TIMEOUT_SECONDS` | `Statement.setQueryTimeout` (seconds) for hot-body **SELECT** and **UPDATE** in the worker; `0` = driver default (no explicit timeout). Negative / non-numeric → `0`; max **86400**. | `0` |
| `RETENTION_INTER_MESSAGE_DELAY_MS` | Pause between consecutive candidates in one pass (0–**60000** ms); `0` = none. | `0` |
| `RETENTION_SNAPSHOT_TEMPFILE_THRESHOLD_BYTES` | When **`> 0`** and UTF-8 length of `messages.content` is **strictly greater** than this value, the retention JSON snapshot is written to a temp file under `java.io.tmpdir` (prefix `retention-snapshot-`) and uploaded to MinIO (see `RETENTION_MINIO_MULTIPART_THRESHOLD_BYTES`); **`0`** (default) keeps the in-memory `writeValueAsBytes` path. Parsed as a non-negative long, clamped to **1 GiB** max; invalid strings → **`0`**. | `0` |
| `RETENTION_MINIO_MULTIPART_THRESHOLD_BYTES` | On the **temp-file** snapshot path only: if **`Files.size(temp) >=`** this threshold, **`MinioClient.uploadObject`** is used (SDK multipart when needed); otherwise **`putObject`** with a stream. Unset / blank defaults to **`Long.MAX_VALUE`** (effectively always **`putObject`** for temp-file until configured). Non-positive or invalid → same default. Example to enable at 32 MiB: **`33554432`**. | unset (= `Long.MAX_VALUE`) |
| `DB_JDBC_URL` | Hot PostgreSQL JDBC URL; **required** when worker enabled. | — |
| `DB_USER` | Hot DB user. | `avandocmsg` |
| `DB_PASSWORD` | Hot DB password. | `avandocmsg` |
| `NATS_URL` | NATS server URL. | `nats://localhost:4222` |
| `MINIO_ENDPOINT` | MinIO S3 endpoint URL. | required with access/secret for client |
| `MINIO_ACCESS_KEY` | MinIO access key. | required with endpoint/secret for client |
| `MINIO_SECRET_KEY` | MinIO secret key. | required with endpoint/access for client |
| `MINIO_REGION` | Optional MinIO region on client builder. | unset |
| `MINIO_BUCKET` | Default bucket name (deep-archiver alignment); used when `RETENTION_MINIO_BUCKET` is unset. | `deep-archive` |

### HTTP when `RETENTION_METRICS_PORT` > 0

- **`GET /metrics`** — Prometheus text format (JVM default exports + worker counters/histograms/gauges). **Build:** **`retention_worker_build_info`** (**Info**, метки **`version`** / **`name`**=`retention-worker`; версия из **`Implementation-Version`** в JAR модуля при сборке, иначе **`unknown`** в IDE/тестах без manifest).
- **Pass liveness (gauges):** `retention_worker_last_hot_body_pass_epoch_seconds` updates when a pass finishes **after** the candidate `SELECT` (including empty batch and `RETENTION_DRY_RUN=true`); not on `RETENTION_REQUIRE_MINIO` skip, advisory-lock skip (no candidate query), or uncaught errors before completion. `retention_worker_last_pass_cleared_count` holds the last pass cleared count; **dry-run** always records **`0`** (no Hot DB clears).
- **`GET /health`** — **`200`** / body `ok` when dependencies are ready; **`503`** / `not ready` when not (no secrets in response).

## MinIO snapshot upload (implementation note)

Hot-body snapshots use **`MinioClient.putObject`** or **`MinioClient.uploadObject`** with `Content-Type` **`application/json`**. The snapshot root JSON includes optional **`pass_id`** (UUID string, same as **`msg.event.retention`** for that **`runOnce`**) when set; omitted otherwise. After the envelope is built, the worker appends **`snapshot_sha256`** (64 lowercase hex chars): SHA-256 over the **UTF-8 bytes of the JSON object immediately before that property is added** (same `ObjectMapper` as upload). The uploaded document **includes** `snapshot_sha256`; NATS **`RetentionAppliedEvent.snapshot_sha256`** and per-message audit **`details_json.snapshot_sha256`** carry the same value when MinIO snapshot materialization runs (`RETENTION_DRY_RUN` publishes no retention event). Skipping **`putObject`** because an object already exists still computes the digest for the **would-be** envelope (current pass fields). Summary bulk audit rows do **not** include `snapshot_sha256`. **`DeepArchiverWorker`** uses the same digest rules and shared **`ArchiveSnapshotEnvelopeDigest`** from **`modules/common`** (that module exposes Jackson as **`api`** so workers share **`ObjectMapper`** types with the helper). By default, the JSON is built with **`ObjectMapper.writeValueAsBytes`** and uploaded from a **`ByteArrayInputStream`** with known size **`bytes.length`** (**`putObject`**). When **`RETENTION_SNAPSHOT_TEMPFILE_THRESHOLD_BYTES > 0`** and the UTF-8 size of **`messages.content`** is strictly greater than that threshold, the mapper writes the **final** payload (including `snapshot_sha256`) to a temp file (**`Files.createTempFile("retention-snapshot-", ".json")`**, under the JVM temp directory). If **`Files.size(temp) >= RETENTION_MINIO_MULTIPART_THRESHOLD_BYTES`** (and that env is set to a finite positive value), **`uploadObject`** is used and **`retention_worker_minio_multipart_uploads_total`** increments on success; otherwise **`putObject`** reads via **`Files.newInputStream`**. The temp file is deleted in a **`finally`** block. Dry-run does not enter the per-message path (no temp files for snapshots). Skip-if-exists without upload does not create temp files when the in-memory path is used; the tempfile path only runs when an upload (`putObject` or `uploadObject`) executes.

**MinIO Java SDK** in this module: **`io.minio:minio:8.5.10`** (see `build.gradle.kts`).

## Local run

From the repository root (Windows example):

```bat
.\gradlew.bat :modules:workers:retention:run
```

Application **`main`**: `com.avandocmsg.messenger.worker.retention.RetentionWorker` (Gradle `application` plugin in `build.gradle.kts`).

Set at least `RETENTION_WORKER_ENABLED=true`, `DB_JDBC_URL`, and working NATS; configure MinIO unless you rely on dry-run / `RETENTION_REQUIRE_MINIO=false` per your policy.

## Docker Compose

**File:** `docker/docker-compose.dev-min.yml`  
**Service:** `retention-worker`  
**Profile:** `retention` (service is not started unless the profile is enabled)

Example:

```bash
docker compose -f docker/docker-compose.dev-min.yml --profile retention up -d retention-worker
```

The compose file maps host **`9192`** → container metrics port and documents **`/metrics`** and **`/health`**; Docker Compose **`healthcheck`** for **`retention-worker`** probes **`GET /health`** on **`RETENTION_METRICS_PORT`** inside the container (so **`RETENTION_METRICS_PORT` must be > 0** for **`healthy`** status).

С хоста после поднятия сервиса: **`.\scripts\smoke-retention-worker.ps1`** (по умолчанию **`http://localhost:9192`**) — проверяет **`/health`**, **`/metrics`** и наличие в scrape строки **`retention_worker_build_info`** (нужен воркер, собранный с регистрацией build info на **`/metrics`**).

## Operations

- **Graceful shutdown:** on SIGTERM or JVM exit, a single shutdown hook stops scheduling new hot-body passes (cooperative flag + bounded wait on the scan executor, default **15 s**), then closes the metrics HTTP server (if `RETENTION_METRICS_PORT` > 0), the NATS connection, and the JDBC pool, in that order. A pass already running—including the current per-message `processOne`—is allowed to finish until the executor wait times out (**best-effort**). Duplicate hook invocation is ignored.

- **Multi-replica Hot DB:** when several retention worker replicas share one Hot PostgreSQL, set `RETENTION_USE_ADVISORY_LOCK=true` so at most one pass runs the hot-body scan and per-message DB writes at a time (reduces duplicate work and NATS churn; idempotency remains as before).

## Safety checklist

1. **Dry-run first:** set `RETENTION_DRY_RUN=true`, enable the worker, confirm candidate counts and metrics/logs match expectations **without** mutating DB, MinIO, or NATS.
2. **Staging:** run a bounded batch (`RETENTION_BATCH_LIMIT`), optional `RETENTION_INTER_MESSAGE_DELAY_MS` to reduce load, and JDBC timeouts if needed.
3. **MinIO:** confirm bucket and prefix (`RETENTION_MINIO_BUCKET`, `RETENTION_MINIO_OBJECT_PREFIX`); use `RETENTION_ENSURE_MINIO_BUCKET=false` if buckets are managed by IaC.
4. **Readiness:** with metrics enabled, probe **`GET /health`** before traffic-sensitive cutovers.
5. **Production:** only then disable dry-run and widen batch/interval as appropriate.
