# Ops Sign-Off Log

**Date**: 2026-06-16  
**Model**: **deploy-ready** — инженерия US1/US7 закрыта в репозитории; при появлении хостов остаётся только ops execution + human signatures.  
**Runbook**: [`stage-prod-deploy-runbook.md`](stage-prod-deploy-runbook.md)  
**Spec**: [`specs/007-platform-stage-readiness/tasks.md`](../../specs/007-platform-stage-readiness/tasks.md) (T601–T607)

**Environment**: QEMU = dev/acceptance (HTTP). Stage/prod gates = real FQDN + vault deploy.

---

## Deploy-ready summary

| Gate | Engineering | Ops execution | Human sign-off |
|------|-------------|---------------|----------------|
| **US1** TLS/Vault | ✅ **READY** | ⏳ pending 2 hosts + DNS | ⏳ Stage TLS signature |
| **US7** E2EE | ✅ **READY** (auto 2/7/8 eng.) | ⏳ smokes on HTTPS URL | ⏳ rows 1–3, 8 formal |
| **US6** Hotplug | ✅ script + template | — | ⏳ 3 named signers |
| **US9** Playwright | ✅ 33/33 QEMU | ⏳ optional staging gate | ✅ engineering 2026-06-16 |

**Prod `MLS_STATUS=active`:** только после US7 8/8 + signatures. До этого — `pilot` / engineering на QEMU.

---

## Automated verification (engineering)

| Check | Command | Result | Notes |
|-------|---------|--------|-------|
| Full build + tests | `.\gradlew buildIntegrity` | **PASS** (2026-06-16) | incl. preview-worker |
| E2EE unit | `:modules:core-api:test --tests "*Mls*"` | **PASS** (2026-06-09) | 17 tests |
| TLS smoke (dev) | `smoke-tls-redirect.ps1 -SkipTls` | **PASS** | HTTP QEMU path; not US1 row 4 |
| Hex write unit | `*ApplicationServiceTest*` | **PASS** | User/Org/File |
| Playwright | @ `http://127.0.0.1:19088` | **PASS** (2026-06-16, 33/33) | outer gate + runtime-gate-report |
| Preview worker | guest `:9195/health` | **PASS** (2026-06-16) | full-server compose |
| k6 baseline | `run-k6-qemu-baseline.ps1` | **PASS** (2026-06-16) | fallback JSON; full k6 on stage |
| Stage preflight kit | `preflight-stage-deploy.ps1 -SkipVaultCheck` | **READY** | FAIL placeholders until real FQDN |

---

## US9 — Fast acceptance (inner / outer)

| Check | Command | Result | Notes |
|-------|---------|--------|-------|
| Inner `all-inner` | `playwright-dev-loop.ps1 -Tier all-inner` | **PASS** (2026-06-16) | 33/33 |
| Outer gate | `qemu-plan-orchestrator.ps1 -SkipVmUp` | **PASS** (2026-06-15) | runtime-gate-report |
| Staging gate (optional) | `playwright-staging-gate.ps1 -BaseUrl https://…` | ⏳ | script ready; needs stage URL |

---

## US1 — Stage/prod TLS

### Engineering deliverables — ✅ READY (2026-06-16)

| Artifact | Location |
|----------|----------|
| Stage inventory + README | `deploy/ansible/inventory/stage/` |
| Prod inventory scaffold | `deploy/ansible/inventory/prod/` |
| Vault examples (DB, VAPID, coturn) | `vault.yml.example`, `group_vars/vault.example.yml` |
| TLS nginx role + certbot/BYO notes | `deploy/ansible/roles/tls/` |
| Env templates (`wss://`, CORS, Keycloak HTTPS) | `korus-web.env.j2`, `korus-server.env.j2` |
| TURN prod overlay | `korus-web/docker-compose.turn-prod.yml`, `korus_web_turn_prod` |
| Preflight + validate | `preflight-stage-deploy.ps1`, `validate-stage-inventory.ps1` |
| TLS smokes | `stage-tls-smoke.ps1`, `smoke-tls-redirect.ps1`, `korus_smoke` tag `tls_smoke` |
| Runbooks | `stage-tls-smoke-runbook.md`, `stage-prod-deploy-runbook.md` |

