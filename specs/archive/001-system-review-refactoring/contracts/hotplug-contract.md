# Hot-Plug Contract — Extracted Microservices

## NATS Subjects

| Subject | Direction | Payload | Purpose |
|---------|-----------|---------|---------|
| `$SVC.heartbeat.{serviceId}` | Service → Core | `{"serviceId":"...","state":"ACTIVE","uptimeMs":12345}` | Presence heartbeat every 10s |
| `$SVC.heartbeat.*` | Core ← All | (subscription) | Core tracks active services |
| `msg.event.index` | Core → Service | `MessageWorkerEvent` JSON | Indexing workload |
| `msg.event.retention` | Core → Service | `RetentionAppliedEvent` JSON | Retention events |
| `msg.event.deep-archive` | Core → Service | `MessageWorkerEvent` JSON | Deep-archive workload |

## Health Check

Each extracted service MUST expose HTTP `/health` and `/ready` endpoints on a configurable port (`SERVICE_HTTP_PORT`, default `9090`).

- `GET /health` → `200 OK` + `{"status":"UP","serviceId":"..."}` always
- `GET /ready` → `200 OK` if state == ACTIVE, `503 Service Unavailable` if DRAINING or INIT

## Graceful Shutdown Protocol

```
SIGTERM → shutdown hook:
  1. Set state to DRAINING → /ready returns 503
  2. Stop accepting new work (unsubscribe from NATS subjects or stop dispatcher)
  3. Wait for in-flight messages to complete (timeout: 30s)
  4. Call Connection.drain(Duration.ofSeconds(10))
  5. Close all resources (MinIO, DB connections if any)
  6. Publish final heartbeat with state=DRAINING
  7. Exit (System.exit(0))
```

## Graceful Degradation (Core Server)

When the core detects a service as MISSING (no heartbeat for 30s):

1. Log: `"Service {serviceId} is MISSING — continuing with reduced functionality"`
2. If the missing service is IndexerWorker: skip Solr indexing, log per-message warning
3. If the missing service is RetentionWorker: skip retention pass, emit metric `retention_worker_skipped_due_to_missing`
4. If the missing service is DeepArchiverWorker: skip deep-archive writes
5. When heartbeat resumes: log: `"Service {serviceId} reconnected — resuming normal operation"`

## Environment Variables

Extracted services reuse existing env variables with the addition of:

| Variable | Default | Description |
|----------|---------|-------------|
| `SERVICE_ID` | (auto) | Unique instance identifier |
| `SERVICE_HTTP_PORT` | `9090` | HTTP port for health endpoints |
| `SERVICE_HEARTBEAT_INTERVAL_MS` | `10000` | Heartbeat publish interval |
| `SERVICE_HEARTBEAT_TTL_MS` | `30000` | Time before core considers service MISSING |
| `SERVICE_DRAIN_TIMEOUT_MS` | `30000` | Max time to drain in-flight work on shutdown |
