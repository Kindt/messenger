# Ops Sign-Off Log: Spec 004

**Date**: 2026-06-15 (engineering closure 2026-06-12; outer gate refresh 2026-06-15)  
**Environment**: local dev / QEMU (stage prod gates marked pending)

## Automated verification (engineering)

| Check | Command | Result | Notes |
|-------|---------|--------|-------|
| Full build + tests | `.\gradlew.bat buildIntegrity --no-daemon --max-workers=1` | **PASS** (2026-06-14) | hex tail + audit logging |
| E2EE unit | `.\gradlew.bat :modules:core-api:test --tests "*Mls*"` | **PASS** (2026-06-09) | 17 tests |
| TLS smoke (dev) | `.\scripts\smoke-tls-redirect.ps1 -SkipTls` | **PASS** | HTTP-only path |
| Hex write unit | `.\gradlew.bat :modules:core-api:test --tests "*ApplicationServiceTest*"` | PASS | User/Org/File |
| Playwright full-stack | `npx playwright test` @ `http://127.0.0.1:19088` | **PASS** (2026-06-15, 30/30) | QEMU hotswap+lb; spec 006 infra |

## US9 — Fast acceptance (inner / outer)

| Check | Command | Result | Notes |
|-------|---------|--------|-------|
| Inner tier `api` | `.\scripts\playwright-dev-loop.ps1 -Tier api` | **PASS** (2026-06-12, 10 tests) | ~10s |
| Inner `all-inner` | `.\scripts\playwright-dev-loop.ps1 -Tier all-inner` | **PASS** (2026-06-15) | 30/30; hotswap nginx lb |
| Outer gate | `.\scripts\qemu-plan-orchestrator.ps1 -SkipVmUp` | **PASS** (2026-06-15, 30/30 + runtime-gate-report) | Ansible web `--force-recreate` on `.env` change |
| Exited(255) probe | auto-remediate on KeepDisks | implemented | Server redeploy once / 10m cooldown |

## US1 — Stage/prod TLS (ops)

| # | Gate | Owner | Status |
|---|------|-------|--------|
| 1 | DNS → stage host | Ops | ⏳ pending real host |
| 2 | `ansible-vault encrypt group_vars/vault.yml` | Ops | ⏳ pending |
| 3 | `ansible-playbook -i inventory/stage playbooks/site.yml --ask-vault-pass` | Ops | ⏳ pending |
| 4 | `smoke-tls-redirect.ps1` with real `-HttpUrl`/`-HttpsUrl` | Ops | ⏳ pending |
| 5 | `ansible-playbook ... --tags tls_smoke` (prod inventory) | Ops | ⏳ pending |

**Local dev**: `.\scripts\smoke-tls-redirect.ps1 -SkipTls` → exit 0 (2026-06-14 re-verified).

**Stage runbook**: [`docs/review/stage-tls-smoke-runbook.md`](../../docs/review/stage-tls-smoke-runbook.md) (US1 row 4 placeholders).

## Engineering backlog closure (2026-06-14)

| Item | Status | Evidence |
|------|--------|----------|
| Code review: `catch (Exception ignored)` in core-api | **closed** | warn-level logs in AdminExportFacade, HealthResource, AdminServerStatsService |
| Hex tail: Keycloak upsert | **closed** | `UserRepositoryPort.upsertFromKeycloak` + AuthService |
| Hex tail: saved-vault chat | **closed** | `SavedChatPort.ensureSavedVaultChat`; removed from ChatRepository |
| Chunk writer duplication | **closed** (prior) | `ChunkedSnapshotWriter` in `modules/common` |
| Redis per health probe | **closed** (prior) | `RedisProbe` shared client |
| E2EE phase 3 OpenMLS / full WASM | **deferred** | product epic; see `docs/plans/06-e2ee-mls.md` § Phase 3 |
| User register `UserRepository.create` → port | **deferred** | low risk; register path unchanged |
| Gradle `core-domain` split | **deferred** | optional phase 3 hex |

## US7 — E2EE security review

| # | Check | Automated | Human sign-off |
|---|-------|-----------|----------------|
| 1 | ADR hybrid (T130) | doc present | ⏳ Product + Engineering |
| 2 | `/plaintext-preview` → 403 when MLS active | unit/API tests | ⏳ Security |
| 3 | Client skips plaintext-preview when MLS active | code review `app.js` | ⏳ Security |
| 4 | NATS `mls.*` consumer staging | config `MLS_WIRE_SUBSCRIBER_ENABLED` | ⏳ Ops |
| 5 | `POST /admin/e2ee/migrate-batch` staging | — | ⏳ Ops |
| 6 | `GET /admin/e2ee/status` sane counts | — | ⏳ Ops |
| 7 | Legacy `e2ee_scheme=legacy` unchanged | unit tests | ✅ automated |
| 8 | Playwright `e2ee-capabilities.spec.ts` | **PASS** on QEMU (2026-06-12) | ⏳ QA formal sign |

**Prod enable**: `MLS_STATUS=active` only after rows 1–8 signed below.

## US6 — Hotplug governance

| Role | Name | Date | Status |
|------|------|------|--------|
| Architecture Owner | _pending_ | | ⏳ |
| Product Owner | _pending_ | | ⏳ |
| Ops/SRE | _pending_ | | ⏳ |

Run when names confirmed:

```powershell
.\scripts\apply-hotplug-signoff.ps1 `
  -ArchitectureOwner "<name>" `
  -ProductOwner "<name>" `
  -OpsSre "<name>"
```

## US5 — Playwright operator gate

Update [runtime-gate-report.md](../../002-web-client-server-parity/runtime-gate-report.md) after full-stack run.

---

### Signatures (fill after gates pass)

| Gate | Signed by | Date |
|------|-----------|------|
| Stage TLS | | |
| E2EE security (8/8) | | |
| Hotplug ADR | | |
| Playwright full-stack | | 2026-06-15 (30/30 engineering) |
