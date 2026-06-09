# Quickstart: Deferred Phase 2 Closure

**Spec**: [spec.md](spec.md) | **Tasks**: [tasks.md](tasks.md)

## Prerequisites

- Java 25, Docker, Ansible 2.14+
- For US5 full-stack: QEMU or `deploy/ansible` local inventory (see US8)
- For US1 stage: DNS pointing to stage host, `vault.yml` encrypted

## US1 — Prod TLS

```powershell
cd deploy/ansible
cp group_vars/vault.example.yml group_vars/vault.yml
# edit secrets, then:
ansible-vault encrypt group_vars/vault.yml
ansible-playbook -i inventory/stage playbooks/site.yml --ask-vault-pass
..\..\scripts\smoke-tls-redirect.ps1 -BaseUrl https://your-stage-host
```

**Ops sign-off**: Record in deployment log; HTTPS + WSS verified.

## US2 — Hex write-path

```powershell
.\gradlew.bat :modules:core-api:test --tests "*UserApplicationServiceTest*"
.\gradlew.bat :modules:core-api:test --tests "*OrganizationApplicationServiceTest*"
.\gradlew.bat :modules:core-api:test --tests "*FileApplicationServiceTest*"
.\gradlew.bat buildIntegrity
```

## US3 — Hex tail

```powershell
.\gradlew.bat :modules:core-api:benchmark
.\gradlew.bat buildIntegrity
```

## US4 — Profiling

```powershell
docker compose -f docker/docker-compose.yml -f docker/docker-compose.profiling.yml up -d
.\scripts\profiling\profile-docker-jfr.ps1
```

## US5 — Playwright

```powershell
# Recommended: US8 QEMU stable first
cd tests/e2e-web
npm ci
$env:PLAYWRIGHT_BASE_URL = "http://127.0.0.1:19088"
npx playwright test
```

Operator sign-off: [HANDOFF.md](../../002-web-client-server-parity/HANDOFF.md) → update [runtime-gate-report.md](../../002-web-client-server-parity/runtime-gate-report.md).

## US6 — Governance

```powershell
.\scripts\apply-hotplug-signoff.ps1 -ArchitectureOwner "Name" -ProductOwner "Name" -OpsSre "Name"
```

## US7 — E2EE (after T130 sign-off)

```powershell
.\gradlew.bat :modules:core-api:test --tests "*Mls*"
cd tests/e2e-web
npm ci
$env:PLAYWRIGHT_BASE_URL = "http://127.0.0.1:19088"
npx playwright test specs/e2ee-capabilities.spec.ts
```

### Security review gate (required before prod `MLS_STATUS=active`)

| # | Check | Owner |
|---|-------|-------|
| 1 | ADR hybrid sign-off recorded (T130) | Product + Engineering |
| 2 | `/plaintext-preview` returns 403 when `mls_status=active` | Security |
| 3 | Client does not call plaintext-preview when MLS active (`app.js`) | Security |
| 4 | NATS `mls.*` consumer enabled in staging (`MLS_WIRE_SUBSCRIBER_ENABLED=true`) | Ops |
| 5 | `POST /admin/e2ee/migrate-batch?limit=50` run in staging; pending count → 0 | Ops |
| 6 | `GET /admin/e2ee/status` — `mls_group_count`, `pending_migrations_count` sane | Ops |
| 7 | Legacy `e2ee_scheme=legacy` smoke unchanged | QA |
| 8 | Playwright `e2ee-capabilities.spec.ts` green | QA |

**Prod enable:** set `MLS_STATUS=active` only after rows 1–8 signed in deployment log.

## US8 — QEMU (optional)

```powershell
.\scripts\qemu-up.ps1
.\scripts\qemu-redeploy.ps1 -WebOnly
# Verify http://127.0.0.1:9088 shows login shell
```
