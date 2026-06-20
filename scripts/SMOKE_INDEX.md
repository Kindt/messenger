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
| Export compliance flow | `scripts/smoke-export-compliance-flow.sh` | `export-compliance-smoke.yml` | `.ps1`; file attach: `flow.sh --include-file` / `flow.ps1 -IncludeFile` |
| Export compliance pack | `scripts/smoke-export-compliance-pack.sh` | `export-compliance-smoke.yml` | `.ps1` |
| Export observability | `scripts/smoke-export-observability.sh` | `export-compliance-smoke.yml` | `.ps1` |
| OpenAPI export compliance | `scripts/smoke-openapi-export-compliance.sh` | `export-compliance-smoke.yml` | `.ps1` |
| Korus web basic smoke | `scripts/smoke-korus-web.sh` | manual (runtime) | `.ps1`; optional — parity smokes cover API/WS |
| **Container portability WAR (021)** | `scripts/smoke-container-portability-guest.ps1` | manual (QEMU server guest); poll `qemu-guest-job.ps1 -Job container-portability-smoke -Loop` | `smoke-core-api-war-guest.sh` + `smoke-ws-gateway-war-guest.sh` on compose network |
| **core-api WAR Tomcat (host lab only)** | `scripts/smoke-core-api-jetty.ps1` | optional host Docker (not QEMU policy) | Isolated Tomcat; prefer guest script above |
| **Deploy acceptance (spec 003)** | `scripts/smoke-deploy-acceptance.sh` | `deploy-messaging-smoke.yml` | orchestrates ready, auth, messaging-e2e, parity-api |
| **Platform W2 guest (optional)** | `scripts/guest-smoke-platform-w2.sh` | manual (QEMU server guest) | `verify-nats-queue-group`; `KORUS_RUN_EXPORT_PURGE_SMOKE=1` for export-replay |
| **QEMU wsUrl probe (host)** | `scripts/test-korus-wsurl.ps1` | manual; outer gate preflight | expects `host-lan-ip.txt` + `:19088/web-client-env.js` |
| **Read replica env probe** | `scripts/smoke-read-replica-env.sh` | manual (guest) | after `replica-stack-up.sh` or `replica-lab-up.sh` |
| **k6 pilot baseline** | `scripts/load/pilot-health.js` | manual (host :18080) | see `scripts/load/README.md` |
| **k6 QEMU baseline (T604 substitute)** | `scripts/run-k6-qemu-baseline.ps1` | manual | writes `deploy/qemu/run/k6-pilot-baseline.json`; fallback if k6 absent |
| **WS soak load (PS-4.1)** | `scripts/load-ws-soak.ps1` / `.sh` | manual (host / guest) | N connections, 5 min; metrics `:9198` host / `:9191` guest |
| **QEMU load gate wrapper** | `scripts/load-ws-soak-qemu.ps1` | manual | sync-api + upload + fanout + WS soak on guests |
| **API upload load (PS-4.1)** | `scripts/load-api-upload.ps1` | manual (host `:18080`) | parallel streaming uploads |
| **Fan-out synthetic (PS-4.1)** | `scripts/load-fanout-synthetic.sh` | manual (server guest) | burst DM + pipeline metrics |
| **Lean stack (spec 006 FR-OPT-01)** | `scripts/smoke-lean-stack.sh` | manual (QEMU server guest) | `scripts/lean-stack-up.sh` (legacy: `pilot-stack-up.sh`); no Solr/ZK; SQL search |
| **Scale stack (spec 006 FR-OPT-04)** | `scripts/smoke-messaging-e2e.sh --load-rounds N` | manual (guest) | `scripts/scale-stack-up.sh`; `scripts/verify-nats-queue-group.sh`; `scripts/profiling/load-message-pipeline.sh` |
| **Enterprise stack (spec 006 FR-OPT-04/05)** | `scripts/smoke-messaging-e2e.sh --load-rounds N` | manual (guest) | `scripts/enterprise-stack-up.sh`; optional `KORUS_ENABLE_READ_REPLICA=1` + `replica-stack-up.sh` |
| **Multi-user messaging E2E** | `scripts/smoke-messaging-e2e.sh` | `deploy-messaging-smoke.yml` | `.ps1`; lib `SmokeMessaging.sh`; `--load-rounds` for Wave 2 load |
| **Security gate (spec 014 PR + QEMU)** | `scripts/security-gate.ps1` | local / manual | `buildIntegrity` + optional headers/rate-limit/timing smokes |
| Stack readiness smoke | `scripts/smoke-ready.sh` | manual | `.ps1` |
| Retention worker health smoke | `scripts/smoke-retention-worker.ps1` | manual | none |
| US2 Epic01 (QEMU wrapper) | `scripts/smoke-us2-epic01-qemu.ps1` | manual | `smoke-us2-epic01.ps1` |
| Hot-plug indexer lifecycle | `scripts/smoke-hotplug-indexer.ps1` | manual | requires NATS (`14222` tunnel on QEMU) |
| Bot-delivery worker (guest) | `scripts/smoke-bot-delivery-worker.ps1` | manual | QEMU server guest via SSH; profile `push`/`full` |
| **Push-worker (QEMU guest)** | `scripts/smoke-push-worker-qemu.ps1` | manual | server guest `:9194/health` via SSH `:12221` |
| **Preview-worker (QEMU guest)** | `scripts/smoke-preview-worker-qemu.ps1` | manual | server guest `:9195/health` via SSH `:12221` |
| **Plugin integrations gate (spec 014)** | `scripts/smoke-integrations-gate.ps1` | manual (QEMU `-WithIntegrations`) | host forwards :18088–:18097, :18190 |
| **Integrations preflight (offline/online)** | `scripts/integrations-gate-preflight.ps1 [-Online]` | manual | before live stand |
| **Sync integrations guest** | `scripts/qemu-sync-integrations.ps1 [-MocksOnly]` | manual | refresh repo + compose on `.30` |
| **Live streaming L2 (spec 013)** | `scripts/smoke-live-session.ps1` | manual | host `:18080`; needs `V034` + LiveKit env |
| **LiveKit tunnel (QEMU, no VM restart)** | `scripts/livekit-host-tunnel.ps1` | manual | host `:17880` -> guest `:7880`; parallel-agent friendly |
| **Plugin platform (spec 014, integrations VM)** | `scripts/smoke-plugin-qemu.ps1` | manual | `qemu-integrations-up.ps1` first; ports 18088–18096 |
| **Plugin echo PHP** | `scripts/smoke-plugin-echo-php.ps1` | manual | `-BaseUrl http://127.0.0.1:18088` |
| **Plugin exchange/storage/ocr/ai** | `scripts/smoke-plugin-{exchange,storage,ocr-mock,ai-triage}.ps1` | manual | host forwards from integrations guest |
| Read receipts (API + WS) | `scripts/smoke-read-receipts.ps1` | manual | UI ✓✓ check optional |
| Retention hot-row purge status | `scripts/smoke-retention-purge.ps1` | manual | requires admin token + stack |
| Retention file cleanup metrics | `scripts/smoke-retention-file-cleanup.ps1` | manual | metrics on retention worker port |
| Web parity API (spec 002 T010/T016 backend) | `scripts/smoke-web-parity-api.sh` | manual | `.ps1`; pin API may 500; UI/WS gates still manual |
| **TLS redirect (spec 003 Phase B / 004 US1)** | `scripts/smoke-tls-redirect.ps1` | manual; Ansible `--tags tls_smoke` via `korus_smoke` role | stage/prod with `korus_tls_enabled`; `-SkipTls` for dev |
| **Stage preflight (spec 007 T601)** | `scripts/preflight-stage-deploy.ps1` | manual (Windows host) | checklist + `validate-stage-inventory.ps1` |
| **Stage TLS wrapper** | `scripts/stage-tls-smoke.ps1` | manual | reads `korus_tls_domain` from inventory |
| **E2EE staging partial** | `scripts/smoke-e2ee-staging.ps1` | manual | `-AdminToken` for migrate-batch |
| **k6 stage baseline** | `scripts/run-k6-stage-baseline.ps1` | manual | delegates to `run-k6-qemu-baseline.ps1` |
| **TURN reachability** | `scripts/smoke-turn.ps1` | manual | TCP 3478 + optional `web-client-env.js` ICE |
| **TURN relay ICE config** | `scripts/smoke-turn-relay.ps1` | manual | Extends smoke-turn; credential in env.js |
| **TURN (QEMU)** | `scripts/smoke-turn-qemu.ps1` | manual | inner gate: `-GuestOnly`; full probe needs web VM hostfwd `:3478` |
| **Cell multi-org (spec 011)** | `scripts/smoke-cell-multi-org-qemu.ps1` | manual | host `:18080`; creates 2 orgs via admin API |
| **Migration import (spec 022 US9)** | `scripts/smoke-migration-import.ps1` | manual | host `:18080`; create + process telegram fixture |
| **DLP mock bridge (spec 022 US7)** | `scripts/smoke-dlp-mock.ps1` | manual | `:8098` or `DLP_MOCK_URL`; block verdict |
| **Federation trust (spec 022 T02308)** | `scripts/smoke-federation-trust.ps1` | manual | host `:18080`; admin trust + platform status |
| **Phase 5 messaging (spec 022 T02301/03/06)** | `scripts/smoke-phase5-messaging.ps1` | manual | host `:18080`; poll + scheduled + reminder API |
| **GDPR export completeness (P1-6)** | `scripts/smoke-export-gdpr-fulfillment.ps1` | manual | admin export-compliance-guide + parity API |
| **File image resize (P1-4)** | `scripts/smoke-file-resize.ps1` | manual | host `:18080`; upload PNG → `/resize?w=32&h=32` |
| **Export-replay non-stub (P2-4)** | `scripts/smoke-export-replay-non-stub.ps1` | manual | export_v1 gate via `smoke-export-replay-before-purge` |
| **SSO Keycloak broker (P2-2)** | `docs/runbooks/sso-keycloak-federation.md` | manual | `scripts/keycloak-enable-identity-provider.sh` on IdP host |
| **LDAP/AD federation (P2-3)** | `docs/runbooks/sso-keycloak-federation.md` § LDAP | manual | `scripts/keycloak-enable-ldap-federation.sh` on Keycloak host |
| **Preview worker health** | `scripts/smoke-preview-worker.ps1` | manual | full-server `:9195/health`; QEMU: `smoke-preview-worker-qemu.ps1` |
| **Playwright staging gate** | `scripts/playwright-staging-gate.ps1` | manual | `-BaseUrl https://...` |
| **Stage/prod deploy runbook** | `docs/review/stage-prod-deploy-runbook.md` | manual | US1/US7 deploy-only when hosts available |
| **Playwright parity matrix** | `tests/e2e-web/` (9 specs) | `deploy-messaging-smoke.yml` (optional nightly job, `continue-on-error`) | spec 004 US5 T110–T115; operator template `docs/parity/runtime-gate-report.md` |

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
- `scripts/smoke-export-compliance-flow.sh --include-file` / `scripts/smoke-export-compliance-flow.ps1 -IncludeFile`

## Deprecation policy

- Canonical для CI/документации: `.sh`; Windows-операторы: `.ps1`.
- Удаление deprecated `.ps1` — только после миграции ссылок (см. spec 008 T202).
