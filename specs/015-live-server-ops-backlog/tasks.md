# Tasks: Spec 015 — Live-server ops backlog

**Registry only.** Не показывать агентам в списках доработок до Sep 2026+ или явного распоряжения.  
**Governance:** [`spec.md`](spec.md)

---

## Stage / prod deploy (spec 007 Phase 6)

| ID | Source | Task | Blocker | Eng. ready |
|----|--------|------|---------|------------|
| LSO-001 | T601 | Stage DNS + vault encrypt + `site.yml` | Real stage host + FQDN | `preflight-stage-deploy.ps1`, inventory |
| LSO-002 | T602 | `stage-tls-smoke.ps1` green on stage URL | After LSO-001 | script ✅ |
| LSO-003 | T603 | E2EE staging smokes rows 4–6 | HTTPS stage + admin token | `smoke-e2ee-staging.ps1` |
| LSO-004 | T604 | k6 baseline @ 20% peak on **stage** | Stage host + k6 | `run-k6-stage-baseline.ps1`; QEMU fallback done |
| LSO-005 | T605 | Hotplug human sign-off (3 signers) | Named approvers | `apply-hotplug-signoff.ps1` |
| LSO-006 | T606 | E2EE QA formal sign (US7 row 8) | Staging HTTPS URL | Playwright QEMU ✅ |
| LSO-007 | T607 | Prod `tls_smoke` tag | Prod inventory host | Ansible tag ✅ |

---

## Presentation / prod features ops (spec 010 Phase B)

| ID | Source | Task | Blocker | Eng. ready |
|----|--------|------|---------|------------|
| LSO-010 | T205 | Vault `korus_coturn_secret` + deploy | Stage/prod vault | compose/runbook ✅ |
| LSO-011 | T206 | Optional TURN TLS `:5349` overlay | Prod coturn host | overlay scaffold |
| LSO-012 | T207 | VAPID keys in vault + push deploy | Stage/prod vault | push-worker prod profile |
| LSO-013 | T208 | Manual push E2E on HTTPS | Stage URL + device | web push partial |
| LSO-014 | T210 | Playwright staging HTTPS formal gate | Stage URL | `playwright-staging-gate.ps1` |
| LSO-015 | T211 | E2EE signoff-packet 8/8 signatures | Security/Product/Ops | packet doc ✅ |
| LSO-016 | T212 | Update `product_status.py` + presentation HTML | After LSO-015 | scripts ✅ |

*(T201–T203, T209 — дубликаты LSO-001/002/003/014 в spec 010.)*

---

## Cloud platform ops (spec 011)

| ID | Source | Task | Blocker | Eng. ready |
|----|--------|------|---------|------------|
| LSO-020 | T01124 | `cell-upgrade.yml` idempotency (2 Cells) | 2 live Cells | playbook exists |
| LSO-021 | T01127 | Platform LB ADR (E2, >15 Cells) | Commercial scale decision | — |
| LSO-022 | T01134 | Product/security sign-off A pool | Human ops | RLS scaffold ✅ |
| LSO-023 | T01130-ops | Apply `tenant_rls_policies.sql` on shared PG | Stage/prod PG | V039 + SQL ✅ |

---

## Integrations live backends (spec 014)

| ID | Source | Task | Blocker | Eng. ready |
|----|--------|------|---------|------------|
| LSO-030 | T01431 | Live-backend integrations gate | Guest `.env` live creds | `smoke-integrations-live-gate.ps1` ✅ |

---

## Live streaming formal acceptance (spec 010/013)

| ID | Source | Task | Blocker | Eng. ready |
|----|--------|------|---------|------------|
| LSO-040 | T407 / L6 | Formal 10k viewer load soak | Stage/prod SFU scale | `run-load-test-matrix-qemu.ps1` (QEMU doc only) |
| LSO-041 | T405 | HLS webui player on HTTPS CDN path | Product + stage egress | egress compose scaffold ✅ |

---

## Spec 003 prod inventory (reference)

| ID | Source | Task | Blocker |
|----|--------|------|---------|
| LSO-050 | T080–T084 | Prod inventory scaffold execution | Prod hosts Sep 2026+ |

---

## Changelog (registry)

| Date | Change |
|------|--------|
| 2026-06-17 | Initial registry; consolidated from specs 007/010/011/014 |

**To add:** new row with next `LSO-NNN`; update changelog table above.
