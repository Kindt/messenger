# Data Model: System Review, Refactoring & Optimization

## Entities

### BottleneckHotspot

Records a profiling finding.

| Field | Type | Description |
|-------|------|-------------|
| moduleName | String | Affected module (core-api, deep-archiver, retention, export-replay, indexer) |
| location | String | Class and method (e.g., `RetentionHotBodyJanitor.processOne()`) |
| hotspotType | enum(CPU, MEMORY, IO, LOCK) | Category of bottleneck |
| estimatedImpactPercent | int | Estimated % of module resource consumption |
| rootCause | String | Analysis of why the hotspot occurs |
| proposedFix | String | Description of the optimization |
| verified | boolean | Whether the fix has been applied and measured |

### ServiceCandidate

Represents a module identified for microservice extraction.

| Field | Type | Description |
|-------|------|-------------|
| name | String | Module name (e.g., `indexer`) |
| natsSubjects | List<String> | NATS subjects consumed by this service |
| resourceProfile | String | CPU/memory/IO characterization |
| dependencies | List<String> | External services required (MinIO, PG, Redis, Solr) |
| hasMainMethod | boolean | Whether module already has a public `main()` |
| extractionComplexity | enum(LOW, MEDIUM, HIGH) | Effort estimate for extraction |
| hotPlugReady | boolean | Whether the extraction contract is implemented |

### HotPlugLifecycle

Tracks the lifecycle state of an extracted service.

| State | Description |
|-------|-------------|
| INIT | Service started, connecting to NATS |
| ACTIVE | Service registered and processing messages |
| DRAINING | Service received SIGTERM, completing in-flight work |
| STOPPED | Service disconnected cleanly |
| MISSING | Heartbeat TTL expired (presumed crashed/network partition) |
