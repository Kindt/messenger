# Журнал изменений (AvandocMsg / Korus Messenger)

Формат: по **дате и времени (UTC)** фиксируются сделанные изменения и заметные **отклонения от базового ТЗ** (`tz_full.html`). Дополнения из `tz_revision_proposal.md` учитываются как целевое развитие, но полное соответствие им не гарантируется, пока не отмечено явно.

**Важно:** время в записях — ориентир хронологии внутри дня (или момент фиксации вручную). При необходимости укажите часовой пояс в коммит-процессе или подставьте время из `git log`.

---

## [Unreleased]

### 2026-06-15 — Roadmap post–spec 007: §12 closure + next priorities

- **`docs/ROADMAP_EPICS.md`**: §7 presentation §12 note; new §8 priority table (stage TLS, E2EE, k6, FR-OPT-08/09).
- **`docs/plans/2026-06-15-unfinished-development-plan.md`**: load test note aligned with §12 presentation closure.

### 2026-06-15 — §12 product presentation closure (FR-OPT 01–07)

- **`docs/PRODUCT_PRESENTATION.md`**, **`product_presentation.html` v2.3**: §12 → реализовано (волны 1–3); traceability и §9 infra обновлены; 08–09 + formal load test — roadmap.

### 2026-06-15 — Close plan #12 + spec 007 platform-stage-readiness (engineering)

- **`specs/007-platform-stage-readiness/`**: spec/plan/tasks/contracts/quickstart; Phases 1–5 [x], ops Phase 6–7 open.
- **`docs/plans/2026-06-15-unfinished-development-plan.md`**: status `completed` (engineering); §10 closure; DoD updated.
- **`docs/plans/README.md`**: row 12 → `completed`.

### 2026-06-15 — W2/W3 hybrid sprint: stage prep, k6, replica lab, hex register, push i18n

- **W2 T-P1:** `stage-tls-smoke-runbook.md`, `e2ee-security-signoff-packet-2026-06-15.md`, k6 `scripts/load/pilot-*.js`.
- **W3 T-P2:** `docker/REPLICA_LAB.md`, `replica-lab-up.sh`, `smoke-read-replica-env.sh`; `UserRepositoryPort.createLocalUser`; push preview i18n bundles.

### 2026-06-15 — W2 platform hardening: wsUrl force redeploy + P1 prep kit

- **wsUrl remediate:** `Start-KorusQemuGuestRedeploy -Force` when reason is wsUrl mismatch; auto-remediate + plan orchestrator use it.
- **`scripts/test-korus-wsurl.ps1`**: host probe for `web-client-env.js` vs `host-lan-ip.txt`.
- **`scripts/guest-smoke-platform-w2.sh`**: guest gate (NATS queue group + optional export-replay).
- **Stage prep:** `inventory/stage/README.md`, `vault.yml.example`; E2EE + hotplug sign-off templates in `docs/review/`.
- **Hex 2b:** `MessageResource.edit` ACL via `MessageApplicationService.getMessageForMember` before write.

### 2026-06-15 — W1 platform hardening: redeploy lock PID + SSH host key refresh

- **`deploy/qemu/lib/Korus-QemuRedeployLock.ps1`**: PID-aware stale lock detection; shared by redeploy, auto-remediate, monitored redeploy.
- **`Get-KorusEd25519HostKey`**: validates cached key via plink; re-probes and updates `ssh-hostkeys.ps1` on mismatch.
- **`ops-signoff-log.md`**: Playwright counts synced to **30/30** (2026-06-15).
- **`ROADMAP_EPICS.md`**, infra design doc § Related changes: spec 006 closure noted.

### 2026-06-15 — product_presentation §18: методика тарифов

- **`scripts/tz_product_pricing.py`**: единый источник ставок §18.1 (дата 2026-06-15), формула «кол-во × цена за ед.»; диаграммы Рис. 7–8 и таблицы генерируются из него.
- Baseline Pilot (full-server) пересчитан по строкам §18.1 (**109 550 ₽/мес**, не округление 96 000).
- Под легендами Рис. 7 и §18.7 — откуда взяты ставки, дата и источник категории.

### 2026-06-15 — outer gate green + korus-web env recreate on redeploy

- **`runtime-gate-report.md`**: outer Playwright **30/30** on QEMU (2026-06-15).
- **`korus_web` Ansible**: `--force-recreate` when `.env` template changes (wsUrl pickup after LAN IP change).
- **`korus-web-up.sh`**: flag `--force-recreate` / `-r`.

### 2026-06-15 — product_presentation.html: публикация для заказчика

- **`product_presentation.html` v2.2**: презентация продукта (не техническое ТЗ); §1–18, приложения, иллюстрации.
- **`tz_product.html`**: редирект на `product_presentation.html`.
- **`docs/PRODUCT_PRESENTATION.md` v1.5**: исходник Markdown; ссылки в `DEV_STACK_PROFILES`, `RESOURCES`.
- **`scripts/build-tz-product-html.py`**, **`tz_product_resources.py`**: сборка HTML и каталог API/клиента.

### 2026-06-15 — spec 006 Wave 3 + Wave 2 closure: zstd archive, batch Solr, cache/replica/scale

- **T301–T303:** `SnapshotPartCodec` (KDA1+zstd), `ChunkedSnapshotWriter`/`DeepArchiveReader`; `deep_archive_bytes_saved_total`; env `DEEP_ARCHIVE_COMPRESSION`.
- **T304–T305:** `IndexerBatchBuffer`, `INDEXER_BATCH_SIZE` / `INDEXER_BATCH_FLUSH_MS`, batch Solr metrics.
- **T306–T307:** NATS `msg.cache.invalidate` (pipeline → core-api); chat-list invalidation in `ChatService`.
- **T308–T310:** `replica-stack-up.sh`, `enterprise-stack-up.sh`, Ansible `enterprise` profile, `nginx.conf.scale.template`.

### 2026-06-15 — spec 006 Wave 3 guest gate T311 green

- Guest: `sync-workers` (~246s), `enterprise-stack-up`, scale replica refresh, messaging smokes green.

### 2026-06-15 — spec 006 Wave 3 guest tooling: qemu-sync-workers

- **`rebuild-workers-guest.sh`**, **`qemu-sync-workers.ps1`**, **`qemu-dev-mode -Mode sync-workers`** — пересборка pipeline/indexer/deep-archiver на QEMU guest.
- **`docs/NATS_SUBJECTS_INTEROP.md`**: `msg.cache.invalidate`; **`SMOKE_INDEX.md`**: enterprise stack row.

### 2026-06-15 — spec 006 Wave 2 guest smokes: E2EE-safe messaging scripts

- **`SmokeMessaging.sh`**: polling по `message_id` / count (E2EE `e2ee-text`); `smoke_mark_read` → `POST /read-batch`.
- **`smoke-messaging-e2e.sh`**, **`load-message-pipeline.sh`**: delivery checks без plaintext needle.
- **`scale-stack-up.sh`**: tag образов + `sudo` fallback для QEMU guest (без buildx).
- **`verify-nats-queue-group.sh`**: точные имена `docker-message-pipeline-1` / `docker-message-pipeline-2-1`.
- Guest gate (QEMU `korus-server`): `verify-nats-queue-group`, `load-message-pipeline`, `smoke-messaging-e2e --load-rounds 3` — green.

### 2026-06-15 — spec 006 Wave 2 T202–T206: cache integration, scale, read replica

- **T202 (FR-OPT-03):** `ReadCacheCoordinator`, `ReadCacheMetrics` (`read_cache_hit_total` / `read_cache_miss_total`); cache-aside в `ChatService`, `UserApplicationService`; invalidation на write-path (`MessageService`, `ReadReceiptService`, profile/presence updates).
- **T203 (FR-OPT-04):** `docker/docker-compose.scale.yml` — 2× `message-pipeline`, 2× `ws-gateway`, `API_REPLICAS=2`; sticky WS notes в `korus-web/README.md`.
- **T204:** `scripts/verify-nats-queue-group.sh`, `scripts/profiling/load-message-pipeline.sh`, `scripts/scale-stack-up.sh`.
- **T205 (FR-OPT-05):** optional read pool в `DatabaseConfig` / `ChatRepository` / `MessageRepository`; lab overlay `docker/docker-compose.replica.yml`.
- **T206:** `scripts/smoke-messaging-e2e.sh --load-rounds N` для нагрузочного прогона на scaled stack.

### 2026-06-15 — spec 006 Wave 2 T201: ReadCachePort + Redis adapter (FR-OPT-03)

- **`ReadCachePort`**, **`ReadCacheKeys`**, **`ReadCacheKind`**: cache-aside hex port; key patterns `chat:list`, `chat:unread`, `user:profile`, `user:presence`.
- **`RedisReadCacheAdapter`**, **`NoOpReadCacheAdapter`**: Lettuce SETEX/DEL, fail-open; opt-in via `REDIS_READ_CACHE_ENABLED`.
- **`AppConfig`**, **`CoreModule`**, HK2 bind in **`JerseyConfig`**; Redis client shared with rate limiter when cache enabled.
- Unit tests: **`RedisReadCacheAdapterTest`**, **`ReadCacheKeysTest`**.

### 2026-06-15 — docs: справочник профилей стендов (dev / full / pilot)

- **`docs/DEV_STACK_PROFILES.md`**: три оси (QEMU disk, Ansible deploy, product tier); матрицы full-server / pilot / dev-min; Keycloak dev vs prod; «что выбрать».
- **`deploy/qemu/README.md`**, **`AGENTS.md`**, **`RESOURCES.md`**: ссылки на справочник.
- **QEMU inventory:** default deploy profile снова **standard** (full-server); pilot — явный override.

### 2026-06-15 — spec 006 Wave 1: pilot compose + Keycloak prod (FR-OPT-01/02)

- **`docker/docker-compose.pilot.yml`**: 8 core services + optional profiles (`push`, `retention`, `compliance`, `archive`); без Solr/ZK; `SEARCH_MODE=sql`, `MLS_WIRE_ENABLED=false` для infra-smokes.
- **`docker/docker-compose.keycloak-prod.yml`** + **`docker/Dockerfile.keycloak-prod`**: Keycloak `start --optimized`; heap `-Xmx256m`, `mem_limit=512m` (RSS ~335 MiB на gate).
- **`scripts/pilot-stack-up.sh`**, **`scripts/smoke-pilot-stack.sh`**: guest helper + acceptance (health, DM/WS, SQL search).
- **Ansible**: `korus_deploy_profile: pilot|standard|enterprise`; role `korus_server` выбирает pilot vs full-stack.
- **`deploy/qemu/RESOURCES.md`**: sizing Pilot + Keycloak dev vs prod; gate RAM ~2 GiB used на guest 9.7 GiB.
- **`AppConfig`**: env `SEARCH_MODE=sql|solr` (explicit Solr toggle).
- **`scripts/lib/SmokeMessaging.sh`**: fix Python 3 `print()` return в JSON helpers.

### 2026-06-15 — tz_product v2.1: приложение I (каталог ресурсов API и клиента)

- **`scripts/tz_product_resources.py`**: каталог 20 JAX-RS resources, ws-gateway `/ws`, webui/, admin-ui/, web-client servlets; генерация HTML/MD.
- **`tz_product.html` v2.1**: приложение I с относительными ссылками на исходники; TOC `#app-i`.
- **`docs/TZ_PRODUCT_ALTERNATIVE.md` v1.4**: то же приложение I для IT.

### 2026-06-15 — tz_product.html v2.0: автономная публикация для заказчика

- **`tz_product.html`**: полный единый документ v2.0 — §1–18, 7 SVG-иллюстраций, §18 примеры стоимости (Pilot/Standard/TCO/диск) для продаж и бухгалтерии; без внешних ссылок.
- **`scripts/build-tz-product-html.py`**: генератор HTML из шаблонов (пересборка при обновлении MD).

### 2026-06-15 — TZ v1.3: §13 интеграции, приложения G/H, глоссарий для юристов

- **`docs/TZ_PRODUCT_ALTERNATIVE.md`**: §13 FR-INT-01…08 (REST/WS, SSO, Bot API, паттерны); приложение G (assumptions/risks); приложение H (one-pager vs tz_full); расширение B.2; §11 п.11.
- **`tz_product.html`**: §13 в TOC и теле; приложения B/G/H; v1.3.

### 2026-06-15 — TZ v1.2: §14 compliance, §15 SLA, §16 профили UX

- **`docs/TZ_PRODUCT_ALTERNATIVE.md`**: FR-COMP-01…08, FR-SLA-01…04, FR-PROF-01…02; приложение F (чеклист go-live); §11 п.8–10.
- **`tz_product.html`**: §14–16 в TOC.

### 2026-06-15 — TZ v1.1: §12 оптимизация инфраструктуры (spec only)

- **`docs/TZ_PRODUCT_ALTERNATIVE.md`**: §12 требования FR-OPT-01…012, пересечения волн, метрики приёмки; §11 п.7; приложение E; §9 infra row.
- **`docs/plans/2026-06-15-infra-optimization-design.md`**: статус «приложение к ТЗ §12», реализация отложена.
- **`tz_product.html`**: §12 в TOC.

### 2026-06-15 — Infra optimization design + TZ sizing profiles

- **`docs/plans/2026-06-15-infra-optimization-design.md`**: roadmap снижения RAM/диска и роста throughput (9 этапов: Pilot compose → sharding).
- **`docs/TZ_PRODUCT_ALTERNATIVE.md`**: §10.1.1 профили Pilot/Standard/Enterprise; §10.2.1 оптимизированные конфигурации.

### 2026-06-15 — Альтернативное продуктовое ТЗ

- **`docs/TZ_PRODUCT_ALTERNATIVE.md`**: полное продуктовое ТЗ для нетехнической аудитории — резюме, словарь, каталог возможностей, 27 кейсов использования, роли, безопасность, traceability к `tz_full.html`, roadmap развития, sizing-таблица 10k/100k/500k/1M пользователей.
- **`tz_product.html`**: HTML-публикация (TOC, status-tags, таблицы) — стиль `tz_full.html`.

- **`.github/workflows/ci.yml`**: `buildIntegrity` on push to any branch (not only main/develop).
- **`.github/workflows/deploy-messaging-smoke.yml`**: fixed `:modules:web-client:assemble`; Ansible bootstrap log under `/tmp`; failure diagnostics; stack down on `always()`.
- **`.github/workflows/export-compliance-smoke.yml`**: `keycloak-ensure-dev-users` before admin smokes; failure diagnostics; stack down on `always()`.
- **`docker/docker-compose.export-smoke.yml`**: `EXPORT_REPLAY_INCLUDE_FILE_BODIES=true` for `--include-file` compliance flow.
- **`deploy/ansible`**: configurable `korus_bootstrap_log` with writable-path fallback for CI localhost.
- **`scripts/ci-stack-diagnostics.sh`**: compose ps + tail logs for GHA failure steps.

### 2026-06-14 — Spec 005: web UI startup fix and i18n

- **`docker/Dockerfile.web-client`**: multi-stage Node 20 Tailwind build; Gradle skips `buildTailwindCss` in image (fixes guest bootstrap `npm` failure / UI :19088 down).
- **Spec `005-webui-i18n-ux`**: spec, plan, tasks, i18n contract; design `docs/plans/2026-06-14-webui-i18n-ux-design.md`.
- **`.cursor/skills/korus-webui`**: project skill for webui/Tailwind/i18n/QEMU acceptance; superpowers junctions refreshed.
- **i18n batches A–E**: modals, settings, thread/composer, time/TTL in `ui-format-utils.js`; default locale `ru`.
- **Phase 5 T050–T052**: i18n JSON architecture — source `webui-build/locales/messages/*.json`, lazy `fetch` in `ui-i18n.js`, removed six duplicate `locales/*.js`; design `docs/plans/2026-06-14-webui-i18n-json-architecture.md`.
- **Phase 4 complete**: kk/zh/ko full translations; `navigator.languages` regional detect; `translate="no"` on brand; Playwright locale-switch test in `ui-auth`.
- **Server UI locale**: `users.ui_locale`, `PATCH /api/v1/users/me/locale`; client sync on settings change; profile applies server preference only when set.
- **QEMU API fast path**: `qemu-dev-mode -Mode sync-api-core` (repo + `docker compose build core-api`, ~3 min); UI hotswap + `sync-ui`.
- **Acceptance**: `playwright-dev-loop.ps1 -Tier all-inner` green on QEMU (26 specs across inner tiers).

### 2026-06-14 — QEMU hotswap WS, sync-ui locales, backup

- **`docker-compose.hotswap-qemu.yml`**: nginx lb + `web-dev` overlay; `/ws` proxied to ws-gateway (fixes WS 404 in hotswap).
- **`New-KorusWebuiSnapshot`**: `npm run build:assets` (tailwind + locales) before `webui.tgz`.
- **`scripts/qemu-backup.ps1`**, **`scripts/qemu-restore.ps1`**: qcow2 backup/restore; **`scripts/git-push.ps1`**: GitHub push without corp proxy.

### 2026-06-12 — QEMU dev modes: sync default, facade, bootstrap phase

- **`qemu-redeploy.ps1`**: default `KORUS_BUILD=0` (sync); `-Rebuild` for full docker build; `-Force` skips ready preflight; guest bootstrap phase in wait-loop.
- **`scripts/qemu-dev-mode.ps1`**: unified entrypoint (`warm`, `sync-api`, `sync-ui`, `rebuild-*`, `status`, `stop`, `monitored`).
- **`Get-KorusGuestBootstrapPhase`**, **`Get-KorusQemuDevStatus`**: guest phase + host health for monitoring.
- **`qemu-redeploy-monitored.ps1`**: sync default, TCG fallback on early VM death, `-Rebuild`/`-Force`.
- **`qemu-web-hotswap.ps1 -Status`**: hotswap/tailwind diagnostics; sync preflight warns if hotswap off.
- Auto-remediate: golden-path lock TTL 120m.

### 2026-06-12 — QEMU webui hot-swap (fast dev loop)

- **`korus-web/docker-compose.hotswap-qemu.yml`**: bind-mount `modules/web-client/.../webui` on web guest.
- **`scripts/qemu-web-hotswap.ps1 -Enable`**, **`scripts/qemu-web-sync.ps1`**: `webui.tgz` sync (~10s) vs full `repo.tgz` redeploy.
- **`playwright-dev-loop.ps1 -SyncWebUi`**: sync UI before inner-tier Playwright.

### 2026-06-12 — Web client: Tailwind CSS v4 build pipeline

- **`modules/web-client/webui-build/`**: npm + Tailwind v4 CLI → `webui/tailwind.css` (prefixed utilities `tw:*`).
- Gradle task **`buildTailwindCss`** runs before `processResources`; CI installs Node deps.
- Auth screen (`renderAuth` in `app.js`) — first Tailwind utility usage.