### Ops execution — ⏳ pending real hosts only

| # | Gate | Owner | Eng. | Ops execution |
|---|------|-------|------|---------------|
| 1 | DNS → web host | Ops | ✅ vars documented | ⏳ point DNS to host |
| 2 | `ansible-vault encrypt vault.yml` | Ops | ✅ example + mapping | ⏳ operator secrets |
| 3 | `site.yml` on stage | Ops | ✅ playbook tested on QEMU guests | ⏳ SSH to stage hosts |
| 4 | `smoke-tls-redirect` real HTTPS | Ops | ✅ script + wrapper | ⏳ after row 3 |
| 5 | `--tags tls_smoke` on prod | Ops | ✅ role + tag | ⏳ prod inventory |

**Day-1 command sequence:** [`stage-prod-deploy-runbook.md`](stage-prod-deploy-runbook.md) §1.

**Dev/QEMU:** `smoke-tls-redirect.ps1 -SkipTls` — engineering only, **не** закрывает US1 ops execution.

---

## US7 — E2EE security review

### Engineering / automated — ✅ READY

| # | Check | Evidence | Eng. status |
|---|-------|----------|-------------|
| 2 | `/plaintext-preview` → 403 when MLS active | unit + API tests | ✅ automated |
| 3 | Client skips plaintext-preview when MLS active | `app.js` + code review doc | ✅ engineering |
| 7 | Legacy `e2ee_scheme=legacy` unchanged | unit tests | ✅ automated |
| 8 | Playwright `e2ee-capabilities.spec.ts` | **PASS** QEMU 2026-06-12 | ✅ engineering |

### Ops execution — ⏳ after HTTPS deploy

| # | Check | Script / doc | Ops execution |
|---|-------|--------------|---------------|
| 4 | NATS `mls.*` consumer | `e2ee-staging-checklist.md` §4 | ⏳ verify logs 24h on stage |
| 5 | `POST /admin/e2ee/migrate-batch` | `smoke-e2ee-staging.ps1 -AdminToken` | ⏳ after deploy |
| 6 | `GET /admin/e2ee/status` | same smoke + checklist §6 | ⏳ after deploy |

### Human sign-off — ⏳ pending names

| # | Check | Owner | Status |
|---|-------|-------|--------|
| 1 | ADR hybrid accepted | Product + Engineering | ⏳ signature |
| 2 | Security review row 2 | Security | ⏳ signature |
| 3 | Security review row 3 | Security | ⏳ signature |
| 8 | QA formal sign on **staging** URL | QA | ⏳ after row 4–6 + Playwright staging |

**Packet:** [`e2ee-security-signoff-packet-2026-06-15.md`](e2ee-security-signoff-packet-2026-06-15.md)

---

## US6 — Hotplug governance

| Role | Name | Date | Status |
|------|------|------|--------|
| Architecture Owner | _pending_ | | ⏳ |
| Product Owner | _pending_ | | ⏳ |
| Ops/SRE | _pending_ | | ⏳ |

```powershell
.\scripts\apply-hotplug-signoff.ps1 -ArchitectureOwner "<name>" -ProductOwner "<name>" -OpsSre "<name>"
```

---

## Engineering backlog closure (2026-06-14)

| Item | Status |
|------|--------|
| Hex tail, chunk writer, Redis probe | **closed** |
| E2EE phase 3 OpenMLS / full WASM | **deferred** |
| Gradle `core-domain` split | **deferred** |

---

## US5 — Playwright operator gate

[`runtime-gate-report.md`](../parity/runtime-gate-report.md) — 33/33 engineering 2026-06-16.

---

### Signatures (fill after ops execution on real hosts)

| Gate | Signed by | Date | Notes |
|------|-----------|------|-------|
| Stage TLS (US1 ops rows 1–4) | | | deploy-only; eng. READY |
| E2EE security (US7 8/8) | | | rows 4–6 after HTTPS deploy |
| Hotplug ADR (US6) | | | |
| Playwright full-stack | engineering | 2026-06-16 | 33/33 QEMU |
| Playwright staging (US7 row 8) | | | `playwright-staging-gate.ps1` |
