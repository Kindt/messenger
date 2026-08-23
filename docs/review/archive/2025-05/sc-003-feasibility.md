# SC-003 Feasibility Note

Status: `partial-achieved-core-api`  
Source plan task: `T052`  
Updated: 2026-05-24

## Target

SC-003 requires at least **15% heap reduction** on same load, or explicit documented rationale why not achievable.

## Current Assessment

**Partially achieved** for **core-api** under QEMU synthetic load:

- Post-load average heap: **126.9 MB → 49.0 MB (−61%)** after `RedisProbe` fix
- Load: `scripts/profiling/load-core-api.ps1` (15s, concurrency 3)

Workers still need Prometheus/JFR profiling passes.

## Decision

- **Achieved (core-api probe path):** yes
- **Achieved (full platform):** not yet
- **Blocker:** JRE containers lack `jcmd`; use Prometheus or JDK-based image for JFR
- **Next iteration:** profile export-replay under export load; deep-archiver/indexer need metrics port or JDK image for JFR

## Retention streaming fix (2026-05-24)

`writeChunkedSnapshotFromFile` implemented — large tempfile chunk path no longer loads full file into heap before upload.