### 2026-06-12 — Spec 004 engineering closure (deferred phase 2 post-backlog)

- **US2/US3 Hex write-path**: User, Organization, File write via application services and ports; saved-chat and public-link ports; legacy cleanup.
- **US1 Prod TLS scaffold**: `deploy/ansible/inventory/prod/`, vault wiring, CORS, WSS, `tls_smoke` tag; ops stage deploy pending real host.
- **US4 JFR profiling**: 8 worker `Dockerfile.*.profiling`, compose overlay, `scripts/profiling/profile-docker-jfr.ps1`.
- **US7 E2EE hybrid MLS**: server KMLS + browser client encrypt in `app.js`; NATS consumer; batch migration; Playwright `e2ee-capabilities.spec.ts`; prod `MLS_STATUS=active` gated on security sign-off.
- **US9 Fast acceptance**: `playwright-dev-loop.ps1` tier manifest, inner/outer gate, failure analysis; Playwright **26/26** on QEMU (2026-06-12).
- **US5/US6/US8**: Playwright parity gates, governance docs, QEMU DX hardening.
- **Docs**: `acceptance-report.md`, `analyze-report.md` (US9), `ops-signoff-log.md`; plans `05`/`06`/`08` synced.

### 2026-06-09 — Deferred backlog (post-backlog plan)

- **Hotplug governance**: ADR accepted, constitution v1.1 Bounded Deployment Split Exception.
- **Worker i18n logs**: all 9 workers — operational logs via `WorkerMessageSources` + bundle keys (ru/en).
- **JFR Docker**: profiling Dockerfiles (`*.profiling`), `docker-compose.profiling.yml`, **`scripts/profiling/profile-docker-jfr.ps1`**.
- **Vault/TLS Phase B**: Ansible role **`tls`**, stage inventory, **`scripts/smoke-tls-redirect.ps1`**, vault secrets wiring.
- **Hexagonal 2c–2e**: User, File metadata, Organization application services + JDBC adapters.
- **Playwright parity**: 7 new specs + `fixtures/ui.ts` (`data-testid` selectors); README matrix updated.
- **E2EE RFC 9420 phase 1**: KMLS wire codec, NATS `mls.*`, `MlsMigrationService`, `e2ee_scheme=mls`, capabilities `mls_status=active`; ADR **`docs/adr/ADR-e2ee-mls-library.md`** (BC wire phase 1, OpenMLS deferred).

### 2026-06-09 — Backlog closure: WIP conferences/i18n, parity sweep, roadmap, hexagonal 2b

- **Conference API**: `POST /v1/conferences`, `GET /v1/conferences/by-room/{roomSlug}`; localized `conference.default_title`; tests **`ConferenceServiceTest`**, **`ConferenceResourceTest`**.
- **Web-client i18n**: `ui-i18n.js`, `locales/en.js`, `locales/ru.js`; parity REST wiring (read receipts, file metadata/auth-link, export attachments, in-chat conference).
- **QEMU debug**: `Write-KorusDebugLog.ps1` gated by **`KORUS_DEBUG_LOG=1`**.
- **Retention/export**: purge gate requires **`export_v1`** only; **`ExportJobRepository.findLatestCompletedExport`**, **`isExportSufficientForPurge`**.
- **Indexer**: Prometheus **`indexer_solr_*_total`** metrics.
- **Security**: **`TimingNormalization`**, **`scripts/audit-timing.ps1`**, **`docs/SECURITY_AUDIT.md`** (generated on run).
- **Hexagonal Phase 2b**: **`MessageApplicationService`**, **`JdbcMessageRepositoryAdapter`**; **`MessageResource.get`** delegation.
- **Smokes**: **`scripts/smoke-export-replay-before-purge.ps1`**, **`scripts/smoke-retention-solr-clear.ps1`**.

### 2026-05-27 — **QEMU**: bootstrap через Ansible (spec 003)

- **`deploy/qemu/vm-bootstrap/run-ansible-local.sh`**: pip install Ansible в гостях, playbooks `qemu-server-local` / `qemu-web-local`.
- Cloud-init: **`python3-pip`**, redeploy **`scripts/qemu-redeploy.ps1`** через Ansible.
- Web `.env`: API/ws к **192.168.76.10**, browser WS через **`ws://<host-lan>:19088/ws`**.

### 2026-05-27 — **spec 003**: Docker + Ansible deploy и autotest suite

- **`deploy/ansible/`**: роли `common`, `korus_server`, `korus_web`, `korus_smoke`; playbooks `ci-local.yml`, `site.yml` (two-host + smoke tag).
- Smokes: **`scripts/smoke-messaging-e2e.sh`** (DM, group 3 users, WS/REST deliver, read receipts), **`scripts/smoke-deploy-acceptance.sh`**, bash-канон **`smoke-ready.sh`**, **`smoke-auth.sh`**, **`smoke-web-parity-api.sh`**; lib **`scripts/lib/SmokeMessaging.sh`**, **`scripts/keycloak-ensure-smoke-users.sh`**.
- CI: **`.github/workflows/deploy-messaging-smoke.yml`** (nightly Ansible + acceptance + Playwright job).
- Playwright: **`tests/e2e-web/`** (`messaging-critical`, `messaging-group`).
- Phase B scaffold: role **`observability`**, UFW в **`common`**, **`group_vars/vault.example.yml`**, **`scripts/lib/SmokeMessaging.ps1`**.
- Spec-kit: **`specs/003-docker-ansible-autotest/`**; docs **`deploy/two-host/README.md`**, **`scripts/SMOKE_INDEX.md`**, **`docs/CI_AND_REPO_HYGIENE.md`**.

### 2026-05-24 — **MLS stub**: deterministic session key derivation

- **`MlsService.deriveSessionKey`**: убран случайный salt на каждый вызов — encrypt/decrypt roundtrip стабилен; тесты **`MlsGroupManagerTest`**, **`MlsServiceDecryptContentTest`**.

### 2026-05-24 — **retention phase C**: file cleanup, purge admin, legal hold API

- **`FileRetentionJanitor`**: orphaned **`file_metadata`** + MinIO delete; env **`RETENTION_FILE_METADATA_CLEANUP_ENABLED`**, metrics **`retention_worker_file_metadata_deleted_total`**, **`retention_worker_minio_objects_deleted_total`**.
- Admin: **`GET /v1/admin/purge/status`**, **`GET/PATCH /v1/admin/legal-hold/*`**; **`LegalHoldRepository`** (V025 flags).
- Smokes: **`scripts/smoke-retention-purge.ps1`**, **`scripts/smoke-retention-file-cleanup.ps1`**.
- Docs: **`docs/ROADMAP_EPICS.md`**, **`docs/db/FLYWAY_AND_SCHEMA.md`** (V024–V028).

### 2026-05-24 — **epics 06–08**: MLS scaffold, read receipts, hexagonal Phase 2a

- MLS: **`MlsGroupManager`**, **`GET /admin/e2ee/status`**, **`MlsBenchmarkTest`**.
- Hexagonal: **`ChatApplicationService`**, **`CoreModule`**, **`CoreApiBenchmarkTest`**; Gradle **`:modules:core-api:benchmark`**.
- Read receipts plan synced; **`buildIntegrity`** green.

### 2026-05-23 — **web-client parity docs**: Spec 002 closure package and deferred runtime runbook

- Сформирован и синхронизирован self-contained пакет `specs/002-web-client-server-parity/*`: baseline (`parity-matrix.md`), closure report (`parity-report.md`), package `README.md`, implementation audit-trail (`IMPLEMENTATION_LOG.md`), operator template (`runtime-gate-report.md`), handoff checklist (`HANDOFF.md`), актуализированные `spec.md`/`plan.md`/`tasks.md`/`quickstart.md`/`checklists/requirements.md`.
- В `spec.md` статус зафиксирован как `Completed (runtime smoke deferred)`; в `plan.md` добавлен `Closure Snapshot` и полный инвентарь артефактов.
- Runtime-gates `T010`/`T016`/`T022` явно выделены как environment-dependent и перенесены в operator-run процесс без запуска localhost smoke в этом цикле.
- Навигация docs синхронизирована: `docs/plans/10-web-client-code-health-backlog.md` и `docs/plans/README.md` ссылаются на пакет `specs/002-web-client-server-parity/`.
- Вход в parity-пакет добавлен из верхнеуровневой и модульной документации: `README.md`, `modules/web-client/README.md`.
- Локальный quality gate подтвержден: `./gradlew.bat buildIntegrity` — green.

### 2026-05-23 — **hygiene**: docs/scripts canonicalization, retention/export status sync

- Введен канон для cleanup-работ: `docs/plans/README.md` (freeze-правила `canonical`/`deprecated`) и новый индекс smoke-сценариев `scripts/SMOKE_INDEX.md`.
- Синхронизированы статусы и фактическое состояние эпиков: `docs/plans/01-retention-phase-b.md` и `docs/plans/03-export-compliance.md` переведены в актуальный `in_progress` контекст.
- Обновлены roadmap/retention-описания под текущую dual-TTL и chunked deep-archive реализацию: `docs/ROADMAP_EPICS.md`, `docs/RETENTION_AND_DEEP_ARCHIVE.md`.
- Актуализирована трассировка задач по feature 001: `specs/001-system-review-refactoring/tasks.md` (отмечены завершенные setup/foundation пункты).
- Документация проекта ссылается на новые источники правды: `README.md` и `docs/CI_AND_REPO_HYGIENE.md` дополнены ссылками на `docs/plans/README.md` и `scripts/SMOKE_INDEX.md`.

### 2026-05-16 — **export**: compliance prep API, smokes, admin UI

- **`POST /api/v1/admin/export-compliance-prep`** (`EXPORT_ADMIN_SUGGEST_ENABLED`): group + retention policy + N text messages; **`include_file`** — upload и сообщение `type=file`; ответ `file_id`, `file_message_id`.
- Админ UI: **seed+prepare**, **seed+file**, **compliance flow** (prep → suggest → export → poll → download/inspect), **poll**.
- Smokes: `smoke-export-compliance-flow` (`-IncludeFile`), `smoke-export-compliance-pack`, `smoke-admin-export-inspect`, `smoke-openapi-export-compliance` (`/api/openapi.json`), CI workflow **Export compliance smoke** (`poll_seconds` input).
- OpenAPI: `@ExampleObject` на **export-compliance-prep**; unit **`AdminExportComplianceOpenApiTest`**; smoke **`smoke-openapi-export-compliance`**.
- Admin UI: кнопка **flow+file**; исправлен **`smoke-export-compliance-flow.sh --include-file`** (prep с файлом).
- Тест **`AdminExportComplianceSeedH2Test`** (H2 + in-memory file proxy).

### 2026-05-16 — **export**: E2EE stats, avatar source, admin audit presets

- **`exportCompleteness`**: `e2eeMessageCount`, `nonE2eeMessageCount`, GDPR `e2ee_file_refs`; `referencedFiles[].exportFileSource=chat_avatar`.
- Админ-консоль: пресеты фильтра аудита для export.* событий.

### 2026-05-16 — **export**: аудит скачивания + smoke ZIP/parts

- **audit_events** `export.downloaded` (part, output_format, output_storage) на **GET …/download**.
- **`scripts/smoke-export-chat.ps1`**: `output_format`, скачивание `bundle` / `?part=json` / `?part=manifest`; `-SkipDownload`.

### 2026-05-16 — **export**: manifest в ZIP + частичное скачивание

- В ZIP: **`attachments/manifest.json`** (fileId, zipPath, size, sha256); **`output_format`** в **GET** status (`json` | `zip`).
- **GET download** query **`part`**: `bundle` (default), `json`, `manifest`.

### 2026-05-16 — **export-replay**: ZIP с бинарными вложениями

- **`EXPORT_REPLAY_INCLUDE_FILE_BODIES`**: `export.json` + `attachments/{fileId}/{filename}` в **`.export.zip`**; MinIO key `exports/{jobId}.export.zip`.
- Лимиты **`EXPORT_REPLAY_MAX_FILE_BODIES`**, **`EXPORT_REPLAY_MAX_FILE_BODY_BYTES`**; блок **`fileBodies`** и обновление GDPR **`file_binary`** в JSON.
- **GET download** отдаёт zip (`application/zip`) при bundle.

### 2026-05-16 — **export**: auto-queue по `msg.export.suggested`

- **`ExportJobEnqueuer`** — общая постановка export (REST + auto).
- **`EXPORT_AUTO_QUEUE_ON_SUGGESTED`** (default false): **`ExportAutoQueueOnSuggested`** → **`msg.export.replay`**, аудит **`export.auto_queued`** / **`export.auto_queue_skipped`**.
- Актор: **`EXPORT_AUTO_QUEUE_ACTOR_USER_ID`** или **`chats.owner_id`**; cooldown **`EXPORT_AUTO_QUEUE_COOLDOWN_MINUTES`** (default 1440).

### 2026-05-16 — **export**: GDPR disclosures + retention export hint (NATS)

- В **`exportCompleteness.gdprDisclosures`** — структурированный чеклист (hot DB, E2EE, файлы, deep-archive, retention, Solr, TTL).
- Subject **`msg.export.suggested`** (`ExportSuggestedEvent`); retention публикует при **`RETENTION_PUBLISH_EXPORT_SUGGESTED=true`** перед hot-body pass.
- **core-api** `ExportSuggestedSubscriber` → аудит **`export.suggested`**; env **`EXPORT_SUGGESTED_SUBSCRIBER_ENABLED`** (default true).

### 2026-05-16 — **export-replay**: дамп Solr-индекса по чату

- **`solrIndex`** в JSON (`chat_id_s` query); env **`EXPORT_REPLAY_INCLUDE_SOLR_INDEX`**, **`EXPORT_REPLAY_MAX_SOLR_DOCS`**, **`EXPORT_REPLAY_SOLR_INCLUDE_CONTENT`**.
- Скрипт **`scripts/pre-retention-export.ps1`** — export перед агрессивной ретенцией.

### 2026-05-16 — **export-replay**: снимки retention hot-body из MinIO

- **`retentionSnapshots`**: ключи из **`retention_hot_body_applied`**, объекты в бакете retention; env **`EXPORT_REPLAY_INCLUDE_RETENTION_SNAPSHOTS`**, **`EXPORT_REPLAY_MAX_RETENTION_SNAPSHOTS`**, **`EXPORT_REPLAY_RETENTION_MINIO_BUCKET`**, **`EXPORT_REPLAY_RETENTION_OBJECT_PREFIX`**.
- Общий **`ExportMinioJsonFetcher`** для deep-archive и retention.

### 2026-05-16 — **export-replay**: снимки deep-archive из MinIO

- Опционально **`deepArchiveSnapshots`** (`messages/{id}.json` из бакета deep-archive); env **`EXPORT_REPLAY_INCLUDE_DEEP_ARCHIVE`**, **`EXPORT_REPLAY_MAX_DEEP_ARCHIVE_SNAPSHOTS`**, **`EXPORT_REPLAY_DEEP_ARCHIVE_BUCKET`**.
- **`exportCompleteness`** отражает число найденных снимков.

### 2026-05-16 — **export-replay**: retention + completeness в JSON

- **`retentionPolicy`** (эффективная политика чата) и **`exportCompleteness`** (что в пакете / что нет); флаг **`exportRecommendedBeforeHotBodyPurge`**.
- Env **`EXPORT_REPLAY_INCLUDE_RETENTION_POLICY`**, **`EXPORT_REPLAY_INCLUDE_EXPORT_COMPLETENESS`** (default **true**).

### 2026-05-16 — **export**: `message_ttl_filter_applied` в **export_jobs** и **GET** status

- **`V017__export_jobs_ttl_filter`**, поле в **`ExportReplayCompleteEvent`**, воркер и NATS-подписчик сохраняют флаг TTL.

### 2026-05-16 — **export**: MinIO для JSON экспорта

- **`ExportOutputRef`** (`minio:exports/{jobId}.export.json`); воркер **`EXPORT_REPLAY_MINIO_UPLOAD`** → upload в MinIO; **GET download** читает из MinIO или **EXPORT_DIR**.
- **`output_storage`** в **GET** status; **full-server** compose включает upload.

### 2026-05-16 — **export**: подписчик **msg.export.replay.complete** в core-api

- **`ExportReplayCompleteSubscriber`**: queue **`core-api-export-complete`**, **`ExportJobRepository.applyCompleteIfPending`** (идемпотентно для **queued** / **processing**).
- Env **EXPORT_COMPLETE_SUBSCRIBER_ENABLED** (по умолчанию **true**); воркеру нужен **EXPORT_PUBLISH_COMPLETE=true**.

### 2026-05-16 — **export**: скачивание JSON через API

- **GET /v1/chats/{chatId}/export/{jobId}/download** — файл из **EXPORT_DIR** (общий том с **export-replay-worker** в **full-server** compose).
- **`ExportFileAccess`**, env **EXPORT_DIR** на **core-api**; тесты **`ExportFileAccessTest`**, **`ExportResourceTest`**.

### 2026-05-16 — **export-replay**: TTL сообщений как в API

- **`ExportReplayWorker`**: **`EXPORT_REPLAY_APPLY_MESSAGE_TTL_FILTER`** (по умолчанию **true**) — тот же предикат, что **`MessageRepository.SQL_MSG_TTL_VISIBLE`**; в JSON **`messageTtlFilterApplied`**.
- Тест **`buildMessagesSql_appliesTtlWhenEnabled`**; smoke **`scripts/smoke-export-chat.ps1`**.

### 2026-05-13 19:45 UTC — **export**: статус задачи и аудит

- **`V016__export_jobs`**, **`ExportJobRepository`**: **POST** создаёт **queued**; **export-replay** → **processing** / терминальный статус + **`output_path`**.
- **`GET /v1/chats/{chatId}/export/{jobId}`**, **`ExportJobStatusResponse`**; **audit_events**: **export.requested**, **export.completed**.
- **`ExportJobStore`**, **`ExportAuditWriter`** в **export-replay**; тесты **`ExportResourceTest`**, **`ExportJobRepositoryH2Test`**.

### 2026-05-13 19:40 UTC — **web-client**: загрузка файлов в чат

- **`app.js`**: **`apiFetch`**, **`uploadChatFile`**, **`sendFileMessage`**; отображение **image** / **file** / **video** по **file_id** (превью и скачивание с Bearer); лимит из **`GET /media/capabilities`**.
- **`styles.css`**: **`.msg-attachment-image`**, **`.msg-attachment-dl`**.

### 2026-05-13 19:30 UTC — **web-client**: refresh JWT и переподключение WebSocket

