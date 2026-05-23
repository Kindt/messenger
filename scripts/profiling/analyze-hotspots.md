# Hotspot Analysis Guide

## Tools

1. **JDK Mission Control (JMC)** — open `.jfr` files, use "Flight Recording" view
2. **async-profiler** — for wall-clock profiling: `profiler.sh -e wall -d 60 -f profile.html <pid>`

## What to Look For

### CPU Hotspots
- Hot Methods tab → sort by "CPU Time"
- Methods with >10% CPU time are top candidates
- Pay attention to: JSON serialization, MinIO operations, Solr requests

### Memory / Allocation Hotspots
- Allocation Profile tab → sort by "Allocation Size"
- Methods generating excessive byte arrays or strings
- Look for: `byte[]`, `String`, `ObjectNode` allocations

### Lock Contention
- Lock Instances tab → threads blocked on locks
- Synchronized blocks in hot paths
- Database connection pool contention

### GC Pressure
- GC Pauses tab → pause frequency and duration
- If GC > 10% of CPU time → allocation optimization needed

## Documenting Findings

Use this Markdown template for each hotspot:

```markdown
## [Module]: [ClassName.methodName()]

| Field | Value |
|-------|-------|
| Hotspot Type | CPU/MEMORY/IO/LOCK |
| Estimated Impact | ~XX% of module CPU/heap |
| Root Cause | ... |
| Proposed Fix | ... |
| Verified | [ ] |
```
