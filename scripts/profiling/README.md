# Profiling Scripts

These scripts facilitate JFR (JDK Flight Recorder) profiling of Korus Messenger services.

## Prerequisites

- JDK 25 (JFR is built-in)
- JDK Mission Control (`jmc`) for analyzing recordings
- Optional: async-profiler for wall-clock profiling

## Usage

### Profile core-api

```powershell
.\scripts\profiling\profile-core-api.ps1 [-DurationSeconds 60] [-OutputDir ./jfr-recordings]
```

### Profile a specific worker

```powershell
.\scripts\profiling\profile-worker.ps1 -WorkerName deep-archiver [-DurationSeconds 60] [-OutputDir ./jfr-recordings]
```

Supported WorkerName values: `deep-archiver`, `retention`, `indexer`, `export-replay`, `archiver`.

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