- **`app.js`**: при **401** — **`POST /auth/refresh`**, повтор запроса, иначе выход из сессии; после refresh — переподключение WS с новым access token.
- Автопереподключение **WebSocket** (backoff до 30 с), статус **WS переподкл.**; **`closeWs`** / выход — без реконнекта.

### 2026-05-13 19:20 UTC — WebRTC mesh: демонстрация экрана удалённым участникам

- **`modules/web-client/app.js`**: **`getDisplayMedia`** — трек экрана в каждый **`RTCPeerConnection`** (через **`clone()`** при наличии), **`rtcRenegotiateMesh`**, **`stopScreenShareInternal`** / **`removeScreenTracksFromMesh`** по **`displaySurface`** / метке трека; при создании **`pc`** учёт уже включённого экрана; второй **`<video>`** на участника для экрана; **`ontrack`** разделяет камеру и дисплей.
- **`modules/web-client/styles.css`**: **`.rtc-remote-screen`**.

### 2026-05-13 19:15 UTC — **export-replay**: **referencedUsers** включает загрузчиков **file_metadata**

- **`ExportReplayWorker`**: CTE **`fup`** в **`SQL_REFERENCED_USERS`** — **`uploaded_by`** для UUID из того же набора, что **`referencedFiles`** (пустой **`ANY`** — безопасно).
- **`ExportResource`** (OpenAPI).

### 2026-05-13 19:10 UTC — **export-replay**: **referencedFiles** (**file_metadata**)

- **`ExportReplayWorker`**: UUID из не‑**e2ee-*** текста **messages** / **message_versions** (regex) и из **chats.avatar_file_id**; **`EXPORT_REPLAY_INCLUDE_REFERENCED_FILES`**, **`EXPORT_REPLAY_MAX_FILE_IDS_FROM_CONTENT`**, **`EXPORT_REPLAY_MAX_REFERENCED_FILES`**; **`referencedFileIdsTruncated`**, **`referencedFilesTruncated`**.
- **`ExportResource`** (OpenAPI), тесты **`collectFileIdsFromText`** / **`tryAddUuidString`** в **`ExportReplayWorkerTest`**.

### 2026-05-13 19:05 UTC — WebRTC mesh: очередь ICE до **`remoteDescription`**

- **`modules/web-client/app.js`**: **`rtcPendingCandidates`**, **`addRemoteIceCandidate`** / **`flushPendingIceCandidates`** — trickle **candidate** до прихода **offer**/**answer** не теряется; сброс при **`teardownPeer`** / **`rtcHangupAll`**; **`flushPendingIceCandidates`** повторяется, если за время **`addIceCandidate`** в очередь снова что-то попало; **`addIceCandidate(null)`** (конец trickle), если уже есть **`remoteDescription`**.

### 2026-05-13 19:00 UTC — WebRTC: обрыв **`connectionState`**, STUN в **`docker-compose.turn`**

- **`modules/web-client`**: **`app.js`** — при **`RTCPeerConnection.connectionState === "failed"`** отправка **`hangup`**, **`teardownPeer`**, сообщение пользователю.
- **`korus-web/docker-compose.turn.yml`**: в **`WEB_CLIENT_RTC_ICE_SERVERS`** добавлен публичный **STUN** вместе с **TURN**.
- **`korus-web/README.md`**: уточнение про состав ICE в **`turn`**-оверлее.

### 2026-05-13 18:55 UTC — Документация **korus-web** / **`-Help`** у **`korus-web-down`** и **`dev-web-stack-down`**

- **`korus-web/README.md`**: шаг **3** — остановка профиля **`web`** через **`dev-web-stack-down`**.
- **`scripts/korus-web-down.ps1`**, **`scripts/korus-web-down.sh`**: в справке — когда использовать **`dev-web-stack-down`**.
- **`scripts/dev-web-stack-down.ps1`**, **`scripts/dev-web-stack-down.sh`**: в справке — остановка **korus-web** отдельно (**`korus-web-down`**).

### 2026-05-13 18:50 UTC — **export-replay**: **chats** + **chat_members**

- **`ExportReplayWorker`**: объект **`chat`** (или **`chatMissing`**), массив **`chatMembers`**; **`EXPORT_REPLAY_INCLUDE_CHAT`**, **`EXPORT_REPLAY_INCLUDE_CHAT_MEMBERS`**, **`EXPORT_REPLAY_MAX_CHAT_MEMBERS`**, **`chatMembersTruncated`**.
- **`ExportResource`** (OpenAPI): флаги выключения чата / участников.

### 2026-05-13 18:45 UTC — **`full-stack-up`**: подсказки TURN, smoke, порядок остановки

- **`scripts/full-stack-up.ps1`**, **`scripts/full-stack-up.sh`**: опциональный **`korus-web-up`** с **`-Turn`**, **`smoke-korus-web`**, явный текст про **`full-stack-down`** и **`korus-web-down`**; **`-Help`** — кратко про подсказки после успеха.
- **`korus-web/README.md`**, **`scripts/TEST_SERVER_READY.md`**: остановка **korus-web** перед **`full-stack-down`** при общей сети с **full-server**.

### 2026-05-13 18:40 UTC — **`dev-web-stack-down`**, подсказки в **`full-stack-down`** / **`dev-web-stack-up`**

- **`scripts/dev-web-stack-down.ps1`**, **`scripts/dev-web-stack-down.sh`**, **`scripts/dev-web-stack-down.cmd`**: **`docker compose -f docker-compose.dev-min.yml --profile web down`** (опционально **`-Volumes`**).
- **`scripts/dev-web-stack-up.ps1`**, **`scripts/dev-web-stack-up.sh`**: строка «Stop profile web».
- **`scripts/full-stack-down.ps1`**, **`scripts/full-stack-down.sh`**: напоминание о **`korus-web-down`** и **`dev-web-stack-down`**; расширен **`-Help`**.
- **`README.md`** (таблица + **`dev-web-stack-down.cmd`**), **`scripts/TEST_SERVER_READY.md`**, **`docs/PARALLEL_DEVELOPMENT.md`**, **`modules/web-client/README.md`**.

### 2026-05-13 18:35 UTC — **export-replay**: **reactions**, **pinned**

- **`ExportReplayWorker`**: массивы **`reactions`**, **`pinnedMessages`** (тот же поднабор сообщений); **`EXPORT_REPLAY_INCLUDE_REACTIONS`**, **`EXPORT_REPLAY_MAX_REACTION_ROWS`**, **`EXPORT_REPLAY_INCLUDE_PINS`**, **`EXPORT_REPLAY_MAX_PINNED_ROWS`**; флаги **`reactionsTruncated`**, **`pinnedTruncated`**.
- **`ExportResource`** (OpenAPI): перечислены новые переменные.

### 2026-05-13 18:25 UTC — **export-replay**: **message_versions**, Docker в **full-server**

- **`ExportReplayWorker`**: массив **`messageVersions`** (подзапрос по тем же сообщениям, что и экспорт; **`contentOmitted`** для правок сообщений с типом **`e2ee-*`**); **`EXPORT_REPLAY_INCLUDE_VERSIONS`**, **`EXPORT_REPLAY_MAX_MESSAGE_VERSIONS`**; флаг **`versionsTruncated`**.
- **`docker/Dockerfile.export-replay-worker`**, сервис **`export-replay-worker`** в **`docker/docker-compose.full-server.yml`** (том **`export-replay-data`**, **`EXPORT_DIR=/export`**).
- **`ExportResource`** (OpenAPI): переменные для версий.

### 2026-05-13 18:15 UTC — **`korus-web-down`**: симметричная остановка со **`Attach` / `Turn`**

- **`scripts/korus-web-down.ps1`**, **`scripts/korus-web-down.sh`**, **`scripts/korus-web-down.cmd`**: **`docker compose down`** из **`korus-web/`** с теми же **`-f`**, что **`korus-web-up`** (**`-Attach`**, **`-Turn`**); **`-Volumes`** — **`down -v`**.
- **`scripts/korus-web-up.ps1`**: строка «Stop: … **korus-web-down**» с актуальными флагами.
- **`korus-web/README.md`**, **`scripts/TEST_SERVER_READY.md`**, **`docs/PARALLEL_DEVELOPMENT.md`**, **`modules/web-client/README.md`**, **`korus-web/docker-compose.turn.yml`**, корневой **`README.md`**.

### 2026-05-13 18:10 UTC — **export-replay**: выгрузка **messages** из hot-БД (JSON v1)

- **`modules/workers/export-replay`**: при **`DB_JDBC_URL`** — SELECT по **`chat_id`**, типы **`e2ee-*`** без поля **`content`** (**`contentOmitted`**); **`EXPORT_REPLAY_MAX_MESSAGES`**, **`EXPORT_REPLAY_QUERY_TIMEOUT_SECONDS`**; статусы **`msg.export.replay.complete`**: **`export_v1`** / **`export_failed`**; без БД — прежний stub (**`stub_written`**).
- **`ExportResource`** (OpenAPI), **`ExportReplayCompleteEvent`**, **`docs/NATS_SUBJECTS_INTEROP.md`**, тест **`ExportReplayWorkerTest`**.

### 2026-05-13 17:50 UTC — CI GitHub: **`gradle.properties`** без **`org.gradle.java.home`**

- **`gradle.properties`**: удалён путь **`C:/Program Files/Java/...`** — на **ubuntu-latest** Gradle не находил JVM и падал на шаге **`buildIntegrity`**.
- **`docs/CI_AND_REPO_HYGIENE.md`**: где задавать JDK локально.

### 2026-05-13 17:30 UTC — WebRTC ICE: **`WEB_CLIENT_RTC_ICE_SERVERS`**, клиент и **korus-web**

- **`modules/web-client`**: **`getRtcIceServers()`** в **`app.js`** — чтение **`iceServersJson`** из **`/web-client-env.js`**, иначе публичный STUN; подсказка в панели видео.
- **`modules/web-client/README.md`**, **`korus-web/.env.example`**: описание переменной и пример JSON.
- **`korus-web/docker-compose.yml`**, **`docker-compose.attach.yml`**: проброс **`WEB_CLIENT_RTC_ICE_SERVERS`** в **web-a** / **web-b**.
- **`WebClientEnvServlet.buildEnvScriptBody`**: вынесена логика сборки скрипта для тестов без **`System.getenv`**; **`WebClientEnvServletTest`**.
- **`scripts/TEST_SERVER_READY.md`**: когда нужен **TURN** для mesh WebRTC и откуда берётся **`iceServersJson`**.

### 2026-05-13 17:00 UTC — WebRTC signaling: **`rtc.signal`**, mesh в **web-client**

- **`modules/common`**: **`RtcSignalEvent`**, **`NatsSubjects.RTC_SIGNAL`**.
- **`modules/ws-gateway`**: **`@OnMessage`** — клиент **`rtc_signal`** → NATS **`rtc.signal`** (с **`fromUserId`** из JWT).
- **`modules/workers/message-pipeline`**: подписка на **`rtc.signal`**, проверка **`PipelineFanoutLogic.isChatMember`**, fan-out в **`msg.deliver.{userId}`**.
- **`modules/web-client`**: mesh **RTCPeerConnection** (STUN), **`GET /chats/.../members`**, обработка **`rtc_signal`** в WS.
- **`docs/NATS_SUBJECTS_INTEROP.md`**, **`modules/web-client/README.md`**.

### 2026-05-13 16:30 UTC — **web-client**: мессенджер-UI, Markdown, демо-видео

- **`modules/web-client/src/main/resources/webui/app.js`**, **`styles.css`**: экран авторизации (двухколоночный на широких экранах), список чатов с аватаром и поиском, Markdown в сообщениях, панель видео/конференции (локальная камера, миниатюры, экран, заглушки участников).
- **`modules/web-client/README.md`**: раздел про возможности UI.

### 2026-05-13 16:00 UTC — SolrJ **10**: API клиента и запросов

- **`modules/core-api`**: **`SolrQuery`** → **`org.apache.solr.client.solrj.request`**, **`HttpJdkSolrClient`** вместо **`Http2SolrClient`**, **`CloudSolrClient.Builder.withDefaultCollection`** вместо **`setDefaultCollection`**.
- **`modules/workers/indexer`**: то же для **`main`**; URL к коллекции через **`/solr/`** как в **`SolrClientFactory`**.

### 2026-05-13 15:20 UTC — **`.cmd`** для smoke / hints; **`install-env-silent` —help**

- **`scripts/smoke-korus-web.cmd`**, **`scripts/dev-ui-hints.cmd`**: запуск одноимённых **`.ps1`** из **`cmd.exe`** (**`%*`**).
- **`scripts/install-env-silent.ps1`**, **`scripts/install-env-silent.sh`**: **`-Help`** / **`--help`** / **`-h`**.
- **`README.md`**: таблица и полный список **`.cmd`** в строке **clean/create-stand**.
- **`scripts/TEST_SERVER_READY.md`**: **`smoke-korus-web.cmd`**, **`-Help`**.

### 2026-05-13 15:10 UTC — **`-Help` / `--help`**: `install-environment`, `smoke-korus-web`, `dev-ui-hints`

- **`scripts/install-environment.ps1`**, **`scripts/install-environment.sh`**: справка **`-Help`** / **`--help`** / **`-h`** (в начале).
- **`scripts/smoke-korus-web.ps1`**: **`-Help`**; комментарий в шапке на **ASCII**.
- **`scripts/dev-ui-hints.ps1`**, **`scripts/dev-ui-hints.sh`**: **`-Help`** / **`--help`**; в **`.sh`** лишние аргументы — ошибка.
- **`README.md`**: таблица документации.

### 2026-05-13 15:00 UTC — Jackson в **`modules:common`**: BOM **2.21.3**

- **`modules/common/build.gradle.kts`**: **`com.fasterxml.jackson:jackson-bom:2.21.3`** (**`api(platform(...))`**) и артефакты **без жёсткой версии** — согласованный набор (**`databind`** = **2.21.x** с тремя сегментами, **`annotations`** = **2.21** по BOM). Ранее **`2.21`** для **`databind`** и **`2.21.2`** для **`annotations`** не совпадали с Central → **Could not resolve**.

### 2026-05-13 14:30 UTC — **`.cmd`** для full-stack / web; **`-Help`** в подъёмных **`.ps1`**

- **`scripts/full-stack-up.cmd`**, **`scripts/full-stack-down.cmd`**, **`scripts/dev-web-stack-up.cmd`**, **`scripts/korus-web-up.cmd`**: запуск одноимённых **`.ps1`** из **`cmd.exe`** (**`%*`**).
- **`scripts/start.ps1`**, **`scripts/create-stand.ps1`**, **`scripts/full-stack-up.ps1`**, **`scripts/dev-web-stack-up.ps1`**, **`scripts/korus-web-up.ps1`**: переключатель **`-Help`** (краткий usage и **exit 0** до загрузки **`korus-env`**).
- **`scripts/full-stack-down.ps1`**: текст **`-Help`** — также **`scripts\full-stack-down.cmd`**.
- **`README.md`**: таблица (**`start.cmd`**, **`full-stack-*.cmd`**, **`-Help`**).

### 2026-05-13 14:00 UTC — Справка: `clean`, `full-stack-down`

- **`scripts/clean.sh`**, **`scripts/full-stack-down.sh`**: **`--help`** / **`-h`**; у **`full-stack-down.sh`** лишние аргументы — ошибка.
- **`scripts/clean.ps1`**: **`-Help`**.
- **`scripts/full-stack-down.ps1`**: **`-Help`**.
- **`README.md`**: уточнения в таблице.

### 2026-05-13 13:00 UTC — Документация: Eclipse и classpath Gradle

- **`docs/CI_AND_REPO_HYGIENE.md`**: раздел **Eclipse (Buildship)** — импорт корня монорепо, **JDK 25**, **Refresh Gradle Project** при ошибках **`com.fasterxml`** / **`java.nio cannot be resolved`**.
- **`README.md`**: ссылка в таблице документации на раздел про Eclipse.
- Удалены файлы **`.vscode/`** из репозитория; **`.gitignore`** снова целиком игнорирует **`.vscode/`**.

### 2026-05-13 12:45 UTC — `SKIP_KORUS_ENSURE` в подъёмных **`.ps1`**, `--skip-ensure` в **`.sh`**

- **`scripts/start.ps1`**, **`scripts/full-stack-up.ps1`**, **`scripts/dev-web-stack-up.ps1`**, **`scripts/korus-web-up.ps1`**: **`$env:SKIP_KORUS_ENSURE -eq '1'`** эквивалентно **`-SkipEnsure`** (как в **`create-stand.ps1`**).
- **`scripts/start.sh`**: разбор **`--skip-ensure`** / **`-S`**, **`--help`**, **`min`/`full`**; исправлена строка **Keycloak** в подсказках.
- **`scripts/full-stack-up.sh`**, **`scripts/dev-web-stack-up.sh`**, **`scripts/korus-web-up.sh`**: флаг **`--skip-ensure`** / **`-S`** и обновлённый **`--help`**.
- **`README.md`**: таблица документации.

### 2026-05-12 23:30 UTC — `create-stand.sh`: `--skip-ensure` / `-S`, подсказки в `install-environment`

- **`scripts/create-stand.sh`**: разбор аргументов **`--skip-ensure`** / **`-S`**, **`--help`**, позиция **`min`/`full`** в любом порядке с флагами; **`SKIP_KORUS_ENSURE=1`** по-прежнему действует.
- **`scripts/install-environment.ps1`**, **`scripts/install-environment.sh`**: после успешной проверки (не в **`-Quiet`**) — одна строка «дальше: **create-stand** + **start**» со ссылкой на **README**.
- **`README.md`**: флаги **`create-stand.sh`**.

### 2026-05-12 23:15 UTC — GitHub Actions: Node 24 (checkout, setup-java, wrapper-validation)

- **`.github/workflows/ci.yml`**: **`actions/checkout@v6`**, **`actions/setup-java@v5`**, **`gradle/actions/wrapper-validation@v5`** — рантайм экшенов на Node.js **24**, без предупреждений о deprecated Node.js **20**.
- **`docs/CI_AND_REPO_HYGIENE.md`**: таблица под актуальные версии.

### 2026-05-12 23:00 UTC — Retry 10s в `korus_compose_file_retry`, `clean.sh all`, `SKIP_KORUS_ENSURE` в `create-stand.ps1`

- **`scripts/lib/korus-env.sh`**: между попытками **`korus_compose_file_retry`** — **10** с (как в PowerShell **`Invoke-KorusDockerComposeInvoke`** / **`up`**).
- **`scripts/clean.sh`**: ветка **`all`** — **`docker system prune -f || true`**.
- **`scripts/create-stand.ps1`**: **`SKIP_KORUS_ENSURE=1`** в сессии — то же, что **`-SkipEnsure`** (паритет с **`create-stand.sh`**).
- **`README.md`**: уточнения в строке таблицы.

### 2026-05-12 22:30 UTC — Bash: `full-stack-down` / `clean` / `create-stand` и `korus_compose_file_retry`

- **`scripts/lib/korus-env.sh`**: универсальная **`korus_compose_file_retry`** (**`docker compose -f …`** с **2** попытками) вместо узкого имени **`korus_compose_down_retry`**.
- **`scripts/full-stack-down.sh`**, **`scripts/clean.sh`**, **`scripts/create-stand.sh`**: **`ROOT`** из **`BASH_SOURCE`**, **`source lib/korus-env.sh`**, **`korus_set_path_env`**, **`korus_compose_file_retry`**; в **`create-stand.sh`** — **`korus_ensure_env`** (если не **`SKIP_KORUS_ENSURE=1`**).
- **`README.md`**: строка таблицы про **`clean`/`create-stand`** (**.ps1** и **.sh**).

### 2026-05-12 22:00 UTC — CI workflow: YAML на строке с `name`

- **`.github/workflows/ci.yml`**: значение **`name`** шага в **кавычках** — двоеточие в тексте **`(all modules: …)`** без кавычек ломало разбор YAML на GitHub (**Invalid workflow file**).

### 2026-05-12 21:30 UTC — CI GitHub: закоммичен `gradle-wrapper.jar`

- **`.gitignore`**: исключение **`!gradle/wrapper/gradle-wrapper.jar`** — ранее правило **`*.jar`** не пускало wrapper в репозиторий, на **`ubuntu-latest`** падали **`wrapper-validation`** и **`./gradlew`**.
- **`gradle/wrapper/gradle-wrapper.jar`**: добавлен в git.

### 2026-05-12 20:00 UTC — Удалён каталог `docs/miro/`

- Удалены вспомогательные CSV/TSV и README из **`docs/miro/`**; из корневого **`README.md`** убрана связанная строка таблицы документации.
- Удалены прежние записи **`[Unreleased]`**, которые описывали только появление и правки содержимого этого каталога.

### 2026-05-12 17:45 UTC — `dev-web-stack-up` / `korus-web-up` и `KORUS_KORUS_WEB_*`

- **`scripts/lib/korus-env.ps1`**: **`Invoke-KorusDockerComposeInvoke`** (произвольные аргументы **`docker`**, рабочий каталог, **2** попытки); **`KORUS_KORUS_WEB_COMPOSE`**, **`KORUS_KORUS_WEB_COMPOSE_ATTACH`**.
- **`scripts/lib/korus-env.sh`**: **`korus_compose_in_dir_retry`**.
- **`scripts/dev-web-stack-up.ps1`**, **`scripts/korus-web-up.ps1`**, **`scripts/dev-web-stack-up.sh`**, **`scripts/korus-web-up.sh`**: **`KORUS_*`**, проверка/тихая установка, повтор **`docker compose`**; **`-SkipEnsure`** / **`SKIP_KORUS_ENSURE=1`**.
- **`README.md`**: строки таблицы про **`dev-web-stack-up`** / **`korus-web-up`**.

### 2026-05-12 17:30 UTC — `KORUS_*` env, установка в `start` / `full-stack-up`

- **`scripts/lib/korus-env.ps1`**, **`scripts/lib/korus-env.sh`**: **`Set-KorusPathEnvironment`** / **`korus_set_path_env`** (**`KORUS_REPO_ROOT`**, **`KORUS_DOCKER_DIR`**, **`KORUS_COMPOSE_DEV_MIN`**, **`KORUS_COMPOSE_FULL_SERVER`**, **`KORUS_SCRIPTS_DIR`**, **`KORUS_KORUS_WEB_DIR`**); **`Invoke-KorusEnsureDevTooling`** / **`korus_ensure_env`**; **`docker compose up`** с **2** попытками.
- **`scripts/start.ps1`**, **`scripts/start.sh`**, **`scripts/full-stack-up.ps1`**, **`scripts/full-stack-up.sh`**: перед стендом — проверка окружения и при необходимости тихая установка; **`-SkipEnsure`** / **`SKIP_KORUS_ENSURE=1`** — пропуск.
- **`scripts/install-environment.ps1`**, **`scripts/install-environment.sh`**: при наличии **`lib/korus-env.*`** — выставление **`KORUS_*`**.
- **`README.md`**: таблица документации.

### 2026-05-12 17:00 UTC — Тихая установка окружения (`install-env-silent`)

- **`scripts/install-env-silent.ps1`**: **winget** — JDK (**Temurin 25** / **21** / **OpenJDK 21**), **Git**, **Docker Desktop**; флаги **`-Quiet`** (минимум вывода, **`winget`** в **`Out-Null`**), **`-WhatIf`**, **`-SkipDocker`**; **`--disable-interactivity`**; строки на **латинице** (**PS 5.1**).
- **`scripts/install-env-silent.cmd`**: запуск **`.ps1`** из **`cmd.exe`**.
- **`scripts/install-environment.ps1`**: **`-SilentInstall`** передаёт **`-Quiet`** в silent-скрипт при **`-Quiet`**; краткий итог проверки при **`-Quiet`**.
- **`scripts/install-env-silent.sh`**: **`--quiet`** / **`QUIET=1`**, функция **`apt_update`** при тихом режиме; **`scripts/install-environment.sh`**: **`--quiet`** / **`-q`** вместе с **`--silent-install`**.
- **`README.md`**: обновлена строка таблицы.

### 2026-05-12 16:30 UTC — PowerShell 5.1: разбор скриптов без UTF-8 BOM

- **`scripts/dev-ui-hints.ps1`**, **`scripts/dev-infra-up.ps1`**, **`scripts/dev-web-stack-up.ps1`**, **`scripts/start.ps1`**, **`scripts/install-environment.ps1`**: сообщения на **ASCII** / латиница, чтобы **Windows PowerShell 5.1** не ломал строки при UTF-8 без BOM; проверка **`[Parser]::ParseFile`** для всех **`scripts/**/*.ps1`**.

### 2026-05-12 16:00 UTC — Скрипты стенда под Windows (аналоги `.sh`) + правка `.sh` под `full-server`

- **`scripts/start.ps1`**, **`scripts/clean.ps1`**, **`scripts/create-stand.ps1`**, **`scripts/install-environment.ps1`**: из корня репозитория; аргумент **`full`** — **`docker/docker-compose.full-server.yml`** (вместо отсутствующего **`dev-full`**).
- **`scripts/start.cmd`**, **`clean.cmd`**, **`create-stand.cmd`**, **`install-environment.cmd`**: вызов **`powershell.exe -File`** для **`cmd.exe`**.
- **`scripts/start.sh`**, **`clean.sh`**, **`create-stand.sh`**: для **`full`** и **`clean all`** — **`docker-compose.full-server.yml`**; комментарии с отсылкой на **`.ps1` / `.cmd`**.
- **`README.md`**: строка в таблице документации.

### 2026-05-12 15:30 UTC — Скрипты подсказок UI и логинов (`dev-ui-hints`)

- **`scripts/dev-ui-hints.ps1`**, **`scripts/dev-ui-hints.sh`**: URL веб-клиента (порт **`KORUS_WEB_LB_PORT`** из **`korus-web/.env`**, иначе **9088**), **`/admin/`**, health, Keycloak, **ws://localhost:8082/ws**; логины realm **`admin`/`admin`**, **`csadmin`/`csadmin`** (см. **`keycloak/avandocmsg-realm.json`**).
- **`scripts/korus-web-up.ps1`**, **`scripts/korus-web-up.sh`**, **`scripts/dev-web-stack-up.ps1`**, **`scripts/dev-web-stack-up.sh`**: в конце вызываются подсказки; после **dev-web-stack-up** — напоминание поднять **korus-web**.
- **`README.md`**: строка в таблице документации.

### 2026-05-12 15:00 UTC — Docker: полный стек `docker-compose.full-server.yml`

- **`docker/docker-compose.full-server.yml`**: один файл — инфраструктура (**postgres-hot/archive**, **redis**, **nats**, **minio**, **zookeeper**, **solr**, **keycloak**) + **core-api** + **ws-gateway** + **message-pipeline** + **retention-worker** (без **`--profile`**); healthcheck **retention-worker** на порт **9191** внутри контейнера.
- **`README.md`**: строка в таблице документации со ссылкой на файл и команду запуска.
- **`scripts/full-stack-up.ps1`**, **`scripts/full-stack-down.ps1`**, **`scripts/full-stack-up.sh`**, **`scripts/full-stack-down.sh`**: обёртки над **`docker compose`** для этого файла.
- **`scripts/TEST_SERVER_READY.md`**: подраздел про полный стек.

### 2026-05-12 14:40 UTC — Bash `dev-web-stack-up.sh` и `smoke-korus-web.sh`

- **`scripts/dev-web-stack-up.sh`**: **`docker/docker-compose.dev-min.yml`** с профилем **`web`** (**`--build`**); подсказки по **`korus-web-up`** / смоку.
- **`scripts/smoke-korus-web.sh`**: **`curl`** — **`/health`**, корень UI, **`/web-client-env.js`**, опционально **`/api/v1/health`** (**`--check-api`**); **`--url`** / **`WEB_BASE_URL`**.
- **`README.md`**, **`korus-web/README.md`**, **`scripts/TEST_SERVER_READY.md`**, **`modules/web-client/README.md`**, **`docs/PARALLEL_DEVELOPMENT.md`**, **`scripts/dev-web-stack-up.ps1`**, **`scripts/korus-web-up.sh`**: ссылки на новые скрипты.

### 2026-05-12 14:30 UTC — Админ-консоль: раздел «Манифест консоли»

- **`CoreAdminUiContributor`**: **`core-admin-manifest`** — **`GET /admin/ui/manifest`** в боковом меню (**`sort_order` 35**, между аудитом и ретенцией).
- **`AdminUiManifestLoadTest`**, **`scripts/lib/SmokeAdminUi.ps1`**: ожидание не менее **7** разделов и наличие **`core-admin-manifest`**.

### 2026-05-12 14:05 UTC — Bash `korus-web-up.sh`, предупреждение о сети в `korus-web-up.ps1`

- **`scripts/korus-web-up.sh`**: подъём **`korus-web/`**, **`--attach` / `-a`**, **`--build` / `-b`**; предупреждение при отсутствии сети **`korus_messenger_dev_min`**.
- **`scripts/korus-web-up.ps1`**: при **`-Attach`** — **`docker network inspect`** и **`Write-Warning`**, если сеть не найдена.
- **`README.md`**, **`korus-web/README.md`**, **`scripts/TEST_SERVER_READY.md`**, **`modules/web-client/README.md`**: ссылки на **`.sh`**.

### 2026-05-12 14:00 UTC — Админ-консоль: кнопка «Обновить»

- **`admin-ui/app.js`**: **«Обновить»** для **статистики**, **аудита** (текущие фильтры), **организаций**, **ретенции** (повторный **GET** последней загруженной **org**/**chat**) и простых **`json_panel`** (**GET** по **`data_path`**, в т. ч. **сессия admin**).

### 2026-05-12 13:45 UTC — Стабильное имя Docker-сети dev-min для attach

- **`docker/docker-compose.dev-min.yml`**: у default-сети задано имя **`korus_messenger_dev_min`** (удобно для **`korus-web/docker-compose.attach.yml`** без **`docker network ls`**).
- **`korus-web/docker-compose.attach.yml`**, **`korus-web/.env.example`**, **`korus-web/README.md`**: дефолт **`KORUS_DEV_MIN_NETWORK`** → **`korus_messenger_dev_min`**; примечание о пересоздании стенда после смены имени сети.

### 2026-05-12 13:15 UTC — Скрипт `korus-web-up.ps1` и документация attach

- **`scripts/korus-web-up.ps1`**: подъём **`korus-web/`** из корня; **`-Attach`** — второй compose-файл **`docker-compose.attach.yml`**; **`-Build`**; **`--env-file .env`**, если файл есть.
- **`scripts/dev-web-stack-up.ps1`**, **`scripts/TEST_SERVER_READY.md`**, **`korus-web/README.md`**, **`README.md`**: ссылки на скрипт и режим attach.

### 2026-05-12 13:00 UTC — `korus-web`: опциональная сеть с dev-min (`docker-compose.attach.yml`)

- **`korus-web/docker-compose.attach.yml`**: внешняя сеть **`${KORUS_DEV_MIN_NETWORK:-korus_messenger_dev_min}`** (после фикса имени в dev-min; иначе задать вручную); **web-a** / **web-b** / **lb** подключаются к ней и по умолчанию используют **`http://core-api:8080`**, **`ws-gateway:8081`** (внутренние имена/порты).
- **`korus-web/.env.example`**, **`korus-web/README.md`**: запуск **`docker compose -f docker-compose.yml -f docker-compose.attach.yml`** (имя внешней сети см. **`docker-compose.dev-min.yml`** / **`KORUS_DEV_MIN_NETWORK`**).

