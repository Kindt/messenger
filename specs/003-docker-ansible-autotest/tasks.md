# Tasks: Docker + Ansible Deployment & Autotest Suite

**Input**: Design documents from `specs/003-docker-ansible-autotest/`

## Format: `[ID] [P?] [Story] Description`

---

## Phase 1: Spec-Kit artifacts

- [x] T001 Create spec.md, plan.md, research.md, data-model.md, contracts, quickstart.md
- [x] T002 Create tasks.md and checklists/requirements.md
- [x] T003 Analyze cross-artifact consistency (analyze-report.md)

---

## Phase 2: Ansible (US1)

- [x] T010 Scaffold `deploy/ansible/` with ansible.cfg, inventory/local, inventory/two-host
- [x] T011 Role `common`: verify Docker, create data dirs
- [x] T012 Role `korus_server`: full-stack-up + keycloak ensure
- [x] T013 Role `korus_web`: template `.env`, korus-web-up
- [x] T014 Role `korus_smoke`: optional post-deploy smoke tag
- [x] T015 Playbooks `ci-local.yml`, `site.yml`, `server-only.yml`, `web-only.yml`

---

## Phase 3: Server smokes (US2, US3)

- [x] T020 `scripts/lib/SmokeMessaging.sh` shared helpers
- [x] T021 `scripts/keycloak-ensure-smoke-users.sh`
- [x] T022 `scripts/smoke-messaging-e2e.sh` + `.ps1`
- [x] T023 WS deliver step in messaging-e2e
- [x] T024 Cross-user read receipts in messaging-e2e
- [x] T025 `scripts/smoke-deploy-acceptance.sh`
- [x] T026 Bash smokes: `smoke-ready.sh`, `smoke-auth.sh`, `smoke-web-parity-api.sh`
- [x] T027 Update `scripts/SMOKE_INDEX.md`

---

## Phase 4: CI (US4)

- [x] T030 `.github/workflows/deploy-messaging-smoke.yml`
- [x] T031 Update `docs/CI_AND_REPO_HYGIENE.md`

---

## Phase 5: Playwright (US5)

- [x] T040 `tests/e2e-web/` scaffold + messaging-critical.spec.ts
- [x] T041 Document Playwright run in quickstart.md

---

## Phase 6: Docs

- [x] T050 Update `deploy/two-host/README.md`, root `README.md`
### Phase 7 — Phase B scaffold (post-MVP)

- [x] T060 Role `observability` + playbook `observability-only.yml`
- [x] T061 UFW optional in role `common` (`korus_configure_ufw`, two-host inventory)
- [x] T062 `group_vars/vault.example.yml`
- [x] T063 Playwright job in `deploy-messaging-smoke.yml`
- [x] T064 `scripts/lib/SmokeMessaging.ps1`

---

## Phase 8 — Prod vault / TLS (E3 Phase B)

- [x] T070 Expand `group_vars/vault.example.yml` (db, minio, keycloak, jwt, coturn)
- [x] T071 `inventory/stage/` with `korus_tls_enabled: true` example vars
- [x] T072 Role `tls`: nginx 443 reverse proxy, certbot/BYO cert notes, HTTP redirect
- [x] T073 Wire vault secrets → `korus_server` env template + `korus_server.yml` comments
- [x] T074 `playbooks/site.yml` includes `tls` role when `korus_tls_enabled`
- [x] T075 `deploy/ansible/README.md` prod vault/TLS checklist
- [x] T076 `scripts/smoke-tls-redirect.ps1` (HTTP→HTTPS + cert subject; `-SkipTls` for dev)

---

## Phase 9 — Prod TLS rollout (spec 004 US1 cross-ref)

Cross-reference: `specs/004-deferred-phase2-closure/` (US1 tasks T020–T026, contract `contracts/tls-deploy-contract.md`).

- [x] T080 `inventory/prod/` hosts + group_vars (mirror stage; BYO cert default, optional certbot)
- [x] T081 Vault + CORS in `korus-server.env.j2`; WSS + optional RTC in `korus-web.env.j2`
- [x] T082 Role `tls`: certbot renew hook, UFW 80/443 operator notes
- [x] T083 `korus_smoke` integrates `smoke-tls-redirect.ps1` (`--tags tls_smoke` optional)
- [x] T084 Ops/security sign-off documented in spec 004 quickstart (see [quickstart.md](../004-deferred-phase2-closure/quickstart.md) US1/US7)
