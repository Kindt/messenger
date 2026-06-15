# US2 Runtime Runbook — 2026-05-23

Status: blocked in current environment due missing container runtime (`docker` not found).

Update (QEMU path): baseline US2 smoke can also run against `qemu-up` stack via SSH tunnels (no local Docker on host required).

## Goal

Close runtime-dependent tasks:
- `T023` Solr atomic update verification
- `T024` Prometheus metrics verification
- `T025` chunked deep-archive verification
- `T026` file-ref skip verification
- `T045` smoke scripts execution
- `T047` mark phase-B plan as completed (after successful checks)

## Preconditions

1. Docker Desktop (or compatible `docker compose`) available in PATH.
2. Ports free: `8080`, `9192`, `9193`, `9194`, and stack defaults from compose files.
3. Local repo root: `D:\proj\korus_messenger`.

## One-shot execution

```powershell
./scripts/full-stack-up.ps1 -ExportSmoke -WaitReady -SkipEnsure
./scripts/smoke-us2-epic01.ps1
./scripts/full-stack-down.ps1 -ExportSmoke
```

## One-shot with auto-report

```powershell
./scripts/us2-verify-and-record.ps1 -RunSmoke -AutoUp -AutoDown
```

## QEMU execution (no host Docker)

When server/web VMs are already up (`./scripts/qemu-up.ps1`):

```powershell
./scripts/smoke-us2-epic01-qemu.ps1 -SkipHotPlug -LenientObservability
```

What it does:
- opens temporary SSH tunnels to server VM metrics ports (`9192`, `9193`);
- runs `smoke-us2-epic01.ps1` against API `http://127.0.0.1:18080`;
- closes tunnels automatically.

Notes:
- `-LenientObservability` is useful when export-admin metrics are not emitted yet on a fresh stack.
- Strict manual checks for `T023/T025/T026` still remain required.
- For strict `T024` on a fresh stack, generate at least one export and one cancel first:
  - `./scripts/smoke-export-chat.ps1 -BaseUrl "http://127.0.0.1:18080"`
  - `./scripts/smoke-export-chat-cancel.ps1 -BaseUrl "http://127.0.0.1:18080" -ChatId "<chat_id>" -SkipAudit`

## QEMU strict-mode prep (for T023/T025/T026)

To enable admin export prep and faster retention/deep-archive strict checks on server VM:

```bash
docker compose -f docker/docker-compose.full-server.yml \
  -f docker/docker-compose.lan-publish.yml \
  -f docker/docker-compose.us2-verify.yml up -d core-api retention-worker archiver-worker deep-archiver-worker indexer-worker
```

Then run:
- `smoke-us2-epic01-qemu.ps1` (baseline metrics/readiness)
- strict message-level checks (Solr clear / MinIO chunk manifest / file-ref skip).

## QEMU smoke pack (Windows, practical baseline)

```powershell
./scripts/smoke-ready.ps1 -BaseUrl "http://127.0.0.1:18080" -StrictDependencies
./scripts/smoke-auth.ps1 -BaseUrl "http://127.0.0.1:18080"
./scripts/smoke-korus-web.ps1 -WebBaseUrl "http://127.0.0.1:19088" -CheckApi
./scripts/smoke-openapi-export-compliance.ps1 -BaseUrl "http://127.0.0.1:18080"
./scripts/smoke-export-observability.ps1 -CoreMetricsUrl "http://127.0.0.1:18080/api/v1/metrics/prometheus" -WorkerMetricsUrl "http://127.0.0.1:19193/metrics" -RetentionMetricsUrl "http://127.0.0.1:19192/metrics"
./scripts/smoke-export-compliance-flow.ps1 -BaseUrl "http://127.0.0.1:18080"
./scripts/smoke-export-compliance-pack.ps1 -BaseUrl "http://127.0.0.1:18080" -WorkerMetricsUrl "http://127.0.0.1:19193/metrics" -RetentionMetricsUrl "http://127.0.0.1:19192/metrics" -NatsUrl "nats://127.0.0.1:14222" -SkipObservability -SkipDownload -SkipAudit
```

Notes:
- Start temporary tunnel before NATS-based smoke steps:
  - `plink -N -batch -pw korus -P 12221 -L 14222:127.0.0.1:4222 korus@127.0.0.1`
- `nats` CLI must be available in host PATH for NATS-based scripts.
- For strict `T024`, generate at least one export and one cancel before observability check (see section above).

## Verified strict evidence (QEMU, 2026-05-24)

- `T023` Solr atomic update:
  - message id `53f052c4-17e2-4d69-8896-49519b6730e5`
  - PostgreSQL `messages.content IS NULL = true`
  - Solr `messages_meta` doc has `content_txt=[""]` (cleared after retention/index update)
- `T025` chunked deep-archive:
  - message id `a2e90882-e57f-46d7-96a4-1a79cc7a9880`
  - MinIO objects: `messages/{id}/manifest.json`, `part-000.json` ... `part-004.json`
  - deep-archiver log: `Wrote 5 chunks for message ...`
- `T026` file-ref skip:
  - fixed in `DeepArchiverWorker` by checking `searchText` (worker payload field) with legacy fallback
  - verification message id `a8a6f036-5daf-4611-b981-12f94bec6b6f`
  - MinIO: no `messages/{id}.json`, no `retention/body/{id}.json`
  - deep-archiver log: `Skipped deep-archive for message ...: content is file reference`

## Notes

- `smoke-us2-epic01.ps1` already orchestrates core US2 smoke checks and fails fast on missing endpoints.
- `smoke-hotplug-indexer.ps1` was fixed for PowerShell parameter conflict (`Host` -> `HostName`).
- If stack is already up, run only:

```powershell
./scripts/smoke-us2-epic01.ps1
```

## Acceptance recording

After successful run:
1. Mark `T023`, `T024`, `T025`, `T026`, `T045` as complete in `specs/001-system-review-refactoring/tasks.md`.
2. Update `docs/plans/01-retention-phase-b.md` status to `completed` and mark final checkboxes.
3. Mark `T047` complete.
4. Fill `docs/review/us2-verification-template-2026-05-23.md` with concrete evidence links/outputs.