### 2026-05-12 12:30 UTC — Админ-консоль: пользователь → организация

- **`CoreAdminUiContributor`**: раздел **«Пользователь → организация»** (`core-user-organization`, **`json_panel`**, без **`data_path`**).
- **`admin-ui/app.js`**: **`PATCH /admin/users/{user_id}/organization`** с **`{"org_id":"…"}`** (подсказка **204**).
- **`AdminUiManifestLoadTest`**, **`scripts/lib/SmokeAdminUi.ps1`**: манифест — не менее **5** разделов, наличие **`core-user-organization`**.

### 2026-05-12 12:00 UTC — Админ-консоль: PATCH ретенции org/chat

- **`admin-ui/app.js`**: в разделе **«Ретенция»** после GET — форма и **`PATCH .../organizations/{id}/retention`** / **`PATCH .../chats/{id}/retention`** с телом **`UpdateRetentionPolicyRequest`** (дни опционально как **null**, три флага — чекбоксы).

### 2026-05-12 00:50 UTC — Скрипты стенда UI (dev-web-stack-up) и смок API через korus-web

- **`scripts/dev-web-stack-up.ps1`**: подъём **`docker/docker-compose.dev-min.yml`** с профилем **`web`**; ключ **`-Build`**.
- **`scripts/smoke-korus-web.ps1`**: ключ **`-CheckApi`** — **`GET /api/v1/health`** через прокси web-client на lb.
- **`modules/web-client/README.md`**: кратко про запуск, Docker и **`korus-web/`**.
- **`README.md`**, **`korus-web/README.md`**, **`scripts/TEST_SERVER_READY.md`**, **`docs/PARALLEL_DEVELOPMENT.md`**: ссылки на **`dev-web-stack-up.ps1`** и **`-CheckApi`**.

### 2026-05-12 00:10 UTC — Смоук манифеста админки

- **`scripts/lib/SmokeAdminUi.ps1`**: после **`GET .../admin/ui/manifest`** — не менее **4** разделов и наличие **`core-retention`** (согласовано с **`AdminUiManifestLoadTest`**).
- **`admin-ui/app.js`**: если в DOM остался старый тулбар аудита без **`#auditLimit`**, он удаляется и строится заново (без полной перезагрузки страницы).

### 2026-05-11 23:55 UTC — Админ-консоль: ретенция, лимит аудита, удаление организации

- **`admin-ui/app.js`**: раздел **«Ретенция (org / chat)»** (GET org/chat retention), для **«Аудит»** — поле **limit** (1–500), для **«Организации»** — удаление по UUID (**DELETE** + подтверждение).

### 2026-05-11 22:45 UTC — Профиль `web`: воркер message-pipeline в Docker

- **`docker/Dockerfile.message-pipeline`**: образ JRE + **`installDist`** воркера **`modules/workers/message-pipeline`**.
- **`docker/docker-compose.dev-min.yml`**: сервис **`message-pipeline`** (профиль **`web`**), **`NATS_URL`**, **`DB_*`**, **`NATS_JETSTREAM=false`** (совместимо с **core-api** без JetStream).
- **`korus-web/README.md`**, **`scripts/TEST_SERVER_READY.md`**, **`README.md`**: уточнено, что профиль **`web`** поднимает и **ws-gateway**, и **message-pipeline**.

### 2026-05-11 21:30 UTC — Интеграция стенда: ws-gateway в dev-min (профиль `web`)

- **`docker/docker-compose.dev-min.yml`**: опциональный сервис **`ws-gateway`** (профиль **`web`**), публикация **`8082:8081`**, **`KEYCLOAK_*`** на сервис **`keycloak:8080`** внутри сети compose, **`NATS_URL`** — **`nats://nats:4222`**.
- **`korus-web/README.md`**, **`korus-web/.env.example`**: как поднять стенд вместе с **dev-min** и выставить **`KORUS_WS_GATEWAY_PORT=8082`**.
- **`scripts/TEST_SERVER_READY.md`**: раздел про **`korus-web/`**, профиль **`web`** и воркер **message-pipeline**.
- **`scripts/smoke-korus-web.ps1`**: смок **`/health`**, корня UI и **`/web-client-env.js`** на URL стека **korus-web**.
- **`README.md`**: строка в таблице документации про **`docker-compose.dev-min.yml`** и профиль **`web`**.

### 2026-05-11 20:15 UTC — Веб-клиент: вынесен Docker-стек `korus-web/`

- Каталог **`korus-web/`**: **`docker-compose.yml`** — две реплики образа из **`docker/Dockerfile.web-client`** (**`modules/web-client`**, Java + Tomcat), сервис **`lb`** (nginx) с балансировкой **`least_conn`** между **web-a** и **web-b**; **`location /ws`** на ws-gateway (**`KORUS_WS_GATEWAY_HOST`** / **`KORUS_WS_GATEWAY_PORT`**, по умолчанию **host.docker.internal:8081**).
- **`korus-web/nginx-lb/`**: Dockerfile образа балансировщика, **`nginx.conf.template`** + **`docker-entrypoint-lb.sh`** (**`envsubst`** для хоста/порта ws-gateway).
- **`korus-web/README.md`**, **`korus-web/.env.example`**; корневой **`README.md`** — раздел о **ведении `CHANGELOG.md`** (UTC + перечень работ) и **поддержке описания проекта**; **`.gitignore`** — **`korus-web/.env`**.

### Локализация API (сообщения для клиента)

