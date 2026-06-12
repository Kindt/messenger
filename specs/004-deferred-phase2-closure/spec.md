# Feature Specification: Deferred Phase 2 Post-Backlog Closure

**Feature Branch**: `004-deferred-phase2-closure`

**Created**: 2026-06-09

**Status**: Engineering Complete — pending ops/security sign-off (see [ops-signoff-log.md](ops-signoff-log.md))

**Input**: Close deferred post-backlog items E0–E10: production TLS/Vault, hexagonal write-path, JFR profiling for all workers, Playwright full-stack gates, governance sign-off, full E2EE (client-side MLS encrypt), optional QEMU dev stability.

**Related specs**: `001-system-review-refactoring`, `002-web-client-server-parity`, `003-docker-ansible-autotest`

## User Scenarios & Testing

### User Story 1 — Production TLS and secrets (Priority: P1)

As an operations engineer, I want stage and production deployments to use encrypted Ansible Vault secrets and HTTPS termination, so that credentials never appear in git and users connect securely over HTTPS and WSS.

**Why this priority**: Blocks safe prod rollout; extends completed Phase 8 scaffold from spec 003.

**Independent Test**: Deploy stage inventory with `--ask-vault-pass`; run `scripts/smoke-tls-redirect.ps1` — exits 0 with valid redirect and certificate subject.

**Acceptance Scenarios**:

1. **Given** encrypted `vault.yml` with DB and Keycloak passwords, **When** `site.yml` runs on stage, **Then** services start without plaintext secrets in compose env files on disk.
2. **Given** `korus_tls_enabled: true` and valid DNS, **When** user opens `http://host/`, **Then** browser is redirected to HTTPS and web UI loads.
3. **Given** TLS-enabled web client, **When** user opens a chat, **Then** WebSocket connects via `wss://` without mixed-content errors.
4. **Given** dev/QEMU with `korus_tls_enabled: false`, **When** existing smoke scripts run, **Then** HTTP paths remain green (no regression).

---

### User Story 2 — Hexagonal write-path for User, Organization, File (Priority: P1)

As a backend developer, I want REST write operations for users, organizations, and files to flow through application services and ports, so that business logic is testable and consistent with the completed read-path refactor.

**Why this priority**: Core architecture debt from post-backlog E4–E6; enables safe legacy cleanup.

**Independent Test**: `./gradlew.bat buildIntegrity` green; H2 adapter tests cover all new write port methods; User PATCH returns same shape as GET via domain mapper.

**Acceptance Scenarios**:

1. **Given** authenticated user, **When** PATCH `/api/v1/users/me` with display name, **Then** change persists via `UserApplicationService` and GET `/me` reflects update.
2. **Given** admin role, **When** creating/deleting organization via admin API, **Then** operations use `OrganizationApplicationService` not direct JDBC in resource.
3. **Given** authenticated user, **When** uploading a file, **Then** metadata and object storage go through `FileApplicationService` ports.
4. **Given** heartbeat POST, **When** called, **Then** presence timestamp updates via port without legacy repository in resource.

---

### User Story 3 — Hexagonal tail and legacy cleanup (Priority: P2)

As a maintainer, I want saved-chat, public file links, read benchmarks, and unused legacy repositories removed after write-path migration, so that hexagonal refactor is fully closed.

**Why this priority**: Completes scope limits explicitly deferred in E4–E6 first PRs.

**Independent Test**: `CoreApiBenchmarkTest` includes File metadata and Organization read budgets; `docs/plans/08-hexagonal-refactoring.md` marks write-path completed.

**Acceptance Scenarios**:

1. **Given** user with saved chat preference, **When** GET/PUT saved-chat, **Then** operation uses hexagonal port (not legacy `UserRepository` in resource).
2. **Given** file owner, **When** creating/revoking public link, **Then** operation uses dedicated port adapter.
3. **Given** write-path fully migrated, **When** legacy write methods remain only for auth registration, **Then** dedicated cleanup PR removes dead code.

---

### User Story 4 — JFR profiling for all workers (Priority: P2)

As a performance engineer, I want JDK-based profiling Docker images for every worker plus compose overlay, so that I can capture JFR inside containers without host `jcmd`.

**Why this priority**: Completes E2 scope (3/8 done); unblocks hotspot analysis on pipeline/archiver/push workers.

**Independent Test**: `scripts/profiling/profile-docker-jfr.ps1` writes `.jfr` for each of 8 profiling targets in overlay.

**Acceptance Scenarios**:

1. **Given** profiling compose overlay up, **When** script runs against `message-pipeline`, **Then** a valid `.jfr` file is copied to host.
2. **Given** production `Dockerfile.*` (JRE), **When** comparing to profiling variant, **Then** only base image tag differs (JDK vs JRE).

