# Smoke Scripts Index

Единый индекс smoke-сценариев для docs/CI. Массовые удаления скриптов делать только после миграции всех ссылок из:

- `.github/workflows/*.yml`
- `README.md`
- `docs/CI_AND_REPO_HYGIENE.md`
- `scripts/TEST_SERVER_READY.md`

Актуальные порты окружений: `docs/PORTS_MATRIX.md`.

## Port-sensitive smoke defaults

- `scripts/smoke-push-worker.sh` / `.ps1`:
  - `full-server`: `http://localhost:9194/health`
  - `dev-min --profile web`: `http://localhost:9193/health`
  - по умолчанию скрипты пробуют оба порта.
- `scripts/smoke-export-worker-metrics.sh` / `.ps1`: `http://localhost:9193/metrics` (export-replay).
- `scripts/smoke-retention-worker.ps1`: `http://localhost:9192/health` (retention).

## Canonical сценарии (использовать по умолчанию)

| Сценарий | Canonical script | CI usage | Параллельные обертки |
|---|---|---|---|
| Export compliance flow | `scripts/smoke-export-compliance-flow.sh` | `export-compliance-smoke.yml` | `.ps1`, `.cmd`, `smoke-export-compliance-with-file-flow.*` |
| Export compliance pack | `scripts/smoke-export-compliance-pack.sh` | `export-compliance-smoke.yml` | `.ps1`, `.cmd` |
| Export observability | `scripts/smoke-export-observability.sh` | `export-compliance-smoke.yml` | `.ps1`, `.cmd` |
| OpenAPI export compliance | `scripts/smoke-openapi-export-compliance.sh` | `export-compliance-smoke.yml` | `.ps1` |
| Korus web basic smoke | `scripts/smoke-korus-web.sh` | manual (runtime) | `.ps1`, `.cmd`; optional — spec 002 parity smokes cover API/WS |
| **Deploy acceptance (spec 003)** | `scripts/smoke-deploy-acceptance.sh` | `deploy-messaging-smoke.yml` | orchestrates ready, auth, messaging-e2e, parity-api |
| **Platform W2 guest (optional)** | `scripts/guest-smoke-platform-w2.sh` | manual (QEMU server guest) | `verify-nats-queue-group`; `KORUS_RUN_EXPORT_PURGE_SMOKE=1` for export-replay |
| **QEMU wsUrl probe (host)** | `scripts/test-korus-wsurl.ps1` | manual; outer gate preflight | expects `host-lan-ip.txt` + `:19088/web-client-env.js` |
| **Read replica env probe** | `scripts/smoke-read-replica-env.sh` | manual (guest) | after `replica-stack-up.sh` or `replica-lab-up.sh` |
| **k6 pilot baseline** | `scripts/load/pilot-health.js` | manual (host :18080) | see `scripts/load/README.md` |
| **Pilot stack (spec 006 FR-OPT-01)** | `scripts/smoke-pilot-stack.sh` | manual (QEMU server guest) | `scripts/pilot-stack-up.sh`; no Solr/ZK; SQL search |
| **Scale stack (spec 006 FR-OPT-04)** | `scripts/smoke-messaging-e2e.sh --load-rounds N` | manual (guest) | `scripts/scale-stack-up.sh`; `scripts/verify-nats-queue-group.sh`; `scripts/profiling/load-message-pipeline.sh` |
| **Enterprise stack (spec 006 FR-OPT-04/05)** | `scripts/smoke-messaging-e2e.sh --load-rounds N` | manual (guest) | `scripts/enterprise-stack-up.sh`; optional `KORUS_ENABLE_READ_REPLICA=1` + `replica-stack-up.sh` |
| **Multi-user messaging E2E** | `scripts/smoke-messaging-e2e.sh` | `deploy-messaging-smoke.yml` | `.ps1`; lib `SmokeMessaging.sh`; `--load-rounds` for Wave 2 load |
| Auth smoke | `scripts/smoke-auth.sh` | manual | `.ps1` |
| Stack readiness smoke | `scripts/smoke-ready.sh` | manual | `.ps1` |
| Retention worker health smoke | `scripts/smoke-retention-worker.ps1` | manual | none |
| US2 Epic01 (QEMU wrapper) | `scripts/smoke-us2-epic01-qemu.ps1` | manual | `smoke-us2-epic01.ps1` |
| Hot-plug indexer lifecycle | `scripts/smoke-hotplug-indexer.ps1` | manual | requires NATS (`14222` tunnel on QEMU) |
| Read receipts (API + WS) | `scripts/smoke-read-receipts.ps1` | manual | UI ✓✓ check optional |
| Retention hot-row purge status | `scripts/smoke-retention-purge.ps1` | manual | requires admin token + stack |
| Retention file cleanup metrics | `scripts/smoke-retention-file-cleanup.ps1` | manual | metrics on retention worker port |
| Web parity API (spec 002 T010/T016 backend) | `scripts/smoke-web-parity-api.sh` | manual | `.ps1`; pin API may 500; UI/WS gates still manual |
| **TLS redirect (spec 003 Phase B / 004 US1)** | `scripts/smoke-tls-redirect.ps1` | manual; Ansible `--tags tls_smoke` via `korus_smoke` role | stage/prod with `korus_tls_enabled`; `-SkipTls` for dev |
| **Playwright parity matrix** | `tests/e2e-web/` (9 specs) | `deploy-messaging-smoke.yml` (optional nightly job, `continue-on-error`) | spec 004 US5 T110–T115; operator template `specs/002-web-client-server-parity/runtime-gate-report.md` |