- Юнит-тесты **`CompositeMessageSourceTest`** (**`modules/common`**): **`ru`**/**`en`**, UTF-8, цепочка bundle (**`messages_chain_a`** → **`messages_chain_b`** в test resources). Паритет ключей **`messages_common_*`** — **`MessagesCommonBundleParityTest`**; **`messages_core_api_*`** — **`MessagesCoreApiBundleParityTest`** (**`modules/core-api`**). Разбор **`app.locale`**: **`AppConfig.localeFromProperty`** — **`AppConfigLocaleFromPropertyTest`**.
- Тексты **`ApiError.message`** и подписи параметров UUID (**`InvalidUuidParameterException`**) вынесены в **`ResourceBundle`**: **`messages_core_api_{ru,en}.properties`**, общие — **`messages_common_{ru,en}.properties`** (модуль **`modules/common`**). UTF-8 через **`Utf8Control`**.
- Язык при старте **`core-api`**: **`app.locale`** (по умолчанию **`ru`**), переменная окружения **`APP_LOCALE`** (**`en`**, **`en-US`** и т.д.). Лог: **`API locale`** в **`MessengerApplication`**. HK2: **`UserMessageSource`** (**`CompositeMessageSource`**).
- **`InvalidUuidParameterException`**: второй аргумент **`UuidParams.required`** — стабильный ключ параметра (**`chat_id`**, **`user_id`**, …), метки — ключи **`param.*`** в bundle. Дополнительно: оставшиеся ответы **`body required`** в **`AdminResource`** переведены на **`messages.get("error.admin.body_required")`**.
- Заготовки bundle по модулям: **`messages_worker_*`** (каждый воркер — свой файл **`_ru`/`_en`**). **`ws-gateway`:** **`messages_ws_gateway_{ru,en}.properties`** — тексты причин закрытия WebSocket при ошибках токена (**`APP_LOCALE`**, по умолчанию **`ru`**); паритет ключей — **`MessagesWsGatewayBundleParityTest`**. Указание **`APP_LOCALE`**: **`README.md`** (таблица), **`scripts/TEST_SERVER_READY.md`**.

### Встроенная админ-консоль

- Статика **`/admin/`** (classpath **`admin-ui/`**): вход через **`POST /api/v1/auth/login`**, затем **`GET /api/v1/admin/ui/manifest`** и данные панелей (эндпоинты из manifest). Раздел «Статистика сервера»: **`GET /api/v1/admin/ui/stats`** (JVM, PostgreSQL/Redis/NATS, счётчики **`users`/`chats`/`messages`**).
- Эпик админки: **`AdminUiSectionKind.JSON_PANEL`** — панель с GET и выводом JSON; в **`CoreAdminUiContributor`** добавлены разделы **«Организации»** (`**/admin/organizations**`) и **«Аудит»** (`**/admin/audit-events**`). Исправлено сохранение **`refresh_token`** в SPA после логина (выход через Keycloak).
- Админка SPA: для **`json_panel`** при массиве объектов — **таблица** (до 100 строк, до 14 колонок); **«Организации»** — **`POST /admin/organizations`** (имя + «Создать»); **«Аудит»** — фильтры **action** / **resource_type** / **resource_id** + «Применить», параметр **`limit`** (1–500, по умолчанию 50); путь в manifest для аудита — **`/admin/audit-events`** (параметры собирает клиент).
- **`scripts/smoke-ready.ps1`**: проверки **`GET /admin/`**, **`GET /api/v1/admin/ui/manifest`**, **`GET /api/v1/admin/ui/stats`** после login; **`scripts/TEST_SERVER_READY.md`** — описание этих шагов. SPA: после входа вызов **`/admin/session`**, сброс токена при **401** и при ошибке загрузки manifest.
- **`scripts/smoke-auth.ps1`**: до **`POST .../auth/logout`** — проверка **`/admin/ui/manifest`** и **`/admin/ui/stats`**. В SPA для «Статистика сервера» — таблица-сводка (uptime, heap, зависимости, счётчики) и полный JSON ниже.
- **`scripts/lib/SmokeAdminUi.ps1`**: общие функции **`Test-SmokeAdminStaticPage`** / **`Test-SmokeAdminUiApi`** для **`smoke-ready.ps1`** и **`smoke-auth.ps1`**. Футер **`/admin/`** — ссылки на **health**, **ready**, **capabilities**, **Prometheus**, **OpenAPI**.
- **`GET /api/v1/admin/ui/manifest`**: поле **`api_version`** (как у **`health`**). Статика админки: заголовок **`X-Content-Type-Options: nosniff`**. SPA: метка версии в шапке, кнопка **«Выйти»** (**`POST /auth/logout`** + сброс **sessionStorage**).
- **`GET /api/v1/admin/console`** (и путь **`v1/admin/console`** в фильтре): **303** на **`/admin/`**, без JWT (**`JwtAuthFilter`**). Статика: **`Cache-Control`** (**`no-store`** для **`.html`**, **`max-age=3600`** для **`.js`/`.css`**).
- **`scripts/lib/SmokeAdminUi.ps1`**: **`Test-SmokeAdminConsoleRedirect`** (**`HttpWebRequest`**, без авто-редиректа) — проверка **303** и **`Location`**; вызывается из **`smoke-ready.ps1`**. Футер **`/admin/`**: ссылка «Вход через API (редирект)».
- Расширение разделов: интерфейс **`com.avandocmsg.messenger.common.admin.ui.AdminUiContributor`** + файл **`META-INF/services/com.avandocmsg.messenger.common.admin.ui.AdminUiContributor`**; ядро — **`CoreAdminUiContributor`**. Отключённый модуль (нет JAR на classpath) не попадает в SPI — разделы исчезают после перезапуска.

### Документация процесса

- **`docs/PARALLEL_DEVELOPMENT.md`**: параллельная разработка (`core-api` / воркеры / `common` / миграции / i18n), ссылка из **`README.md`**.
- Удалён закрытый трекер **`docs/TZ_SERVER_100.md`** (100 пунктов); добавлен **`docs/ROADMAP_EPICS.md`** — эпики доработок после базовой реализации. Обновлены **`README.md`**, **`docs/RETENTION_AND_DEEP_ARCHIVE.md`** (в т. ч. ссылки с мёртвого трекера на дорожную карту).

### CI и репозиторий

- **`.editorconfig`:** UTF-8, **`end_of_line = lf`** для текстовых файлов; переводы строк для **`gradlew`** / **`gradlew.bat`** по-прежнему задаёт **`.gitattributes`** (LF / CRLF). Отступы: **4** пробела для Java и **`.kts`**, **2** для YAML и XML в репозитории.
- **GitHub Actions:** **`.github/workflows/ci.yml`** — JDK **25**, **`./gradlew buildIntegrity`** (все subprojects: **`build`**), проверка Gradle Wrapper; job **`build`**; push в **`main`** / **`master`** / **`develop`**, PR, **`workflow_dispatch`**; **`permissions: contents: read`**, concurrency. Документация **`docs/CI_AND_REPO_HYGIENE.md`**, **`README.md`**, **`scripts/TEST_SERVER_READY.md`**.
- **Dependabot:** **`.github/dependabot.yml`** — Gradle **weekly**, GitHub Actions **monthly**.
- **`.gitattributes`:** **`gradlew`** → LF, **`gradlew.bat`** → CRLF. См. **`docs/CI_AND_REPO_HYGIENE.md`**.

### Аудит (админка и файлы): безопасный JSON в `details_json`

- **`AdminResource`:** политики ретенции (**`PATCH`** org/chat) — **`ObjectMapper` / `ObjectNode`** (**`retentionPolicyPatchAuditDetails`**); **`user.organization.set`** — **`userOrganizationSetAuditDetails(UUID)`**; **`organization.create`** — **`organizationCreateAuditDetails(name)`**; **`organization.delete`** — **`organizationDeleteAuditDetails(name)`** (имя до удаления через **`OrganizationRepository.findById`**). Общий вывод: **`writeAdminAuditJson`**. Тесты **`AdminResourceTest`**.
- **`FileResource`:** публичные ссылки — **`publicLinkCreateAuditDetails`**, **`publicLinkRevokeAuditDetails`**. Тест **`FileResourceAuditDetailsTest`**.

### Репозитории: API и регрессии

- **`AdminResource`:** UUID в путях ретенции org/chat, **`DELETE .../organizations/{orgId}`** и тело **`PATCH .../users/{userId}/organization`** (`org_id`) — через **`UuidParams.required`** (единый **400** + **`ApiError`**, счётчик **`api_invalid_uuid_parameter_total`**, ТЗ п. 28); для **`PATCH .../organization`** при отсутствии тела — **400** **`body required`**. Тесты **`AdminResourceTest`**.
- **`ContactResource`** / **`ChatBanResource`:** невалидные UUID в path/body → **`UuidParams`** (**400** + **`ApiError`**); **`ContactService`** и **`ChatBanService.banUser`** работают с **`UUID`**. Тесты **`ContactResourceTest`**, **`ChatBanResourceTest`**.
- **`BlocksResource`:** невалидный UUID в **`DELETE .../blocks/{userId}`** или в **`user_id`** тела **`POST`** → **`UuidParams`**. Тест **`BlocksResourceTest`**.
- **`FileResource`**, **`ConferenceResource`**, **`CryptoResource`** (key packages), **`UserResource`** **`/{id}`:** ранний выход при невалидном UUID — **`FileResourceTest`**, **`ConferenceResourceTest`**, **`CryptoResourceTest`**, **`UserResourceTest`**.
- **`ChatResource`** (**`POST /v1/chats`**, тип **`group`**): каждый элемент **`member_ids`** — **`UuidParams.required`**; **`markRead`**: **`up_to_message_id`** (если задан). **`MessageResource.send`**: **`reply_to_msg_id`** (если задан) — **`UuidParams.required`**, разбор передаётся в **`MessageService`** как **`UUID`**; **`GET`** истории — query **`before`**; **`getById`** — **`msgId`**. Тесты **`ChatResourceTest`**, **`MessageResourceTest`**.
- **`OrganizationRepository`:** **`findById(UUID) → Optional<OrgRow>`**. Тест **`OrganizationRepositoryH2Test`**.
- **`GET .../audit-events`:** фильтр только по **`resource_id`** и нижняя граница **`limit`** (**`AuditRepository.listRecent`**) — тесты **`AuditRepositoryH2Test`** (**`listRecent_withResourceIdOnly_matchesWithoutActionOrType`**, **`listRecent_limitZero_clampedToOne`**).

### Юнит-тесты H2 (`modules/core-api`)

In-memory **H2** для изолированной проверки SQL без PostgreSQL (часть сценариев намеренно обходит методы с **`INSERT … ON CONFLICT`** / **`RETURNING`**, несовместимыми с H2 — см. javadoc в классах тестов):

| Класс теста | Что покрыто (кратко) |
|-------------|----------------------|
| **`FilePublicLinkRepositoryH2Test`** | insert/revoke/find по токену, kind C без пароля |
| **`ChatRepositoryRetentionOverlayH2Test`** | **`chatExists`**, **`findOrgIdForRetentionOverlay`** (владелец / роли участников) |
| **`UserRepositoryH2Test`** | **`create`**, **`findById`**, **`findByUsername`**, **`updateProfile`**, **`updatePresence`**, **`touchHeartbeat`** |
| **`BlockRepositoryH2Test`** | **`exists`**, **`listBlockedUsers`**, **`unblock`** (данные через JDBC — см. класс) |
| **`ContactRepositoryH2Test`** | **`list`**, **`remove`** (данные через JDBC) |
| **`ChatBanRepositoryH2Test`** | **`ban`**, **`findById`**, **`findByChatId`**, **`isBanned`**, **`unban`**; явные **`created_at`** для порядка списка |
| **`ConferenceRepositoryH2Test`** | **`newRoomSlug`**, **`findById`**, **`listForChat`**, **`endConference`**, **`findCreatorId`** (строки через JDBC — без **`INSERT … RETURNING`**) |
| **`FileRepositoryH2Test`** | **`insert`**, **`findById`**, **`delete`** |
| **`MessageRepositoryH2Test`** | **`insert`**, **`findById`**, **`findByChatId`**, **`findLatestMessageId`**; TTL скрывает сообщение; явные **`created_at`** для детерминизма сортировки |
| **`ChatReadRepositoryH2Test`** | **`countUnreadFromOthers`** в режиме **`MODE=PostgreSQL`**; состояние чтения через JDBC (**`upsertLastRead`** в H2 не используется) |

### Ретенция: `pass_id` в построчном `audit_events.details_json`

- Для **`message.retention.hot_body_cleared`** в **`details_json`** добавлено опциональное поле **`pass_id`** (UUID прохода — тот же, что в **`RetentionAppliedEvent`** и в **`resource_id`** сводки **`message.retention.bulk_cleared`**). Для **`message.retention.bulk_cleared`** в **`details_json`** добавлено поле **`pass_id`** (дубликат **`resource_id`** для единообразного парсинга). **`RetentionHotBodyJanitor`**. Документация **`docs/RETENTION_AND_DEEP_ARCHIVE.md`** §8.

### Docker

- **`docker/docker-compose.dev-min.yml`**, сервис **`retention-worker`**: **`healthcheck`** (**`curl`** → **`http://127.0.0.1:$RETENTION_METRICS_PORT/health`** внутри контейнера, порт как в **`environment`**, **`interval`** 30s, **`timeout`** 5s, **`retries`** 3, **`start_period`** 60s). В **`docker/Dockerfile.retention-worker`** добавлен пакет **`curl`** (лёгкий слой **`apt-get`**) для проверки; при **`RETENTION_METRICS_PORT=0`** HTTP выключен — **`healthy`** недостижим без включения метрик.

### База данных (Flyway)

- **`V015__messages_retention_hot_body_candidate_index.sql`:** частичный индекс **`idx_messages_retention_hot_body_candidates`** на **`messages (created_at ASC, chat_id)`** при **`deleted = false`**, **`content IS NOT NULL`**, **`trim(content) <> ''`** — под выборку кандидатов **`RetentionHotBodyJanitor.hotBodyCandidateSelectSql`**; **`docs/db/FLYWAY_AND_SCHEMA.md`**.

- **`V014__audit_events_list_indexes.sql`:** индексы **`idx_audit_events_action_occurred`** (**`action`**, **`occurred_at DESC`**) и **`idx_audit_events_resource_occurred`** (**`resource_id`**, **`occurred_at DESC`**) для **`GET /api/v1/admin/audit-events`** с фильтрами и **`ORDER BY occurred_at DESC LIMIT`**; **`docs/db/FLYWAY_AND_SCHEMA.md`**.

### Документация и скрипты