---

### User Story 5 — Playwright full-stack parity gates (Priority: P2)

As a QA engineer, I want browser E2E to cover parity-matrix user-facing domains on a stable full stack, so that UI regressions are caught beyond API smokes.

**Why this priority**: Completes E7 gaps (skips, DOM scenarios, operator sign-off path).

**Independent Test**: `npx playwright test` in `tests/e2e-web/` exits 0 against QEMU or full-server stack.

**Acceptance Scenarios**:

1. **Given** full stack running, **When** Playwright suite runs, **Then** all non-admin parity-matrix rows have at least one spec scenario (pass or documented waiver).
2. **Given** auth session spec, **When** logout control exists in shell, **Then** logout scenario runs (not permanently skipped).
3. **Given** files-export spec, **When** export worker available, **Then** DOM upload or download path is exercised per HANDOFF T016.
4. **Given** operator completes HANDOFF checklist, **When** results recorded, **Then** `runtime-gate-report.md` operator section is updated.

---

### User Story 6 — Governance and documentation closure (Priority: P3)

As a tech lead, I want named governance approvers and synchronized plan documents, so that post-backlog closure is auditable.

**Why this priority**: E0 hotplug has placeholder approver names; docs/plans need final status.

**Independent Test**: ADR Approval Log lists real names; `docs/plans/05`, `06`, `08` reflect completed or dated deferred status; `SMOKE_INDEX.md` lists all new smokes.

**Acceptance Scenarios**:

1. **Given** hotplug ADR accepted, **When** sign-off script runs with real owners, **Then** Approval Log table shows actual names and dates.
2. **Given** phase 2 work merged, **When** reviewing `CHANGELOG.md`, **Then** entry documents closure scope.

---

### User Story 7 — Full E2EE with client-side MLS (Priority: P1, gated)

As an end user in an MLS-enabled chat, I want messages encrypted on my device before send, so that the server cannot read plaintext content.

**Why this priority**: Highest-risk post-backlog item (E8–E10); phase 1 delivered wire only.

**Blocking prerequisite**: Product sign-off after library spike (T130) before crypto implementation tasks.

**Independent Test**: Interop test suite green; legacy clients still send/receive with `e2ee_scheme=legacy`; security review checklist signed before prod.

**Acceptance Scenarios**:

1. **Given** MLS-active chat and capable client, **When** user sends message, **Then** wire payload is MLS ciphertext and server preview endpoint is disabled or restricted.
2. **Given** new member added to MLS group, **When** epoch rotates, **Then** Welcome/Commit flow delivers keys to new device.
3. **Given** legacy client, **When** sending to mixed chat, **Then** legacy encrypt path still works.
4. **Given** admin dashboard, **When** viewing E2EE status, **Then** pending migrations and active group counts are accurate.

---

### User Story 8 — QEMU dev stability (Priority: P2, optional)

As a developer on Windows, I want reliable QEMU up/redeploy for full-stack QA, so that Playwright and manual UI verification are not blocked by SSH flakes or misconfigured upstream URLs.

**Why this priority**: Unblocks US5; not required for prod TLS/hex paths.

**Independent Test**: `scripts/qemu-up.ps1` then `scripts/qemu-redeploy.ps1 -WebOnly` — web UI shows login shell and API health reachable.

**Acceptance Scenarios**:

1. **Given** QEMU VMs running, **When** guest repo sync runs, **Then** transient plink stderr does not fail redeploy.
2. **Given** web VM deployed, **When** checking upstream env, **Then** API points to server host port 18080 not 8080.

---

### User Story 9 — Fast full-stack acceptance loop (Priority: P1)

As a developer fixing Playwright or web/API parity, I want a fast inner loop against an already-up QEMU stack, so that I do not wait for redeploy and the full 26-test suite on every iteration.

**Why this priority**: US5 full-stack gate took hours when mixed with infra wait and blind Playwright retries; inner loop unblocks daily dev.

**Independent Test**: With stack ready, `.\scripts\playwright-dev-loop.ps1 -Tier api` exits 0 in under 90s; `-Tier ui-messaging` exits 0 in under 3m.

**Acceptance Scenarios**:

1. **Given** API/UI health green, **When** inner loop runs preflight, **Then** it skips Playwright if env/ports wrong (no 5m wasted suite).
2. **Given** Playwright failure, **When** analysis runs, **Then** `plan-failure-analysis.json` lists category + recommendedAction; outer orchestrator does **not** re-run full suite with same fingerprint.
3. **Given** API-only spec failure, **When** developer runs `-Tier api`, **Then** only API-heavy specs run (no DOM login waits).
4. **Given** inner tiers green (`inner-tier-status.json`), **When** outer gate runs once, **Then** smoke + full Playwright + gate report complete.

