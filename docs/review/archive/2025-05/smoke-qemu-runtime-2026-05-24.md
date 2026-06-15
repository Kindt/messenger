# QEMU Smoke Runtime Report — 2026-05-24

Scope: practical smoke sweep for current QEMU two-VM environment (`server:18080`, `web:19088`) after US2 strict fixes.

## Passed

- `scripts/smoke-ready.ps1 -BaseUrl "http://127.0.0.1:18080" -StrictDependencies`
- `scripts/smoke-auth.ps1 -BaseUrl "http://127.0.0.1:18080"`
- `scripts/smoke-korus-web.ps1 -WebBaseUrl "http://127.0.0.1:19088" -CheckApi`
- `scripts/smoke-retention-worker.ps1 -BaseUrl "http://127.0.0.1:19192"`
- `scripts/smoke-openapi-export-compliance.ps1 -BaseUrl "http://127.0.0.1:18080"`
- `scripts/smoke-export-observability.ps1 -CoreMetricsUrl "http://127.0.0.1:18080/api/v1/metrics/prometheus" -WorkerMetricsUrl "http://127.0.0.1:19193/metrics" -RetentionMetricsUrl "http://127.0.0.1:19192/metrics"`
- `scripts/smoke-us2-epic01-qemu.ps1 -SkipHotPlug`
- `scripts/smoke-us2-epic01-qemu.ps1` (full, incl. hot-plug via NATS tunnel `14222 -> VM:4222`) — **2026-05-24 post-redeploy**
- `scripts/smoke-export-compliance-flow.ps1 -BaseUrl "http://127.0.0.1:18080"`
- `scripts/smoke-export-compliance-pack.ps1 -BaseUrl "http://127.0.0.1:18080" -WorkerMetricsUrl "http://127.0.0.1:19193/metrics" -RetentionMetricsUrl "http://127.0.0.1:19192/metrics" -NatsUrl "nats://127.0.0.1:14222" -SkipObservability -SkipDownload -SkipAudit` (with temporary SSH tunnel `14222 -> VM:4222`)
- `scripts/smoke-hotplug-indexer.ps1 -RepoRoot "." -NatsUrl "nats://127.0.0.1:14222"` (via temporary SSH tunnel `14222 -> VM:4222`)
- `scripts/smoke-push-worker.ps1 -HealthUrl "http://127.0.0.1:19194/health"` (via temporary SSH tunnel `19194 -> VM:9194`)
- `scripts/smoke-export-suggested-nats.ps1 -ChatId <id> -BaseUrl "http://127.0.0.1:18080" -NatsUrl "nats://127.0.0.1:14222"` (passes after enabling suggested subscriber flags in `docker-compose.us2-verify.yml`)
- `scripts/smoke-export-auto-queue-nats.ps1 -ChatId <id> -BaseUrl "http://127.0.0.1:18080" -NatsUrl "nats://127.0.0.1:14222"` (passes with `EXPORT_AUTO_QUEUE_ON_SUGGESTED=true`)
- `scripts/smoke-deep-archive-chunks.ps1 -BaseUrl "http://127.0.0.1:18080" -UseSshMinioTunnel` (passes with temporary SSH tunnel `19000 -> VM:9000`; verified `manifest.json` + `part-*.json` + SHA-256 integrity)
- `scripts/smoke-export-chat.ps1 -BaseUrl "http://127.0.0.1:18080"` (passes after `ExportJobEnqueuer` fix: DB insert before NATS publish; job `4e1efec9-...` → `export_v1`, bundle download OK)
- `scripts/smoke-ttl-ui.ps1 -BaseUrl "http://127.0.0.1:18080"` (API: `visibility_ttl_seconds=60`; web UI on `19088`: `.msg-ttl-indicator` shows `· ⏱ …` for marker message)

## Environment-limited items

- NATS CLI dependent scenarios require:
  - `nats` binary in host PATH (installed during this run), and
  - server-side suggested-event processing enabled (`RETENTION_PUBLISH_EXPORT_SUGGESTED=true`, `EXPORT_SUGGESTED_SUBSCRIBER_ENABLED=true`; plus `EXPORT_AUTO_QUEUE_ON_SUGGESTED=true` for auto-queue),
  - and a local SSH tunnel to VM NATS (`14222 -> 4222`) when host has no direct NATS port forward.
- Default host-local ports in some smoke scripts are for local Docker mode (`localhost:8080/919x`) and need QEMU URL overrides/tunnels when running against VMs.

## Script fixes made during this sweep

- `scripts/smoke-admin-export-compliance-prep.ps1`: added pipeline output (`CHAT_ID=...`, `FILE_ID=...`) for wrapper scripts.
- `scripts/smoke-export-suggest-cancel-flow.ps1`: normalized script content to parse reliably in PowerShell.
- `scripts/smoke-export-compliance-pack.ps1`: fixed chat id scope across step scriptblocks and passed QEMU-aware core metrics URL to worker metrics step.
- `scripts/smoke-deep-archive-chunks.ps1`: added end-to-end chunk smoke (large message -> deep-archive manifest/parts in MinIO -> chunk and assembled SHA-256 validation) with optional QEMU SSH tunnel for MinIO.
- `ExportJobEnqueuer`: insert queued row before NATS publish (fixes race where worker skipped job as "not queued").
- `ExportMetrics.ensureRegistered()`: export counters visible on cold `/metrics` scrape (T024).
- `scripts/smoke-us2-epic01-qemu.ps1`: NATS tunnel (`14222`) + `NatsUrl` passthrough for hot-plug step.
- `scripts/lib/SmokeExportInspect.ps1`: supports `zip_bundle=false` (json export mode) instead of failing.
- `scripts/lib/SmokeAdminUi.ps1`: relaxed static admin page marker check to avoid false negative on localized HTML.
- `scripts/lib/SmokePrometheus.ps1`: fixed number parsing for scientific notation in gauges/counters.