- **`README.md`** (корень репозитория): краткая точка входа — **`./gradlew test`**, таблица ссылок на **`CHANGELOG.md`**, **`docs/*`**, **`scripts/TEST_SERVER_READY.md`**, **`modules/workers/retention/README.md`**.
- **`docs/CI_AND_REPO_HYGIENE.md`** (новый): GitHub Actions, Dependabot, **`.gitattributes`**; перекрёстные ссылки на **`scripts/TEST_SERVER_READY.md`**, **`docs/TZ_SERVER_100.md`** (п. 95), **`docs/db/FLYWAY_AND_SCHEMA.md`**.
- **`docs/RETENTION_AND_DEEP_ARCHIVE.md`** §8: дополнение — формы **`details_json`** для **`organization.*`**, **`user.organization.set`**, **`file.public_link.*`** и политик ретенции (Jackson).
- **`docs/RETENTION_AND_DEEP_ARCHIVE.md`:** **§10** — явная отсылка: текущий фокус — **фаза A** (**§13**), граница «завершения A» vs **B/C**; **§13** — укрупнённые фазы **A / B / C** для планирования (маппинг на §10) и краткое правило автономных низкорисковых правок; **`modules/workers/retention/README.md`** — ссылка на §13 и абзац про **`scripts/smoke-retention-worker.ps1`** / **`retention_worker_build_info`**. **`scripts/TEST_SERVER_READY.md`** (Docker / **retention**) — ссылки на **§10/§13** и **`docs/db/FLYWAY_AND_SCHEMA.md`** (**V014**/**V015**); **`docs/db/FLYWAY_AND_SCHEMA.md`** — перекрёстные ссылки на RETENTION §8/§9/§13 для **V014**/**V015**.

- **`docs/RETENTION_AND_DEEP_ARCHIVE.md`:** §12 — черновик этапа 4 (purge строки Hot, `file_metadata`/MinIO, legal hold вне hot-body); в §10 для этапа 4 — отсылка «см. §12».

- Smoke-скрипт **`scripts/smoke-retention-worker.ps1`**: **`GET /health`**, **`GET /metrics`** (в т.ч. подстрока **`retention_worker_build_info`**), по умолчанию **`http://localhost:9192`**; абзац в **`scripts/TEST_SERVER_READY.md`** (блок Docker / профиль **`retention`**).

- **`AdminResource`**, **`GET .../audit-events`**: в **`@Operation`** — пояснение про **`pass_id`** в **`details_json`** и ссылка на **`docs/RETENTION_AND_DEEP_ARCHIVE.md`** §8.

- **`docs/NATS_SUBJECTS_INTEROP.md`:** абзац «Корреляция с админским аудитом» — **`pass_id`** в NATS и в **`audit_events`**, ссылка на §8 RETENTION.

- **`docs/RETENTION_AND_DEEP_ARCHIVE.md`** §8: обратная ссылка на **`docs/NATS_SUBJECTS_INTEROP.md`** для связки **`msg.event.retention`** ↔ аудит.

### Ретенция: Prometheus `retention_worker_build_info`

- Метрика **`retention_worker_build_info`** (**`io.prometheus.client.Info`**, default registry): метки **`version`** (из **`Package#getImplementationVersion`** для **`RetentionWorker`**, иначе **`unknown`**) и **`name`** = **`retention-worker`**. Регистрация/установка меток один раз при старте HTTP **`/metrics`** (**`RetentionMetricsHttpServer.start`** → **`RetentionMetrics.registerBuildInfoOnce`**). В **`modules/workers/retention/build.gradle.kts`** для задачи **`jar`** — manifest **`Implementation-Version`** из **`project.version`**. Тест **`RetentionMetricsHttpServerTest`**. Документация **`docs/RETENTION_AND_DEEP_ARCHIVE.md`** §9.1, **`modules/workers/retention/README.md`**.

### Ретенция: Prometheus gauge последнего hot-body прохода

- После успешного **`SELECT`** кандидатов в **`RetentionHotBodyJanitor.runOnce`** (в т.ч. **0** кандидатов и **dry-run**): **`retention_worker_last_hot_body_pass_epoch_seconds`** (Unix epoch), **`retention_worker_last_pass_cleared_count`** (**`0`** в dry-run, не «would_clear»). Не обновляются при пропуске **`RETENTION_REQUIRE_MINIO`**, при неудачном **`pg_try_advisory_lock`** (без **`SELECT`**), при исключении до завершения прохода. Тесты **`RetentionHotBodyPassGaugesTest`**. Документация **`docs/RETENTION_AND_DEEP_ARCHIVE.md`** §9.1, **`modules/workers/retention/README.md`**.

### Deep-archive MinIO: `snapshot_sha256` на `messages/{id}.json`

- **`DeepArchiverWorker`:** в корень JSON объекта **`messages/{messageId}.json`** добавлено **`snapshot_sha256`** — та же семантика, что у снимка ретенции в MinIO (SHA-256 hex по UTF-8 байтам корня **до** этого поля, затем поле в загружаемом документе). Общий хелпер **`ArchiveSnapshotEnvelopeDigest`** (**`modules/common`**); класс **`RetentionSnapshotEnvelopeDigest`** в модуле retention удалён в пользу него. Тест **`DeepArchiverWorkerMinioJsonTest`**. Документация **`docs/RETENTION_AND_DEEP_ARCHIVE.md`** §6.

### Админ: фильтр аудита по `resource_id`

- **`GET /api/v1/admin/audit-events`:** опциональный query **`resource_id`** (точное совпадение с колонкой **`audit_events.resource_id`**, до **128** символов); с **`action`** / **`resource_type`** объединяется через **AND**. Удобно для сводки ретенции по UUID прохода (**`message.retention.bulk_cleared`**, **`resource_type=retention_pass`**). **`AuditRepository.listRecent(limit, action, resourceType, resourceId)`**; тест **`AuditRepositoryH2Test`**.

### Hot-body снимок и NATS: `snapshot_sha256` (SHA-256 hex)

- В корень JSON снимка в MinIO (**`RetentionHotBodyJanitor`**) добавлено **`snapshot_sha256`** (**`ArchiveSnapshotFormat.JSON_SNAPSHOT_SHA256`**): **64** символа **a–f**, SHA-256 по UTF-8 байтам объекта **до** добавления этого поля (тот же **`ObjectMapper`**, что **`putObject`/`uploadObject`**). В **`RetentionAppliedEvent`** (**`msg.event.retention`**) — опциональное поле **`snapshot_sha256`** (**`@JsonInclude(NON_NULL)`**, в старых сообщениях отсутствие → **`null`**); при **`RETENTION_DRY_RUN=true`** событие не публикуется. В построчном **`audit_events`** (**`message.retention.hot_body_cleared`**) в **`details_json`** — опционально **`snapshot_sha256`**; в сводном **`message.retention.bulk_cleared`** — **без** агрегата **`snapshot_sha256`**. При пропуске **`putObject`** из‑за уже существующего объекта digest считается для того же конверта, что и при загрузке. Утилита **`Sha256Hex`** (**`modules/common`**), хелпер дайджеста — **`ArchiveSnapshotEnvelopeDigest`**. Тесты **`Sha256HexTest`**, **`RetentionMinioSnapshotPayloadTest`**, **`RetentionAppliedEventTest`**. Документация **`docs/NATS_SUBJECTS_INTEROP.md`**, **`docs/RETENTION_AND_DEEP_ARCHIVE.md`**, **`modules/workers/retention/README.md`**.

### MinIO снимок hot-body ретенции: `pass_id` в JSON

- В корне JSON объекта, загружаемого **`RetentionHotBodyJanitor`** в MinIO при очистке тела, опционально **`pass_id`** (тот же UUID строкой, что **`RetentionAppliedEvent.pass_id`** за проход **`runOnce`**); при **`null`** ключ не пишется. Тесты **`RetentionMinioSnapshotPayloadTest`**. Документация **`docs/RETENTION_AND_DEEP_ARCHIVE.md`** §6, **`modules/workers/retention/README.md`**.

### NATS `msg.event.retention`: `pass_id` в `RetentionAppliedEvent`

- Поле **`pass_id`** (UUID прохода **`RetentionHotBodyJanitor.runOnce`**, **`string`**) в JSON **`RetentionAppliedEvent`**; генерируется один раз на проход (в т.ч. dry-run — в **`INFO`**-логе; при сводном **`message.retention.bulk_cleared`** тот же UUID в **`resource_id`**). Десериализация: отсутствие поля → **`null`**. Тесты **`RetentionAppliedEventTest`**. Документация **`docs/NATS_SUBJECTS_INTEROP.md`**, **`docs/RETENTION_AND_DEEP_ARCHIVE.md`**.

### NATS `msg.event.retention`: `snapshot_version` в `RetentionAppliedEvent`

- Поле **`snapshot_version`** (**`int`**, **`ArchiveSnapshotFormat.SNAPSHOT_VERSION`**) в JSON **`RetentionAppliedEvent`**; воркер задаёт его при публикации (**`RetentionHotBodyJanitor`**). Десериализация: отсутствие поля в старых сообщениях → **`1`**. Тесты **`RetentionAppliedEventTest`**. Документация **`docs/NATS_SUBJECTS_INTEROP.md`**.

### MinIO: общий конверт снимков ретенции и deep-archive

- Поля **`snapshot_version`** (**`1`**) и **`producer`** (**`retention-worker`** / **`deep-archiver`**) в корне JSON, записываемого в MinIO: снимок тела **`RetentionHotBodyJanitor`** и объект **`DeepArchiverWorker`** по ключу **`messages/{id}.json`** (аддитивно к существующим полям). Константы — **`com.avandocmsg.messenger.common.retention.ArchiveSnapshotFormat`**. Тесты **`ArchiveSnapshotFormatTest`**, **`RetentionMinioSnapshotPayloadTest`**, **`DeepArchiverWorkerMinioJsonTest`**. Документация **`docs/RETENTION_AND_DEEP_ARCHIVE.md`** §6 / §10, **`docs/NATS_SUBJECTS_INTEROP.md`** (объект в MinIO vs payload NATS).

### Ретенция: опциональный PostgreSQL advisory lock на проход hot-body

- Env **`RETENTION_USE_ADVISORY_LOCK`** (по умолчанию **`false`**): при **`true`** и **`jdbc:postgresql:`** на **`DB_JDBC_URL`** один JDBC‑сеанс на проход держит session **`pg_try_advisory_lock`** с ключами **`RetentionAdvisoryLockIds`** до **`pg_advisory_unlock`** в **`finally`**; кандидаты **`SELECT`**, **`UPDATE`**, **`retention_hot_body_applied`**, сводный **`audit_events`** идут через то же соединение. Если lock не получен — **`INFO`**, без **`SELECT`**, счётчик **`retention_worker_pass_skipped_advisory_lock_total`**, **`0`** очищенных (в т.ч. чтобы два реплики не сканировали в **dry-run**). Тесты **`RetentionAdvisoryLockIdsTest`**, **`RetentionPlatformDefaultsTest`**. Документация **`docs/RETENTION_AND_DEEP_ARCHIVE.md`** §9, **`modules/workers/retention/README.md`**, **`application.properties`**.

### Ретенция: multipart для больших temp-file снимков в MinIO

- Env **`RETENTION_MINIO_MULTIPART_THRESHOLD_BYTES`**: при temp-file пути снимка и **`Files.size(temp) >=`** порога — **`MinioClient.uploadObject`** (MinIO Java SDK **8.5.10**, внутренний multipart при необходимости); иначе — прежний **`putObject`** со стримом; путь **`writeValueAsBytes`** без изменений. По умолчанию порог **`Long.MAX_VALUE`** (**`RetentionPlatformDefaults.MINIO_MULTIPART_THRESHOLD_BYTES_DEFAULT`**) — фактически только **`putObject`** для temp-file, пока env не задан (типичное значение для включения — **`33554432`**, 32 MiB). Счётчик **`retention_worker_minio_multipart_uploads_total`** после успешного **`uploadObject`**. Парсинг и тесты **`RetentionPlatformDefaultsTest`**, **`RetentionHotBodyJanitorDryRunTest`**. Документация **`docs/RETENTION_AND_DEEP_ARCHIVE.md`** §9–§10, **`modules/workers/retention/README.md`**, **`application.properties`**.

### Ретенция: graceful shutdown для `RetentionWorker`

- Один JVM shutdown-hook (**`hookStarted`** — идемпотентность): **`INFO`** в начале и в конце; **`shutdownRequested`** останавливает ожидание главного потока; однопоточный **`ScheduledExecutorService`** для проходов — **`shutdown`** + **`awaitTermination`** (до **15 с**), затем закрытие **метрики HTTP → NATS → Hikari** через **`RetentionShutdown.runCloseables`** (**`WARN`** на сбой каждого шага). Юнит-тест **`RetentionShutdownTest`** для **`runCloseables`**. Документация **`docs/RETENTION_AND_DEEP_ARCHIVE.md`** §9, **`modules/workers/retention/README.md`** (Operations).

### Ретенция: снимок MinIO через temp-file при большом `messages.content`

- Env **`RETENTION_SNAPSHOT_TEMPFILE_THRESHOLD_BYTES`** (по умолчанию **`0`** = выключено; прежний путь **`writeValueAsBytes`** без изменений): при **`> 0`** и UTF-8 длине **`messages.content`**, **строго большей** порога, JSON снимка пишется во временный файл (**`java.io.tmpdir`**, префикс **`retention-snapshot-`**), **`putObject`** с **`InputStream`** и известным размером, удаление файла в **`finally`**. Парсинг и потолок **1 GiB** — **`RetentionPlatformDefaults`**, выбор пути — **`RetentionSnapshotMaterialization`**, метрика **`retention_worker_minio_snapshot_tempfile_total`**. Тесты **`RetentionPlatformDefaultsTest`**, **`RetentionSnapshotMaterializationTest`**. Документация **`docs/RETENTION_AND_DEEP_ARCHIVE.md`** §9, **`modules/workers/retention/README.md`**, **`application.properties`**.

### Операторская документация: воркер ретенции

- **`modules/workers/retention/README.md`:** единая точка входа для операторов — назначение воркера, ссылка на **`docs/RETENTION_AND_DEEP_ARCHIVE.md`**, таблица **`RETENTION_*`** и связанных переменных (**`DB_JDBC_URL`**, **`DB_*`**, **`NATS_URL`**, **`MINIO_*`**), метрики **`/metrics`**, **`GET /health`**, локальный запуск **`gradlew :modules:workers:retention:run`**, профиль Compose **`retention`** (**`docker/docker-compose.dev-min.yml`**, сервис **`retention-worker`**), чеклист безопасности (в т.ч. **`RETENTION_DRY_RUN`**), кратко про загрузку в MinIO (**`putObject`**, версия SDK).

### Ретенция: пауза между сообщениями в проходе hot-body

- Env **`RETENTION_INTER_MESSAGE_DELAY_MS`** (по умолчанию **`0`**, макс. **`60000`**): опциональная пауза между кандидатами в одном проходе **`RetentionHotBodyJanitor`** после успешной или неуспешной обработки; после последнего в пачке — без паузы. При прерывании во время **`Thread.sleep`** — восстановление **`interrupt`** и досрочный выход из прохода с **`WARN`** в логе. Парсинг **`RetentionPlatformDefaults`**, хелпер **`RetentionInterMessageSleep.sleepQuiet`**, стартовый лог **`RetentionWorker`**. Тесты **`RetentionPlatformDefaultsTest`**, **`RetentionInterMessageSleepTest`**. Документация **`docs/RETENTION_AND_DEEP_ARCHIVE.md`** §9, **`application.properties`**, **`scripts/TEST_SERVER_READY.md`**.

### Ретенция: опциональный JDBC query timeout для hot-body

- Env **`RETENTION_JDBC_QUERY_TIMEOUT_SECONDS`** (по умолчанию **`0`** = без **`Statement.setQueryTimeout`**): при **`> 0`** — лимит в секундах на **`SELECT`** кандидатов и на **`UPDATE messages SET content = NULL`** в **`RetentionHotBodyJanitor`** (только воркер ретенции). Парсинг и дефолт **`0`** — **`RetentionPlatformDefaults.jdbcQueryTimeoutSecondsFromEnv`**, тесты **`RetentionPlatformDefaultsTest`**, **`RetentionHotBodyJanitorDryRunTest`**. Документация **`docs/RETENTION_AND_DEEP_ARCHIVE.md`** §9, подсказка в **`application.properties`**.

### Ретенция: readiness `GET /health` на порту метрик

- При **`RETENTION_METRICS_PORT` > 0** на том же HTTP‑сервере, что **`/metrics`**, доступен **`GET /health`**: **`200`** и **`ok`**, если воркер выключен или (включён и) Hot DB отвечает на **`SELECT 1`**, NATS **`CONNECTED`**, при **`RETENTION_REQUIRE_MINIO=true`** — MinIO настроен и бакет ретенции существует; иначе **`503`** и **`not ready`** (без утечки секретов). Классы **`RetentionMetricsHttpServer`**, **`RetentionHealthProbe`**; тесты **`RetentionMetricsHttpServerTest`**. Документация **`docs/RETENTION_AND_DEEP_ARCHIVE.md`** §9 / §9.1, комментарий в **`docker/docker-compose.dev-min.yml`**.

### Ретенция: dry-run для `RetentionWorker` / `RetentionHotBodyJanitor`

- Env **`RETENTION_DRY_RUN`** (по умолчанию **`false`**): проход hot-body только **`SELECT`** кандидатов + **`INFO`**-сводка и метрика **`retention_worker_dry_run_passes_total`**; без **`UPDATE`**, MinIO **`putObject`/`statObject`** на пути мутации, **`retention_hot_body_applied`**, **`audit_events`**, NATS **`msg.event.index`** / **`msg.event.retention`**. Стартовый **`WARN`** с **`RETENTION_DRY_RUN=true`**. Тесты **`RetentionPlatformDefaultsTest`**, **`RetentionHotBodyJanitorDryRunTest`**; подсказки в **`application.properties`**, **`docs/RETENTION_AND_DEEP_ARCHIVE.md`** §9, **`scripts/TEST_SERVER_READY.md`**.

### Ретенция: регрессионные тесты SQL кандидатов (legal hold / deep)

- Юнит-тесты **`RetentionHotBodyCandidateSqlTest`**: проверка, что текст SELECT hot-body включает **`pol.eff_legal = false`**, **`pol.eff_deep = true`** и вычисление **`eff_legal` / `eff_deep` / `eff_body_days`** (без PostgreSQL). В **`RetentionHotBodyJanitor`** вынесен **`hotBodyCandidateSelectSql(useAppliedLog)`** для единого источника строки запроса.

### Ретенция: регрессия SQL — исключение soft-delete из кандидатов hot-body

- **`RetentionHotBodyCandidateSqlTest`:** в SELECT кандидатов должно оставаться условие **`m.deleted = false`** (соответствует **`messages.deleted`** в **`V001`**), чтобы soft-deleted сообщения не попадали в hot-body ретенцию. Документировано только в журнале; логика SQL без изменений.

### Документация: ретенция (env и legal hold)

- Подсказки в **`application.properties`** (**`RETENTION_BULK_AUDIT_MIN_CLEARED`**, **`RETENTION_SKIP_SNAPSHOT_IF_DEEP_EXISTS`**, **`RETENTION_METRICS_PORT`** в списке env воркера), выравнивание **`scripts/TEST_SERVER_READY.md`** и **`docs/RETENTION_AND_DEEP_ARCHIVE.md`** §9 / §10 с фактическим SQL (**`legal_hold`** исключает кандидатов на очистку тела). Дополнительно: **`docs/TZ_SERVER_100.md`** п. **89** (воркер, env, аудит), **`docs/NATS_SUBJECTS_INTEROP.md`** (поля **`RetentionAppliedEvent`**), §10 таблица этапов — по текущему коду.

### Ретенция: пропуск дублирующего MinIO-снимка при уже существующем объекте

- Env **`RETENTION_SKIP_SNAPSHOT_IF_DEEP_EXISTS`** (по умолчанию **`false`**): перед **`putObject`** воркер выполняет **`statObject`** — если бакет записи ретенции совпадает с **`MINIO_BUCKET`**, сначала ключ **`messages/{messageId}.json`** (как у **`DeepArchiverWorker`**); иначе только ключ снимка ретенции в целевом бакете. При «объект уже есть» загрузка пропускается, **`UPDATE` / NATS / аудит** без изменений; в **`retention_hot_body_applied`** и событиях фиксируется фактический ключ (**`messages/…`** или префикс ретенции). Метрика Prometheus **`retention_worker_minio_snapshot_skipped_existing_total{reason=...}`**. Чистая логика и env в тестах **`RetentionSnapshotSkipResolverTest`**, **`RetentionPlatformDefaultsTest`**.

### Ретенция: сводный аудит массового прохода (`RetentionWorker`)

- Env **`RETENTION_BULK_AUDIT_MIN_CLEARED`** (по умолчанию **`0`** = выключено): после прохода hot-body, если число успешно очищенных тел **`≥`** порога, одна строка в **`audit_events`**: **`action`** = **`message.retention.bulk_cleared`**, **`resource_type`** = **`retention_pass`**, **`resource_id`** = UUID прохода, **`details_json`** с метриками прохода (в т.ч. **`sample_chat_ids`**). Построчный аудит **`message.retention.hot_body_cleared`** при **`RETENTION_AUDIT_ENABLED=true`** не отключается — сводка **дополнительная**. Тесты **`RetentionBulkAuditTest`**, **`RetentionPlatformDefaultsTest`**.

### Ретенция: Prometheus для `RetentionWorker`

- Env **`RETENTION_METRICS_PORT`**: при значении **`1…65535`** процесс поднимает **`/metrics`** (Prometheus text, JVM default exports + счётчики/гистограммы прохода hot-body, MinIO, ошибок, пинга БД). Зависимости **`io.prometheus:simpleclient*`** в **`modules/workers/retention`**.

### Админ: фильтр аудита по `action`

- **`GET /api/v1/admin/audit-events`:** опциональные query **`action`** и **`resource_type`** (точное совпадение, до 64 символов каждый; при двух — **AND**); **`AuditRepository.listRecent(limit, action, resourceType)`**; тесты **`AuditRepositoryH2Test`**. Удобно для событий ретенции **`message.retention.hot_body_cleared`** / **`resource_type=message`**.

### Ретенция: фаза 1 (схема + GET)

- **Миграция `V011__org_retention_policy.sql`:** таблица **`org_retention_policy`** (FK на **`organizations`**, каскад при удалении org).
- **`RetentionPolicyRepository`**, **`RetentionPolicyResponse.resolved`**, **`AppConfig`:** свойства **`retention.default.*`** и env **`RETENTION_DEFAULT_*`**.
- **`GET /api/v1/admin/organizations/{orgId}/retention`** в **`AdminResource`** (роль **admin**).
- Тесты: **`RetentionPolicyResponseTest`**, **`RetentionPolicyRepositoryH2Test`**.

### Сообщения: TTL видимости (`ttl_seconds`)

- **`SendMessageRequest`** / **`MessageResponse`:** поле **`ttl_seconds`** (JSON); валидация в **`MessageResource`** (**1 … `message.ttl.max.seconds`**, env **`MESSAGE_TTL_MAX_SECONDS`**).
- **`MessageRepository`:** предикат **`SQL_MSG_TTL_VISIBLE`** — скрытие истёкших в ленте, **`findById`**, поиске, доступе к файлу по ссылке из сообщения, **`findLatestMessageId`**; **`ChatReadRepository.countUnreadFromOthers`** не считает истёкшие.
- **`MessageResource`:** зависимость **`AppConfig`**.

### Ретенция: PATCH политики организации

- **`PATCH /api/v1/admin/organizations/{orgId}/retention`** + **`UpdateRetentionPolicyRequest`**; **`RetentionPolicyRepository.upsert`**; аудит **`organization.retention.set`**; тест **`upsert`** в **`RetentionPolicyRepositoryH2Test`**.

### Ретенция: воркер (hot-body, этап 3)

- **`AppConfig`:** **`retention.worker.enabled`** / **`retention.scan.interval.seconds`** (env **`RETENTION_WORKER_ENABLED`**, **`RETENTION_SCAN_INTERVAL_SECONDS`**; по умолчанию выключено, интервал 3600 с).
- Модуль **`modules/workers/retention`**: при **`RETENTION_WORKER_ENABLED=true`** — Hot PostgreSQL (**`DB_JDBC_URL`**), NATS (**`NATS_URL`**), MinIO (**`MINIO_*`**, по умолчанию обязательно **`RETENTION_REQUIRE_MINIO=true`**); env **`RETENTION_INITIAL_DELAY_SECONDS`** — пауза перед первым сканом (в dev-compose **`retention-worker`** — **30** с); опционально отдельный бакет снимков **`RETENTION_MINIO_BUCKET`** и префикс ключей **`RETENTION_MINIO_OBJECT_PREFIX`** (по умолчанию **`retention/body/`**); при **`RETENTION_ENSURE_MINIO_BUCKET=true`** — попытка создать бакет при старте (**`RetentionMinioBootstrap`**). Пакетный проход: эффективная **`hot_message_body_max_age_days`** (как в админском GET чата), без **legal hold**, при **`deep_archive_enabled`** → снимок **`content`** в MinIO, **`UPDATE messages SET content = NULL`**, публикация **`msg.event.index`** с **`index_op=update`**, публикация **`msg.event.retention`** (**`RetentionAppliedEvent`**), запись в **`retention_hot_body_applied`** (миграция **`V013`**, env **`RETENTION_USE_APPLIED_LOG`** по умолчанию **`true`**), при **`RETENTION_AUDIT_ENABLED=true`** — строка в **`audit_events`** (**`message.retention.hot_body_cleared`**, **`actor_user_id`** = null). Env: **`RETENTION_BATCH_LIMIT`**, **`RETENTION_DEFAULT_*`** (как в core-api). Тесты **`RetentionPlatformDefaultsTest`**, **`RetentionAppliedEventTest`**.
- **`docker/Dockerfile.retention-worker`**, сервис **`retention-worker`** в **`docker/docker-compose.dev-min.yml`** (профиль Compose **`retention`**); корневой **`.dockerignore`** для контекста сборки образов.
- **`application.properties`**: подсказка по env **`RetentionWorker`** (чтение только из окружения).
- **`docs/RETENTION_AND_DEEP_ARCHIVE.md`**, **`docs/NATS_SUBJECTS_INTEROP.md`**, **`docs/db/FLYWAY_AND_SCHEMA.md`**, **`scripts/TEST_SERVER_READY.md`**: описание поведения и рисков.

### Ретенция: политика чата (V012 + admin)

- **`V012__chat_retention_policy`**, **`ChatRetentionPolicyRepository`**, **`ChatRetentionPolicyResponse.resolved`** (без org — **`RetentionPolicyResponse.platformDefaults`**, не подставлять UUID чата в org-слой).
- **`ChatRepository.chatExists`**, **`findOrgIdForRetentionOverlay`** (владелец → участники по ролям).
- **`GET`/`PATCH /api/v1/admin/chats/{chatId}/retention`**; аудит **`chat.retention.set`**; тесты **`ChatRetentionPolicyRepositoryH2Test`**, **`ChatRetentionPolicyResponseTest`**, доп. кейс в **`RetentionPolicyResponseTest`**.

### ТЗ п. 89: проект ретенции и deep-archive

- **`docs/RETENTION_AND_DEEP_ARCHIVE.md`:** целевая модель сроков хранения (Hot / Archive / Deep), черновик схем **`retention_policy`** / override на чат, использование **`messages.ttl_seconds`**, воркер очистки, Solr, админский API и поэтапное внедрение.
- **`docs/TZ_SERVER_100.md`:** пункт **89** закрыт со ссылкой на документ и на существующие воркеры.

### ТЗ п. 63: граница объёма MLS (RFC 9420)

- **`docs/TZ_SERVER_100.md`:** пункт **63** закрыт как **осознанный объём**: полный MLS по **RFC 9420** не реализован; зафиксированы **`MlsService`** (упрощение) и роль **`E2EEService`** / **`CryptoResource`**.
- **`MlsService`:** javadoc — явное различие с полным MLS-handshake.
- **`CryptoResource`:** уточнён текст **`@Tag`** OpenAPI (без заявления полного RFC 9420 handshake).

### ТЗ п. 59–60: Solr после edit/delete

- **`MessageWorkerEvent`:** опциональное поле **`index_op`** (**`update`** / **`delete`**); фабрики **`fromPersistedMessage`**, **`forIndexDelete`**; **`fromSendEvent`** без изменения контракта для pipeline (**`index_op`** = null).
- **`MessageService`:** публикация в **`msg.event.index`** после успешного **`editMessage`** и **`deleteMessage`**.
- **`IndexerWorker`:** **`deleteById`** + commit при **`index_op=delete`**; иначе прежний upsert.
- **`ArchiverWorker`:** при **`index_op=delete`** — **`DELETE`** из **`archive_message_meta`** (если архив включён), затем handoff в deep-archive как раньше.
- **`PreviewWorker`:** игнор событий **`delete`** (нет контекста для превью).
- **`MessageRepository.loadMessagesForSearchResults`:** фильтр **`m.deleted = false`**.
- **`docs/TZ_SERVER_100.md`**, **`docs/NATS_SUBJECTS_INTEROP.md`:** пункты **59–60** закрыты.

### ТЗ п. 33: граница API по read receipts

- **`docs/TZ_SERVER_100.md`:** пункт **33** закрыт как **осознанный объём**: per-message / per-participant receipts **вне** **`chat_read_state`** не входят в API; для синхронизации «прочитано» используются только агрегат и эндпоинты **п. 34** (**`POST .../read`**, **`GET .../unread-count`**).

### Серверный logout (отзыв refresh)

- **`POST /api/v1/auth/logout`** с телом **`{"refresh_token":"..."}`** — отзыв refresh в Keycloak (**RFC 7009**, `openid-connect/revoke`); успех — **204**; при сбое отзыва (Keycloak недоступен / неожиданный статус) — **502** и **`ApiError`**. Тот же **rate limit** по IP, что у логина (**`AuthRateLimiter.allowLogout`**). Публичный путь в **`JwtAuthFilter`**. Скрипт **`smoke-auth.ps1`**: опциональная проверка logout после login/refresh, ключ **`-SkipLogout`**.

---

## 2026-05-09

### ТЗ п. 1–82: синхронизация трекера с кодом + опциональный JWT `aud`

- **`docs/TZ_SERVER_100.md`:** пункты **1–8, 10–38, 39–44, 45–55, 56–58, 61–62, 64–73, 82** отмечены как реализованные со ссылками на API/классы; далее закрыты **33**, **59–60**, **63**, **89** (см. записи **[Unreleased]**).
- **`AppConfig` / `TokenValidator`:** опциональная проверка **`aud`** (**`KEYCLOAK_AUDIENCE`**, **`keycloak.audience`**).
- **`scripts/TEST_SERVER_READY.md`:** упоминание **`KEYCLOAK_AUDIENCE`**.

### ТЗ §93–§97, §99–§100: тесты, OpenAPI, скрипты, убран символ §

- Тест **`ChatDtoJsonTest`** (snake_case для **`ChatResponse`**).
- OpenAPI / Swagger: отмечено в **`TZ_SERVER_100`**; в описаниях операций и комментариях **§** заменён на **«ТЗ п. …»** (в т.ч. **`ChatResource`**, **`FileResource`**, **`AdminResource`**, **`PrometheusMetricsResource`**, **`application.properties`**, **`V010`** SQL).
- Пункты **93–97**, **99–100** в **`docs/TZ_SERVER_100.md`** отмечены выполненными с отсылками к коду и скриптам.
- Дополнительно: **`NatsSubjects`**, **`TypingEvent`**, **`FLYWAY_AND_SCHEMA`**, javadoc репозиториев — та же замена **§** → **«ТЗ п. …»**.

### ТЗ §88, §90–§92, §98: экспорт, NATS interop, JWT для файлов

- **`docs/NATS_SUBJECTS_INTEROP.md`:** таблица subject’ов, DTO, core-api / workers / **ws-gateway**.
- **`NatsSubjects`:** ссылка на документ interop.
- **`ExportResource`:** уточнённое описание OpenAPI (очередь **export-replay**, stub, не GDPR-пакет).
- **`JwtAuthFilter`:** комментарий про **`/files/pub`** vs **`/files/auth-link`** (kind B с JWT).
- **`docs/TZ_SERVER_100.md`:** отмечены пункты **88**, **90–92**, **98**.

### ТЗ §85–§87: Flyway, индексы, схема audit_events

- **`V010__hot_path_indexes.sql`:** идемпотентные индексы под блоки, **`users.org_id`**, активные сообщения в чате, срок жизни публичных ссылок.
- **`V001`:** удалено создание legacy-**`audit_events`** (схема только в **`V008`**).
- **`V008`:** в начале **`DROP TABLE IF EXISTS audit_events CASCADE`** перед **`CREATE`**, чтобы убрать старую таблицу с копий после прежнего V001.
- Документ **`docs/db/FLYWAY_AND_SCHEMA.md`** (идемпотентность, checksum/**`repair`**, политика FK).
- **Важно:** после подтягивания изменений в **`V001`/`V008`** на окружениях с уже применёнными миграциями может понадобиться **`flyway repair`**.

### ТЗ §79–§84: CORS, MDC в логах, админ-роли, безопасное логирование auth

- **CORS:** **`CorsPreflightFilter`** (**`OPTIONS`**, `@PreMatching`), **`CorsResponseFilter`**, **`CorsOriginPolicy`** / **`cors.allowed.origins`** (env **`CORS_ALLOWED_ORIGINS`**); удалён старый **`CorsFilter`**.
- **Логи:** **`RequestContextMdcFilter`** / **`RequestContextMdcClearFilter`**, MDC **`http.method`** / **`http.path`**, обновлён **`logback.xml`**.
- **`AdminResource`:** **`@RolesAllowed("admin")`** на уровне класса.
- **`AuthService`:** при ошибках Keycloak в лог пишется только HTTP-статус, не тело ответа.
- Документы: **`docs/TZ_SERVER_100.md`**, комментарий в **`application.properties`**.

### Health / smoke-ready / трекер ТЗ

- **`scripts/smoke-ready.ps1`:** опция **`-StrictDependencies`** — после **`database_ok`** проверяются **`redis_ok`** и **`nats_ok`** из **`GET /api/v1/health/ready`**.
- **`docs/TZ_SERVER_100.md`:** пункты **74–78** отмечены как выполненные (фактические пути **`/api/v1/health`**, **`/api/v1/health/ready`**, поля **`redis_ok`/`nats_ok`**, метрики в т.ч. **`api_invalid_uuid_parameter_total`**).
- **`scripts/TEST_SERVER_READY.md`:** описание **`StrictDependencies`**.

### Неверный UUID: исключение, mapper, метрика, тесты ACL файла

- **`InvalidUuidParameterException`** + **`InvalidUuidParameterExceptionMapper`**: **`UuidParams.required`** больше не бросает **`WebApplicationException`**; ответы **400** + **`ApiError`** как раньше; счётчик **`api_invalid_uuid_parameter_total`** (**`ApiValidationMetrics`**).
- **`CurrentUserId.uuid`:** разбор JWT **`sub`** через тот же **`UuidParams`** (см. ресурсы **`SearchResource`**, **`ContactResource`**, **`AdminResource`**, **`UserResource`** `/me` и др.).
- Тесты: **`InvalidUuidParameterExceptionMapperTest`**, **`MessageRepositoryViewerMayAccessFileH2Test`** (H2 в **`core-api`**), **`ExportResourceTest`** на новое исключение.

### ACL файлов по общему сообщению в чате, блоки в fan-out, UUID, метрики

- **`MessageRepository.viewerMayAccessFileViaSharedNonE2eeMessage`:** не-E2EE сообщение с **`trim(content)` = file UUID**, участник чата не в бане, те же правила **`blocks`**, что у ленты.
- **`FileService.mayViewFile`**, **`GET /api/v1/files/{fileId}`** и **`GET .../download`:** владелец **или** проверка выше; **`DELETE`** без изменений (только владелец).
- **`UuidParams.required`:** единый разбор UUID (**400** + **`ApiError`**) в **`ChatResource`**, **`MessageResource`**, **`FileResource`** (где применимо); см. также mapper выше.
- **`PipelineFanoutLogic`:** исключение получателей с блокировкой с отправителем (в обе стороны); тест **H2** с таблицей **`blocks`**.
- **`ApiDeniedMetrics`:** счётчики **`api_denied_file_access_total`**, **`api_denied_message_send_total`** (экспорт через существующий **`PrometheusMetricsResource`**).
- Документы: **`docs/TZ_SERVER_100.md`**, **`scripts/TEST_SERVER_READY.md`**.

### Целевая структура пакетов ядра (hexagonal)

- **`docs/ARCHITECTURE_CORE_PACKAGES.md`:** границы `domain` / `application` / `port` / `adapter.*`, таблица соответствия текущему `api.*`, фазы миграции.
- Якорные пакеты **`com.avandocmsg.messenger.core.*`** (`package-info.java` в модуле **`core-api`**) без переноса существующих классов.

### Фаза 1 портов: NATS

- **`NatsOutboundPort`** (`core.port`): **`publish`**, **`flush`**, **`publishPipelineMessageSend`** (JetStream vs core NATS как раньше в **`MessageService`**).
- **`NatsConnectionOutbound`** (`core.adapter.messaging`): реализация над **`Connection`** и **`Optional` из JetStream**; собирается в **`MessengerApplication`**, биндится в HK2 рядом с **`Connection`** (для **`HealthResource`**).
- **`ChatService`**, **`MessageService`**, **`ExportResource`** переведены на порт; тесты — **`NatsOutboundPort.noop()`** или запись вызовов.
- **`NatsConnectionStatus`**, **`UuidGenerator`**, биндинг **`Clock.systemUTC()`** в HK2; **`HealthResource`** → статус NATS через порт; **`FileService`** / **`FileResource`** (TTL ссылок) и генерация id в **`ChatService`** / **`MessageService`** / **`ExportResource`** через **`UuidGenerator`**.
- **`Clock` / `UuidGenerator` в конструкторе `MessengerApplication`:** те же экземпляры для **`TokenValidator`** (JWKS TTL и проверка **`exp`**), **`AuthService`**, репозиториев **`ChatRepository`**, **`MessageRepository`**, **`OrganizationRepository`**, **`FilePublicLinkRepository`**, **`ChatBanRepository`**, **`KeyPackageRepository`**, **`SessionRepository`**, **`ConferenceRepository`** (вместо разрозненных **`Instant.now()` / `UUID.randomUUID()`**).

---

## 2026-05-08

### Файлы и мелкие ACL

- **`GET/DELETE /api/v1/files/{fileId}`**, **`GET .../download`:** доступ только **владельцу** (`uploaded_by`); неверный UUID файла — **400**.
- **`MessageResource`:** безопасный разбор UUID для **send, list (before), get, edit, delete**; пустой **`before`** игнорируется; **PATCH** сообщения без тела/контента — **400**.
- **`POST .../export`:** участники с **banned** в чате не могут ставить задачу экспорта (**403**).

### Дополнение — ACL сообщений (реакции, pin, версии, forward), поиск пользователей

- **`ChatRepository.findById`:** доступ только если пользователь — участник не забанен; для **p2p** тот же критерий, что и в списке чатов (нет активной блокировки с собеседником).
- **`MessageService`:** **`messageVisibleToViewer`** для GET сообщения, **реакций**, **версий правок**, **pin**; **`canAccessChat`** для списка/отправки/**unpin**/закреплённых; **`getPinnedMessages`**; отправка только членами (**403** «Not a member»); **edit/delete** — проверка `chat_id`; **forward** без лишней проверки только **`sendBlockedReason`**.
- **`MessageResource`:** **403** членства для списка, закреплённых, версий, реакций, pin/unpin; неверный UUID — **400**; pin/add reaction при недоступном сообщении — **404**; **forward** при запрете источника или назначения — **403** из **`sendBlockedReason`**, иначе при ошибке пересылки — **400**.
- **`GET /api/v1/chats/{chatId}`:** разбор UUID с **400** при неверном формате.
- **`GET /api/v1/search/users`:** **`UserRepository.searchForViewer`**, ответ **`UserSearchHit`**; **`SearchResource`** — OpenAPI (**`UserSearchHit`** в схеме).

### 23:55 UTC — Блокировки по ТЗ (раздел 10)

- **REST:** **`POST /api/v1/blocks`** (`{"user_id":"..."}`) — блокировка; **`DELETE /api/v1/blocks/{userId}`** — снятие; **`GET /api/v1/blocks`** — список с **`blocked_at`**.
- При блокировке удаляются записи **контактов в обе стороны**; **добавление контакта** запрещено при любой блокировке между пользователями.
- **Сообщения:** в ленте исключаются отправители с блокировкой в **любую сторону** (не только «я заблокировал»); отправка в **P2P** при взаимной блокировке — **403**; пересылка учитывает те же ограничения.
- **Чаты и поиск:** список чатов и **`listChatIdsForUser`** скрывают **P2P** при активной блокировке с собеседником (согласовано с отсутствием нового P2P при блоке).

### 22:45 UTC — Прочитано / непрочитано, блоки, готовность Redis+NATS, отзыв ссылок, удаление org

- **Миграция `V009`:** таблица **`chat_read_state`** (`last_read_message_id` по паре user/chat).
- **Чаты:** **`POST /api/v1/chats/{chatId}/read`**, **`GET .../unread-count`**, **`POST .../typing`** (NATS **`msg.typing`**); **`ChatReadRepository`**, расширен **`ChatService`** и wiring в **`MessengerApplication` / `JerseyConfig`** (**`ChatReadRepository`**, **`BlocksResource`**).
- **Блокировки:** **`GET /api/v1/blocks`** — список заблокированных (**`BlockedUserResponse`**).
- **Профиль:** **`GET /api/v1/users/me/saved-chat`** — id чата «Хранилище».
- **`GET /api/v1/health/ready`:** в **`HealthReadyResponse`** добавлены **`redis_ok`**, **`nats_ok`** (проверка Lettuce + статус NATS).
- **Файлы:** **`DELETE /api/v1/files/{fileId}/public-links/{linkId}`** — отзыв ссылки (**`revoked_at`** в **`file_public_links`**).
- **Админ:** **`DELETE /api/v1/admin/organizations/{orgId}`** — удаление организации без пользователей (**409** если есть **`users.org_id`**).
- **Трекер объёма работ:** **`docs/TZ_SERVER_100.md`** (100 пунктов для серверной реализации по ТЗ).

### 20:30 UTC — Покрытие ТЗ (сервер, без клиентов): поиск, Хранилище, ссылки, аудит, org, метрики

- **Миграция `V008`:** таблицы **`organizations`**, **`audit_events`**, **`file_public_links`**; колонка **`users.org_id`**; тип чата **`saved`** («Хранилище» §30).
- **Индексация Solr:** в **`MessageWorkerEvent`** добавлено опциональное поле **`searchText`** (только для не‑E2EE); воркер **indexer** пишет в Solr поле **`content_txt`**.
- **Поиск сообщений:** **`GET /api/v1/search/messages?q=`** — при **`SOLR_URL`** / **`SOLR_ZK`** запрос к Solr + ACL по чатам пользователя; иначе **SQL** по plaintext с фильтрами блоков и членства.
- **Хранилище:** при логине/регистрации **`ensureSavedVaultChat`** создаёт чат **`saved`** «Saved Messages»; **`POST .../messages/{msgId}/forward`** с **`target_chat_id`** копирует сообщение (в т.ч. в vault); редактирование в **`saved`** запрещено.
- **Публичные ссылки на файлы §15:** **`POST /api/v1/files/{file_id}/public-links`** (`link_kind` A/B/C, TTL); выдача **`GET /api/v1/files/pub/{token}`** (A,C + пароль для C), **`GET /api/v1/files/auth-link/{token}`** (B + JWT).
- **Админ (realm admin):** **`GET /admin/audit-events`**, **`GET/POST /admin/organizations`**, **`PATCH /admin/users/{userId}/organization`**.
- **Профиль:** **`UserProfile.org_id`**.
- **Наблюдаемость §22 (база):** **`GET /api/v1/metrics/prometheus`** (JVM/process через Prometheus simpleclient).
- **`JwtAuthFilter`:** публичные пути **`/metrics`**, **`/files/pub`**.

---

## 2026-05-12

### 09:10 UTC — Скрипты тестового стенда и критерии готовности

- **`scripts/dev-infra-up.ps1`** — Docker: postgres-hot, redis, nats, minio; опция `-WithKeycloak` добавляет keycloak.
- **`scripts/dev-keycloak-up.ps1`** — только postgres-hot + keycloak (realm из `keycloak/`).
- **`scripts/run-core-api-local.ps1`** — переменные окружения по умолчанию для localhost и запуск `gradlew :modules:core-api:run`.
- **`scripts/TEST_SERVER_READY.md`** — когда считать готовым тестовый запуск: минимум **GET /api/v1/health** после инфры; полный JWT-тест — после Keycloak и совпадения issuer/JWKS.

### 08:45 UTC — Логика fan-out пайплайна и тесты

- Выделен класс **`PipelineFanoutLogic`** (`loadRecipientUserIds`) — выбор получателей из `chat_members` (исключая отправителя и `banned`). **`MessagePipelineWorker`** использует его вместо дублирования SQL.
- **Тесты:** `PipelineFanoutLogicTest` на встроенном **H2** + **HikariCP** (`testImplementation com.h2database:h2`). Проверки: исключение отправителя и забаненных; пустой список при одном участнике-отправителе.

---

## 2026-05-08

### 18:00 UTC — Готовность prod-like: файлы, compose, smoke

- **`FileService`:** лимит загрузки = **`AppConfig.mediaMaxUploadBytes()`** (не константа 50 MB); метод **`uploadStream`** для multipart.
- **`POST /api/v1/files/upload`** с **`multipart/form-data`**, поле **`file`** (**`MultiPartFeature`**); сырое тело (**octet-stream**) без изменений по смыслу.
- **`docker-compose.dev-min.yml`:** для **core-api** добавлены **MinIO**, **MEDIA_MAX_UPLOAD_BYTES**, **JITSI_MEET_BASE_URL**, **WEBRTC_STUN_URIS**, зависимость от сервиса **minio**.
- **`run-core-api-local.ps1`:** значения по умолчанию для **MINIO_*** (локальный стенд с **dev-infra-up**).
- **`scripts/smoke-ready.ps1`** — автоматическая проверка health → ready → capabilities → login → admin session.

### 17:00 UTC — Присутствие, медиа-возможности, конференции (Jitsi)

- **Миграция `V007`:** поля **`presence_status`**, **`last_seen_at`** у **`users`**; таблицы **`conferences`**, **`conference_participants`**.
- **Профиль:** в **`UserProfile`** добавлены **`presence_status`**, **`last_seen_at`**; **`PATCH /api/v1/users/me/presence`** (`presence_status`: online | away | dnd | offline); **`POST /api/v1/users/me/heartbeat`** обновляет **`last_seen_at`**.
- **Медиа:** **`GET /api/v1/media/capabilities`** (публичный путь без JWT) — лимит загрузки, список типов сообщений с вложениями, STUN из **`WEBRTC_STUN_URIS`**, базовый URL Jitsi; изображения/видео как и раньше через **`POST /api/v1/files/upload`** и сообщения с типом **`image`** / **`video`** / **`file`**.
- **Конференции:** создание в чате **`POST /api/v1/chats/{chat_id}/conferences`**, список, **`join_url`** на meet.jit.si (или **`JITSI_MEET_BASE_URL`**), участники join/leave/end; медиапоток не проходит через core-api — клиент открывает **`join_url`** или свой WebRTC.
- **`AppConfig`:** **`media.max.upload.bytes`**, **`jitsi.meet.base.url`**, **`conference.room.prefix`**, **`webrtc.stun.uris`**.

### 16:00 UTC — Единый контракт REST: snake_case по всему API

- Все публичные DTO **`modules/core-api/.../dto`** используют **`@JsonProperty`** с **snake_case** в JSON (сообщения, чаты, контакты, пользователи, файлы, баны, MLS и т.д.).
- Убраны **`@JsonAlias`** для camelCase: клиенты должны слать **`display_name`**, **`refresh_token`**, **`member_ids`** и т.п.
- **`HealthReadyResponse.database_ok`** в **`common`** (зависимость **`jackson-annotations`** добавлена в **`modules/common`**).
- **Не изменялись** DTO внутренних событий NATS (**`MessageSendEvent`**, **`ExportReplayJob`**, …) — формат очередей между сервисами прежний.

### 15:30 UTC — Единый JSON для auth/admin DTO

- **`RegisterResponse`:** **`user_id`**, **`display_name`** в JSON (совместимо с контрактом login/refresh).
- **`RegisterRequest`:** **`display_name`** в JSON, **`displayName`** через **`@JsonAlias`**.
- **`AdminSessionResponse`:** **`user_id`**, **`realm_roles`**, **`api_version`**.
- Тест **`AuthDtoJsonTest`** — проверка имён полей при сериализации/десериализации.

### 15:00 UTC — Refresh с телом как у login, OAuth JSON, Docker, WebSocket

- **`POST /api/v1/auth/refresh`** возвращает **`LoginResponse`** (новые **`access_token`** / **`refresh_token`** / **`expires_in`**), а не пустое тело; **`AuthService.refreshAccessToken`**. Запрос принимает **`refresh_token`** или **`refreshToken`** (**`@JsonAlias`**).
- **`LoginResponse`:** в JSON поля в стиле OAuth (**`access_token`**, **`refresh_token`**, **`expires_in`**).
- **`docker-compose.dev-min.yml`:** у **`core-api`** переменные **`KEYCLOAK_MASTER_*`** и зависимость от сервиса **`keycloak`** (регистрация через Admin API внутри сети compose).
- **`MessagingWebSocket`:** **`URLDecoder`** для query-параметра **`token`** (корректные JWT в URL).
- **`smoke-auth.ps1`:** цепочка **login → refresh → admin/session** (ключ **`-SkipRefresh`** отключает refresh).

### 14:30 UTC — Пакет из 10 этапов (регистрация, master, health, smoke)

1. **Регистрация:** сначала создание пользователя в Keycloak; **`id` в PostgreSQL** берётся из **`Location`** ответа Admin API или из **`GET .../users?exact=true`** — совпадение **`sub`** и строки в **`users`** без лишнего upsert при первом логине.
2. **409** от Keycloak при занятом имени → конфликт регистрации (как и раньше на уровне API).
3. **`KEYCLOAK_MASTER_USER` / `KEYCLOAK_MASTER_PASSWORD`** и свойства **`keycloak.master.*`** — учётка realm **master** для **admin-cli** (не хардкод только в коде).
4. **`JwtAuthFilter`:** без JWT доступны **`openapi.json`** / **`openapi.yaml`** (просмотр Swagger без логина).
5. **`POST .../auth/refresh`:** при успешном ответе разбор **`access_token`** и **`upsertFromKeycloak`** (актуальный профиль после refresh).
6. **`GET /api/v1/health/ready`** и DTO **`HealthReadyResponse`** — **`SELECT 1`** к БД; при недоступности PostgreSQL тело JSON и статус **503**.
7. **`scripts/smoke-auth.ps1`** — логин и **`GET /api/v1/admin/session`** (выход **0** при успехе).
8. **`run-core-api-local.ps1`:** значения по умолчанию для **`KEYCLOAK_MASTER_*`**.
9. **`application.properties`:** комментарий и ключи master по умолчанию.
10. Обновлён **`TEST_SERVER_READY.md`** (готовность и smoke).

### 13:15 UTC — `@RolesAllowed`, админ API и OpenAPI Bearer

- **`RolesAllowedDynamicFeature`** в **`JerseyConfig`** — Jakarta **`@RolesAllowed("admin")`** на ресурсах.
- **`GET /api/v1/admin/session`** (**`AdminResource`**) — для учёток с realm-ролью **admin** возвращает `userId`, `username`, `realmRoles`, `apiVersion` (проверка UI после входа **csadmin**/admin).
- **`ForbiddenExceptionMapper`** — ответ **403** с **`ApiError`**, без падения в общий 500.
- **OpenAPI:** схема **`bearerAuth`** (JWT) в **`OpenApiConfig`**.

### 13:00 UTC — Realm-роли Keycloak в `SecurityContext`

- **`RealmRoleExtractor`** — разбор `realm_access.roles` из JWT.
- **`UserPrincipal`** хранит набор realm-ролей; **`JwtAuthFilter`** выставляет **`isUserInRole(role)`** по ним (для `csadmin`/`admin` с ролью **`admin`** в токене).

### 12:30 UTC — Тестовый суперпользователь `csadmin`

- **`keycloak/avandocmsg-realm.json`:** пользователь **`csadmin` / `csadmin`** с realm-ролью **`admin`** — основная учётка для проверок и визуальной настройки сервера из клиента (рядом с существующим `admin`/`admin`).
- **`scripts/TEST_SERVER_READY.md`:** таблица тестовых пользователей realm и напоминание про учётную запись консоли Keycloak.

### 12:00 UTC — Авторизация Keycloak: ROPC, admin API, синхронизация `users`

- **`keycloak/avandocmsg-realm.json`:** для клиентов `messenger-web` и `messenger-mobile` включено **`directAccessGrantsEnabled`** (password grant для API-логина).
- **`AppConfig`:** `keycloakBaseUrl`, `keycloakMasterTokenEndpoint`, **`keycloakAdminRealmBase`** — корректные URL для master-токена и Admin REST (`/admin/realms/{realm}` вместо ошибочной подстановки).
- **`AuthService`:** токен админа запрашивается у **realm `master`**; после успешного логина из access token читаются `sub`, `preferred_username`, `name` и вызывается **`UserRepository.upsertFromKeycloak`** — строка в PostgreSQL с `id = sub`, чтобы защищённые эндпоинты находили пользователя по JWT.

### 09:15 UTC — Пайплайн, воркеры, интеграции

- **NATS / пайплайн:** константы тем в `NatsSubjects`; после fan-out на `msg.deliver.{userId}` публикация метаданных в `msg.event.index`, `msg.event.push`, `msg.event.bot` (без тела сообщения в событии). DTO `MessageWorkerEvent` для downstream-воркеров.
- **Отправка сообщений:** публикация в `msg.send` через `NatsSubjects.MSG_SEND`; в событие передаётся `clientMsgId` (исправление рассинхрона с дедупом).
- **Воркеры:** у всех модулей Gradle добавлены исполняемые `main`, подписки на NATS (очереди где уместно), Logback.
- **Архивация:** archiver — опциональная запись метаданных в Archive Postgres (`ARCHIVE_JDBC_URL`), затем публикация в `msg.event.deep-archive`.
- **Deep archive:** потребление `msg.event.deep-archive`, опциональная запись JSON в MinIO.
- **Индексация:** indexer — индекс в Solr только метаданных при `SOLR_URL` / `SOLR_ZK` (без plaintext для E2EE).
- **Preview:** извлечение URL из `messages.content` в Hot DB (для не‑E2EE), SSRF-ограничения, TTL-кэш; переменные `PREVIEW_*`.
- **Push / bot / export-replay:** MVP — разрешение устройств и вебхуков метаданными без текста сообщения; таблица `bot_webhook_subscriptions` (миграция V006); export-replay — потребление `msg.export.replay`, запись stub-файла, опционально `msg.export.replay.complete`.
- **Общее:** `HikariDataSources` в `common`; `ExportReplayJob` / `ExportReplayCompleteEvent`.
- **WebSocket:** подписка на доставку использует префикс из `NatsSubjects.MSG_DELIVER_PREFIX`.
- **REST:** `POST /api/v1/chats/{chatId}/export` — постановка задачи экспорта в NATS (`msg.export.replay`) для ролей **owner** и **admin** (см. код).

### 10:40 UTC — NATS JetStream (опционально)

- Стрим `MESSAGING` для темы `msg.send` (`JetStreamMessagingSetup` в `common`). В **core-api**: `nats.jetstream` / env `NATS_JETSTREAM` — публикация через `JetStream.publish` при старте создаёт/проверяет стрим. В **message-pipeline**: env `NATS_JETSTREAM=true` — push-подписка с durable `pipeline-msg-send`, ручной **ack/nak** после fan-out и `msg.event.*`. Включать **оба** сервиса с JetStream одновременно.  
- **Зависимости:** `modules/common` тянет `jnats` (общая настройка стрима).  
- **Тесты:** `MessageService` принимает `Optional<JetStream>`; unit-тесты с `Optional.empty()`.

| Тема | Комментарий (уточнение к ТЗ) |
|------|------------------------------|
| JetStream / at-least-once | Режим **опция**; `msg.event.*`, `msg.deliver.*`, `msg.export.replay` по умолчанию без стримов. Полное покрытие JetStream не заявлено. |

### 11:55 UTC — Rate-limit auth (Redis)

- Опционально `rate.limit.auth.enabled=true` / env `RATE_LIMIT_AUTH_ENABLED` — Redis при старте; счётчики по IP (`X-Forwarded-For` или remote): логин — окно 60 с, по умолчанию 60/мин; регистрация — окно 1 ч, по умолчанию 5/ч. Lua `INCR`+`EXPIRE`, при ошибке Redis — **fail-open**. Ответ **429** с `ApiError`. Класс `AuthRateLimiter`, закрытие Redis в `MessengerApplication.stop()`.

| Источник | Комментарий |
|----------|-------------|
| `tz_revision_proposal.md` (rate-limit) | Реализован **первый слой** только для `/v1/auth/login` и `/v1/auth/register`; остальные API — не охвачены. |

### 14:05 UTC — Unit-тесты, целостность сборки, исправление noop

- **Тесты:** `modules/common` (`MessageWorkerEvent`, `NatsSubjects`), `modules/core-api` (`AuthRateLimiter`, `ExportResource` через JDK `Proxy`/`stub`; `AppConfigRateLimitDefaultsTest` с `assume` при env), `modules/workers/preview` (`UrlExtractor`, `SsrfGuard`, `TtlStringCache`), `modules/ws-gateway` (`WsTokenValidator`).
- **Исправление:** `AuthRateLimiter.noop()` — NPE в `allowLogin`/`allowRegister` из‑за порядка вычисления аргументов; ранний выход `if (!enabled) return true`.
- **Сборка:** задача Gradle **`buildIntegrity`** — `build` во всех подпроектах (`gradlew buildIntegrity`). JVM для тестов: `-Dnet.bytebuddy.experimental=true`.
- **Бэклог:** воркеры без отдельных тестов; JaCoCo — по необходимости.

### 14:10 UTC — Отклонения от `tz_full.html` (сводка)

| Тема | В ТЗ (полное HTML) | Факт в проекте |
|------|---------------------|----------------|
| Версия Java | 17 | Целевая **25** (`build.gradle.kts`) — осознанное расхождение. |
| Регистрация §7 | Телефон + SMS OTP | Логин/пароль и **Keycloak** — ближе к MD, не к §7 HTML. |
| Поиск сообщений | Solr + ACL | API поиска — SQL; Solr у indexer при настройке окружения. |
| NATS | JetStream, at-least-once | По умолчанию core NATS; JetStream для `msg.send` — **опция** (см. блок **10:40 UTC**). |
| E2EE сообщений | §24 | **`MlsService` не RFC 9420**; цель — MLS по MD. |
| Клиенты | Web / Android / Desktop | В репозитории — сервер и воркеры. |
| Экспорт | по ТЗ | Воркер-заглушка + **POST экспорта**; полный compliance — не завершён. |

---

## Как вести журнал дальше

1. Новые записи добавляйте **в начало** файла под заголовок **`## ГГГГ-ММ-ДД`**.
2. Несколько изменений за один день — подзаголовки **`### ЧЧ:ММ UTC — краткое название`** (или локальный пояс, если договорились иначе).
3. «Отклонения от ТЗ» переносите в общую таблицу при необходимости или дублируйте ссылку на время блока.
4. По возможности сверяйте дату/время с **`git log --format=%ci`** для точности.
