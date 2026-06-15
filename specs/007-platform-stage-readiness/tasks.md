# Tasks: Platform & Stage Readiness (007)

**Input:** hybrid sprint D — [`docs/plans/2026-06-15-unfinished-development-plan.md`](../../docs/plans/2026-06-15-unfinished-development-plan.md) §9

---

## Phase 1 — W1 T-P2 (QEMU DX)

- [x] T101 Stale redeploy lock PID (`Korus-QemuRedeployLock.ps1`)
- [x] T102 SSH host key auto-refresh (`Get-KorusEd25519HostKey`)
- [x] T103 ops-signoff Playwright 30/30 sync
- [x] T104 ROADMAP §7 + infra design § Related changes

## Phase 2 — W1 T-P1 (stage prep)

- [x] T201 `inventory/stage/README.md`
- [x] T202 `inventory/stage/group_vars/vault.yml.example`
- [x] T203 `docs/review/e2ee-staging-checklist.md`
- [x] T204 `docs/review/hotplug-signoff-request-template.md`

## Phase 3 — W2 T-P2 (outer gate engineering)

- [x] T301 wsUrl `-Force` web redeploy (auto-remediate + orchestrator)
- [x] T302 `scripts/test-korus-wsurl.ps1`
- [x] T303 Hex edit ACL via `MessageApplicationService`
- [x] T304 `scripts/guest-smoke-platform-w2.sh` + SMOKE_INDEX

## Phase 4 — W2 T-P1 (ops prep)

- [x] T401 `docs/review/stage-tls-smoke-runbook.md`
- [x] T402 k6 `scripts/load/pilot-health.js`, `pilot-rest.js`
- [x] T403 `docs/review/e2ee-security-signoff-packet-2026-06-15.md`

## Phase 5 — W3 T-P2 (platform tail)

- [x] T501 `docker/REPLICA_LAB.md`, `replica-lab-up.sh`, `smoke-read-replica-env.sh`
- [x] T502 `UserRepositoryPort.createLocalUser` + AuthService + H2 test
- [x] T503 Push preview i18n + bundle parity test

## Phase 6 — W3/W4 T-P1 (ops — blocked without stage)

- [ ] T601 Stage DNS + vault encrypt + `site.yml` (US1 rows 1–3) — prep kit ✅; operator on stage host
- [ ] T602 Real TLS smoke on stage URL (US1 row 4) — runbook ✅
- [ ] T603 E2EE staging rows 4–6 — checklist ✅
- [x] T604 k6 baseline JSON on QEMU (`scripts/run-k6-qemu-baseline.ps1`, `deploy/qemu/run/k6-pilot-baseline.json` fallback 2026-06-15; full k6 when installed)
- [ ] T605 Hotplug `apply-hotplug-signoff.ps1` (US6) — script + template ✅; human signers pending
- [ ] T606 E2EE QA formal sign (US7 row 8)
- [ ] T607 Prod `tls_smoke` tag (US1 row 5)

## Phase 7 — W4 verification (operator)

- [x] T701a `test-korus-wsurl.ps1` + `smoke-korus-web.ps1` ExpectWsHost (2026-06-15, QEMU live)
- [x] T701b Inner Playwright `all-inner` green (2026-06-15)
- [x] T701c Full outer `qemu-plan-orchestrator.ps1 -SkipVmUp` green (2026-06-15, runtime-gate-report)
- [x] T702 Guest `guest-smoke-platform-w2.sh` on live server guest (2026-06-15)

---

**Engineering closure:** Phases 1–5 + T701–T702 complete → spec 007 engineering sign-off 2026-06-15.  
**Ops backlog:** Phase 6 + T604 (k6 baseline) → resume when stage host / k6 installed.
