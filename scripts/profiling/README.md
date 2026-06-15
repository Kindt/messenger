# Profiling Scripts

These scripts facilitate JFR (JDK Flight Recorder) profiling of Korus Messenger services.

## Prerequisites

- JDK 25 (JFR is built-in)
- JDK Mission Control (`jmc`) for analyzing recordings
- Optional: async-profiler for wall-clock profiling

## Usage

### Profile core-api under load

```powershell
.\scripts\profiling\load-core-api.ps1 -BaseUrl http://127.0.0.1:18080 -DurationSeconds 30
.\scripts\profiling\measure-prometheus-heap.ps1 -MetricsUrl http://127.0.0.1:18080/api/v1/metrics/prometheus
```

For QEMU/docker: JRE images lack `jcmd`; use Prometheus scripts above, or the **profiling compose overlay** (JDK images, 8 targets):

```powershell
docker compose -f docker/docker-compose.full-server.yml -f docker/docker-compose.profiling.yml up -d --build
.\scripts\profiling\profile-docker-jfr.ps1 -DurationSeconds 60
```

Single service (auto-detect container by compose service name):

```powershell
.\scripts\profiling\profile-docker-jfr.ps1 -Service message-pipeline -DurationSeconds 60
```

Explicit container name (when auto-detect is ambiguous):

```powershell
.\scripts\profiling\profile-docker-jfr.ps1 -ContainerName korus-core-api-1 -OutputName core-api -DurationSeconds 60
```

Profiling overlay services: `core-api`, `message-pipeline`, `archiver-worker`, `deep-archiver-worker`, `retention-worker`, `export-replay-worker`, `push-worker`, `indexer-worker`, `bot-delivery-worker`.

Host-local Tomcat can still use JFR:

```powershell
.\scripts\profiling\profile-core-api.ps1 [-DurationSeconds 60] [-OutputDir ./jfr-recordings]
```

### Profile workers on QEMU

```powershell
.\scripts\profiling\profile-qemu-workers.ps1 -ApiBaseUrl http://127.0.0.1:18080
```

Requires SSH tunnel to server VM (ports 19192 retention, 19193 export-replay). Prometheus without JFR overlay: deep-archiver `:9196/metrics`, indexer `:9197/metrics` (full-server compose).

After code changes to workers, rebuild on QEMU guest:

```bash
docker compose -f docker/docker-compose.full-server.yml build retention-worker deep-archiver-worker
docker compose -f docker/docker-compose.full-server.yml up -d retention-worker deep-archiver-worker
```

(`qemu-redeploy.ps1 -ServerOnly` rebuilds **core-api** only.)

```powershell
.\scripts\profiling\profile-worker.ps1 -WorkerName deep-archiver [-DurationSeconds 60] [-OutputDir ./jfr-recordings]
```

Supported WorkerName values: `deep-archiver`, `retention`, `indexer`, `export-replay`, `archiver`, `message-pipeline`, `push`.

## Analysis

Open the generated `.jfr` files in JDK Mission Control:

```powershell
jmc ./jfr-recordings/core-api.jfr
```

Look for:

- **CPU hotspots**: Methods consuming the most CPU time
- **Allocation hotspots**: Methods generating the most heap pressure
- **Lock contention**: Threads blocked on locks
- **GC pressure**: Frequency and duration of garbage collection pauses

## Hotspot Report Template

After analysis, document findings using the BottleneckHotspot format:

```markdown
### [module-name]: [location]

- **Type**: CPU/MEMORY/IO/LOCK
- **Estimated Impact**: ~XX% of module resources
- **Root Cause**: ...
- **Proposed Fix**: ...
- **Verified**: [ ] Yes / [ ] No
```
