# Tasks: Deferred Phase 2 Post-Backlog Closure

**Input**: Design documents from `specs/004-deferred-phase2-closure/`

**Format**: `[ID] [P?] [Story] Description`

---

## Phase 1: Spec-kit artifacts

- [x] T001 Create spec.md and checklists/requirements.md in specs/004-deferred-phase2-closure/
- [x] T002 Create plan.md, research.md, data-model.md, quickstart.md in specs/004-deferred-phase2-closure/
- [x] T003 Create contracts/*.md in specs/004-deferred-phase2-closure/contracts/

---

## Phase 2: Foundational

- [x] T010 [P] Update .specify/feature.json to specs/004-deferred-phase2-closure
- [x] T011 Add Phase 9 cross-ref in specs/003-docker-ansible-autotest/tasks.md for prod TLS rollout

---

## Phase 3: US1 Prod TLS (T020–T026)

- [x] T020 [P] [US1] Create deploy/ansible/inventory/prod/hosts.yml and group_vars/all.yml
- [x] T021 [P] [US1] Wire vault secrets to deploy/ansible/roles/korus_server/templates/korus-server.env.j2
- [x] T022 [P] [US1] Wire vault secrets to deploy/ansible/roles/korus_web/templates/korus-web.env.j2
- [x] T023 [US1] Add CORS_ALLOWED_ORIGINS to deploy/ansible/roles/korus_server/templates/korus-server.env.j2
- [x] T024 [US1] Extend deploy/ansible/roles/tls/tasks/main.yml certbot renew hook and UFW notes
- [x] T025 [US1] Set wss:// and Keycloak HTTPS vars in korus-web and server env templates
- [x] T026 [US1] Integrate scripts/smoke-tls-redirect.ps1 in deploy/ansible/roles/korus_smoke/tasks/main.yml

---

## Phase 4: US2 Hex write (T040–T069)

- [x] T040 [P] [US2] Extend UserRepositoryPort write methods in modules/core-api/src/main/java/com/avandocmsg/messenger/core/port/UserRepositoryPort.java
- [x] T041 [P] [US2] Implement write methods in JdbcUserRepositoryAdapter
- [x] T042 [P] [US2] Add write use-cases to UserApplicationService
- [x] T043 [P] [US2] Delegate UserResource PATCH/presence/privacy/heartbeat to UserApplicationService
- [x] T044 [P] [US2] Extend UserApplicationServiceTest and JdbcUserRepositoryAdapterH2Test
- [x] T050 [P] [US2] Extend OrganizationRepositoryPort CRUD in modules/core-api/.../OrganizationRepositoryPort.java
- [x] T051 [P] [US2] Implement org write in JdbcOrganizationRepositoryAdapter
- [x] T052 [P] [US2] Add org write to OrganizationApplicationService
- [x] T053 [P] [US2] Delegate AdminResource org operations to OrganizationApplicationService
- [x] T054 [P] [US2] Add OrganizationApplicationServiceTest and H2 tests
- [x] T060 [P] [US2] Create ObjectStoragePort in modules/core-api/src/main/java/com/avandocmsg/messenger/core/port/
- [x] T061 [P] [US2] Create MinioObjectStorageAdapter
- [x] T062 [P] [US2] Extend FileMetadataPort insert/delete and JdbcFileMetadataAdapter
- [x] T063 [P] [US2] Move upload/download/delete logic to FileApplicationService
- [x] T064 [P] [US2] Delegate FileResource write paths to FileApplicationService
- [x] T065 [P] [US2] Add FileApplicationServiceTest and update FileResourceTest

---

## Phase 5: US3 Hex tail (T070–T074)

- [x] T070 [US3] Create SavedChatPort and adapter; delegate UserResource saved-chat paths
- [x] T071 [US3] Create PublicLinkPort and adapter from FilePublicLinkRepository
- [x] T072 [US3] Add File and Organization read benchmarks to CoreApiBenchmarkTest
- [x] T073 [US3] Remove unused legacy write methods from api.repository after port migration
- [x] T074 [US3] Mark write-path completed in docs/plans/08-hexagonal-refactoring.md

---

## Phase 6: US4 Profiling (T090–T096)

- [x] T090 [P] [US4] Create docker/Dockerfile.message-pipeline.profiling
- [x] T091 [P] [US4] Create docker/Dockerfile.archiver-worker.profiling
- [x] T092 [P] [US4] Create docker/Dockerfile.deep-archiver-worker.profiling
- [x] T093 [P] [US4] Create docker/Dockerfile.push-worker.profiling
- [x] T094 [P] [US4] Create docker/Dockerfile.export-replay-worker.profiling
- [x] T095 [US4] Extend docker/docker-compose.profiling.yml for 8 profiling targets
- [x] T096 [US4] Update scripts/profiling/profile-docker-jfr.ps1 and README.md

---

## Phase 7: US5 Playwright (T110–T115)

- [x] T110 [US5] Fix or document logout in tests/e2e-web/specs/auth-session.spec.ts
- [x] T111 [US5] Add DOM upload scenario to tests/e2e-web/specs/files-export.spec.ts
- [x] T112 [US5] Add RTC UI or waiver to tests/e2e-web/specs/conference-rtc.spec.ts
- [x] T113 [US5] Add device register test to tests/e2e-web/specs/profile-settings.spec.ts
- [x] T114 [US5] Add optional Playwright job to .github/workflows/deploy-messaging-smoke.yml
- [x] T115 [US5] Update specs/002-web-client-server-parity/runtime-gate-report.md operator template

---

## Phase 8: US7 E2EE (T130–T169)

- [x] T130 [US7] Update docs/adr/ADR-e2ee-mls-library.md with spike decision and product sign-off note
- [x] T140 [US7] Replace MlsService stub with real MLS operations in modules/core-api/.../mls/
- [x] T141 [US7] Add NATS consumer for mls.* subjects in MlsWirePublisher or dedicated consumer
- [x] T142 [US7] Implement membership epoch rotation in MlsGroupManager
- [x] T150 [P] [US7] Add client MLS WASM/JS integration hook in modules/web-client/src/main/resources/webui/app.js
- [x] T151 [P] [US7] Browser key package generation RFC 9420 in app.js
- [x] T160 [US7] Client-side encrypt/decrypt send path in app.js
- [x] T161 [US7] Restrict /plaintext-preview when mls_status active
- [x] T165 [US7] Batch migration job and external interop tests
- [x] T169 [US7] Document security review gate in specs/004-deferred-phase2-closure/quickstart.md

---

## Phase 9: US5 E2EE Playwright (T170)

- [x] T170 [US5] Extend tests/e2e-web/specs/e2ee-capabilities.spec.ts for browser MLS flow

---

## Phase 10: US6 Governance (T180–T182)

- [x] T180 [US6] Document apply-hotplug-signoff.ps1 usage; update ADR Approval Log template in docs/adr/ADR-hotplug-deployment-split.md
- [x] T181 [US6] Sync docs/plans/05-worker-localization.md, 06-e2ee-mls.md, 08-hexagonal-refactoring.md and scripts/SMOKE_INDEX.md
- [x] T182 [US6] Add CI benchmark artifact retention note in docs/plans/08-hexagonal-refactoring.md

---

## Phase 11: US8 QEMU DX (T190–T192)

- [x] T190 [P] [US8] Harden plink stderr handling in deploy/qemu/lib/Update-KorusGuestRepo.ps1
- [x] T191 [US8] Fix vars_files order in deploy/ansible/playbooks/qemu-web-local.yml for WEB_CLIENT_API_UPSTREAM
- [x] T192 [US8] Verify scripts/full-stack-up.sh COMPOSE_PARALLEL_LIMIT and redeploy stability

---

## Phase 12: Polish (T200)

- [x] T200 Create analyze-report.md and acceptance-report.md in specs/004-deferred-phase2-closure/

---

## Phase 13: US9 Fast acceptance (T201–T215)

- [x] T201 [US9] US9 spec + R6 + contracts/fast-acceptance-contract.md
- [x] T202 [US9] scripts/playwright-dev-loop.ps1 — preflight, -Tier, env defaults
- [x] T203 [US9] tests/e2e-web/playwright-tiers.json tier manifest
- [x] T204 [US9] ASCII plan-failure-i18n.json; remove Cyrillic from Invoke-KorusPlanFailureAnalysis.ps1
- [x] T205 [US9] qemu-plan-orchestrator outer gate: inner-tier check, blocked on playwright fail
- [x] T206 [US9] playwright.config.ts env comment for QEMU ports
- [x] T207 [US9] tests/e2e-web/README.md tier commands
- [x] T208 [US9] KeepDisks Exited(255) probe in auto-remediate
- [x] T209 [US9] preload-qemu-docker-images in quickstart / deploy/qemu/README
- [x] T210 [US9] Fix remaining Playwright failures (conference selectors, ui fixtures)
- [x] T211 [US9] inner-tier-status.json writer in dev-loop
- [x] T212 [US9] HANDOFF inner → outer path
- [x] T213 [US9] ops-signoff-log US9 row
- [x] T214 [US9] .cursor/rules/qemu-chat-watch tier-first workflow
- [x] T215 [US9] acceptance-report.md US9 addendum

---

## Dependencies

```text
T010 → all implementation
US2 (T040-T065) → US3 (T070-T074)
T130 → T140-T169
T160 → T170
US8 (T190-T192) recommended before US5 full-stack gate
US9 (T201-T215) inner tiers before outer orchestrator gate
```

## Parallel example (Week 1)

```text
# Parallel:
T020, T021, T040, T050, T060, T090-T094, T130

# After US2 C1-C3:
T070-T074

# After T130 sign-off:
T140, T150 in parallel
```