---

### Edge Cases

- TLS cert renewal fails → rollback documented via `korus_tls_enabled: false` inventory flag; HTTP smokes still pass on dev.
- MLS client older than server → explicit `e2ee_scheme=legacy` fallback; no silent decrypt failure.
- Playwright without TURN → RTC UI tests use mock/skip with parity-matrix waiver note.
- Hex migration mid-flight → legacy `api.repository.*` retained for auth-only until US3 cleanup PR.
- Vault password lost → documented recovery via re-encrypt workflow in ansible README.

## Requirements

### Functional Requirements

- **FR-001** (US1): Stage/prod deploy MUST use ansible-vault; secrets MUST NOT appear in git.
- **FR-002** (US1): HTTPS termination MUST redirect HTTP; secure WebSocket MUST work for web clients.
- **FR-003** (US2): User profile/presence/privacy/heartbeat writes MUST flow through `UserApplicationService`.
- **FR-004** (US2): Admin organization CRUD MUST flow through `OrganizationApplicationService`.
- **FR-005** (US2): File upload/download/delete MUST use `ObjectStoragePort` and `FileMetadataPort`.
- **FR-006** (US4): Profiling overlay MUST NOT change production JRE images.
- **FR-007** (US5): Playwright MUST cover parity-matrix non-admin rows without undocumented permanent skips.
- **FR-008** (US7): MLS chats MUST encrypt on client; server MUST NOT expose plaintext preview when MLS active.
- **FR-009** (US7): Legacy E2EE scheme MUST remain functional for older clients.
- **FR-010** (US6): Hotplug ADR MUST list real approver names, not placeholders.
- **FR-011** (US9): Inner loop MUST NOT trigger `qemu-down` or full server redeploy unless analysis action is `redeploy_server`.
- **FR-012** (US9): All `.ps1` orchestration scripts MUST remain ASCII-only (i18n in `deploy/qemu/lib/plan-failure-i18n.json`).

### Key Entities

- **DeployTarget**: inventory group, TLS flags, vault-backed env vars.
- **HexWritePort**: repository port method contract per aggregate (User, Organization, File, PublicLink).
- **ProfilingTarget**: worker name, JDK image tag, compose service alias.
- **PlaywrightScenario**: spec file, parity-matrix row, pass/waiver status.
- **AcceptanceTier**: named Playwright subset (api, ui-messaging, …) with pass timestamp in `inner-tier-status.json`.
- **MlsGroupState**: epoch, member key packages, wire message types.
- **GovernanceSignoff**: role, approver name, decision date.

## Success Criteria

### Measurable Outcomes

- **SC-001**: Ops deploys stage with vault and verifies HTTPS in under 15 minutes using quickstart runbook.
- **SC-002**: Developers capture JFR from any of 8 worker containers in one script invocation.
- **SC-003**: QA runs one Playwright command and gets green on stable full stack.
- **SC-004**: MLS-enabled chat participants decrypt only their own messages on device; server audit shows no plaintext retention for MLS sends.
- **SC-005**: All post-backlog directions in `docs/plans/*.md` show `completed` or explicit deferred status with date.
- **SC-006** (US9): Inner tier retest after code change completes in under 2 minutes when stack is already up.
- **SC-007** (US9): Zero full-suite Playwright runs while preflight fails.
- **SC-008** (US9): Outer golden path runs at most once per fix batch (not per orchestrator retry tick).
- **SC-009** (US9): Playwright 26/26 on outer gate before `runtime-gate-report` operator approval.

## Assumptions

- Spec 003 Phase 8 TLS scaffold remains; US1 extends to prod rollout (Phase 9 cross-ref).
- E2EE spike (T130) selects WASM client + server library per updated ADR before US7 code tasks.
- Stage uses Let's Encrypt; production may use BYO-cert (documented in research.md).
- US8 is recommended before US5 full-stack gate but not blocking MVP (US1+US2).
- `buildIntegrity` remains PR gate; full-stack smokes are manual/optional CI per constitution.

## Clarifications Resolved

| Topic | Decision |
|-------|----------|
| E2EE library | Hybrid: server MLS library per spike ADR; browser WASM MLS for client encrypt |
| TLS on prod | Let's Encrypt on stage; prod supports BYO-cert with certbot optional |
| US8 vs US5 | US8 optional; US5 full-stack gate documents QEMU prerequisite in quickstart |
| US9 inner vs outer | Inner loop on host Playwright tiers; outer gate = smoke + full suite + gate report once |
