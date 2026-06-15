# Phase 0 — Research: System Review, Refactoring & Optimization

## 1. Profiling Tool Selection

**Decision**: async-profiler + JFR (JDK Flight Recorder) via JDK Mission Control

**Rationale**:
- Java 25 has built-in JFR; no extra agents needed for basic CPU/memory profiling
- async-profiler provides low-overhead wall-clock profiling for off-CPU waits
- Both tools work on Linux production with < 1% overhead
- JDK Mission Control provides the UI for analyzing JFR recordings

**Alternatives considered**:
- YourKit: commercial, powerful, but adds per-process cost
- VisualVM: adequate but less suited for headless production profiling
- Custom metrics via Prometheus: good for aggregate trends, insufficient for hotspot-level analysis

## 2. Microservice Extraction Pattern

**Decision**: NATS queue groups + shared infrastructure, one JAR per extracted service

**Rationale**:
- Existing workers already use NATS queue groups for workload distribution
- Extracting a module means packaging it as a standalone `main()` JAR that subscribes to the same NATS subjects
- No service discovery needed — NATS handles fan-out via queue groups
- Shared MinIO/PostgreSQL/Redis: extracted services read/write the same data stores as core-api; consistency is guaranteed by database transactions, not service orchestration

**Candidates for extraction**:
| Service | NATS Subject | Resource Profile | Extraction Complexity |
|---------|-------------|-----------------|----------------------|
| IndexerWorker | `msg.event.index` | CPU-heavy (Solr indexing) | Low — already standalone `main()` |
| RetentionWorker | `msg.event.retention` | Memory-heavy (JSON materialization) | Medium — shares MinIO client with core-api |
| DeepArchiverWorker | `msg.event.deep-archive` | I/O-heavy (MinIO writes) | Low — already standalone `main()` |
| ExportReplayWorker | (on-demand) | CPU + I/O | Medium — complex state machine |

## 3. Hot-Plug Lifecycle Design

**Decision**: Heartbeat-based presence detection + SIGTERM graceful drain

**Mechanism**:
- Each extracted service publishes a heartbeat on `$SVC.heartbeat.{serviceId}` every 10s
- Core server subscribes to `$SVC.heartbeat.*` and tracks active services in a ConcurrentHashMap with TTL (30s)
- On SIGTERM: JVM shutdown hook sets state to DRAINING → completes current message → calls `Connection.drain(Duration)` → exits
- Core server detects absence after heartbeat TTL expires → logs warning → continues with graceful degradation

**Alternatives considered**:
- etcd/Consul for service registry: overkill for 2-4 services; NATS alone is sufficient
- Kubernetes liveness probes: not applicable — we need hot-plug without K8s
- gRPC health protocol: adds gRPC dependency; HTTP `/health` + NATS heartbeat is simpler

## 4. Solr Atomic Update for Empty Content

**Decision**: SolJ `AtomicUpdate` with `set` to empty string for `content_txt` field is sufficient

**Rationale**:
- Solr atomic updates with `set: ""` correctly replace the field value with empty string
- No need for `delete` before `add` — atomic update is atomic at the document level
- Existing `IndexerWorker` already uses `AtomicUpdate` for `index_op=update`
- Verified via SolrJ 10.x documentation: `set` on a stored field with empty string removes the stored value

**Alternative considered**:
- Delete + re-add document: changes document version, triggers unnecessary replication in SolrCloud