## Operator utilities

- `scripts/ensure-qemu-images.ps1` — download Ubuntu cloud image if missing (OS images are not in git)
- `scripts/stop-local-indexer.ps1` — stop orphan `:services:indexer:run` after smoke/manual runs (Windows).
- `scripts/publish-spec-001-branch.ps1` — push branch `001-system-review-refactoring` when GitHub is reachable.
- `scripts/apply-hotplug-signoff.ps1` — record ADR/constitution approvals with **real approver names** (T048/T056, spec 004 T180); see `docs/adr/ADR-hotplug-deployment-split.md` Approval Log section.
- `scripts/profiling/profile-docker-jfr.ps1` — JFR inside JDK profiling containers (`docker-compose.profiling.yml`).

## Export / retention extended scenarios

Используются для точечных проверок, не являются обязательными в CI по умолчанию:

- `scripts/smoke-export-chat.sh` / `.ps1`
- `scripts/smoke-export-chat-cancel.sh` / `.ps1`
- `scripts/smoke-export-chat-request-cancel.sh` / `.ps1`
- `scripts/smoke-export-suggested.sh` / `.ps1`
- `scripts/smoke-export-suggested-nats.sh` / `.ps1`
- `scripts/smoke-export-suggest-flow.sh` / `.ps1`
- `scripts/smoke-export-replay-before-purge.ps1` — export_v1 gate before purge (ROADMAP §1)
- `scripts/smoke-retention-solr-clear.ps1` — Solr content_txt clear after retention (ROADMAP §1)
- `scripts/audit-timing.ps1` — timing audit → `docs/SECURITY_AUDIT.md` (ROADMAP §5)
- `scripts/smoke-export-suggest-cancel-flow.sh` / `.ps1`
- `scripts/smoke-export-auto-queue-nats.sh` / `.ps1`
- `scripts/smoke-retention-export-suggested.sh` / `.ps1`
- `scripts/smoke-retention-export-suggested-full.ps1`
- `scripts/smoke-deep-archive-chunks.ps1`
- `scripts/smoke-ttl-ui.ps1`
- `scripts/smoke-export-worker-metrics.sh` / `.ps1`
- `scripts/smoke-admin-export.sh` / `.ps1`
- `scripts/smoke-admin-export-cancel.sh` / `.ps1`
- `scripts/smoke-admin-export-request-cancel.sh` / `.ps1`
- `scripts/smoke-admin-export-download.sh` / `.ps1`
- `scripts/smoke-admin-export-inspect.sh` / `.ps1`
- `scripts/smoke-admin-export-global-jobs.sh` / `.ps1`
- `scripts/smoke-admin-export-compliance-prep.sh` / `.ps1`
- `scripts/smoke-export-compliance-stack.sh` / `.ps1`
- `scripts/smoke-export-compliance-with-file-flow.sh` / `.ps1` / `.cmd`

## Deprecation policy

- До отдельного cleanup PR ничего не удалять.
- Если сценарий дублируется (`.sh`, `.ps1`, `.cmd`), canonical для CI/документации выбирается в пользу `.sh`.
- `.ps1` и `.cmd` остаются для Windows-операторов до явной миграции.
