# Plan: Spec 011 — Korus Cloud Platform

**Spec:** [`spec.md`](spec.md)  
**Design:** [`design/cloud-platform-design.md`](design/cloud-platform-design.md)  
**Date:** 2026-06-15  
**Status:** `approved`

---

## Architecture summary

| Layer | Deliverable | Repo touchpoints |
|-------|-------------|------------------|
| **Cell** | server + web VM(s), compose profile | `docker-compose.full-server.yml`, `korus-web`, `playbooks/site.yml` |
| **Manifest** | `deploy/cloud/cells/<id>.yaml` | new |
| **IaC** | Terraform `generic` → Ansible inventory | `deploy/cloud/` |
| **Obs** | Central Prometheus/Grafana | `deploy/observability/` |
| **Backup** | `cell_backup` Ansible role | new role |
| **Control** | git registry + playbooks | `deploy/cloud/registry.yaml` |

**Decisions (locked):** VM+Docker; E1 edge; DNS platform+CNAME; hybrid backup; billing_model per deal; git registry v1.

---

## Phase 0 — Scaffold (2–4 weeks) ✅

**Goal:** Manifest schema, validator, template Cell, provision playbook skeleton.

**Exit (2026-06-16):** validator green; `checkCellManifest` in `buildIntegrity`; [`quickstart.md`](quickstart.md) worked example.

---

## Phase 1 — Internal Cell (C) (4–8 weeks) ✅ engineering

**Goal:** Dogfood — one internal Cell + platform obs scrape.

**Exit (2026-06-16):** `internal-dev` manifest + inventory; Prometheus scrape file; `cell_backup` role; multi-org smoke on QEMU. Live Grafana on dedicated platform host → Phase 2 ops.

---

## Phase 2 — First hosted client (B) (8–12 weeks)

**Goal:** Commercial dedicated Cell; both DNS modes; bank backup preset optional.

| Deliverable | Owner |
|-------------|-------|
| `cell-upgrade.yml`, `cell-destroy.yml` | Platform |
| Runbook `docs/runbooks/cell-restore.md` | Platform + Ops |
| Sales: link segment one-pagers | Sales |
| First client manifest + vault | Ops |

**Exit:** US3 SC-011-03; quarterly restore drill.

---

## Phase 3 — Scale GitOps (12–20 weeks)

**Goal:** 5+ Cells; upgrade loop; optional Terraform provider beyond `generic`.

| Deliverable | Owner |
|-------------|-------|
| Pre-commit manifest validate | Platform |
| Margin review after 3 Cells | Product |
| E2 platform LB evaluation | Platform |

**Exit:** upgrade one Cell without affecting others (automated checklist).

---

## Phase 4 — Shared SaaS (A) (20+ weeks)

**Goal:** Shared Cell pool; hybrid PG (RLS → schema promote).

| Gate | Deliverable |
|------|-------------|
| Security | Pen-test cross-tenant |
| Code | Flyway RLS + realm provisioning |
| Ops | Promote A→B playbook |

**Exit:** US6; explicit product/security sign-off.

---

## Risks

| Risk | Mitigation |
|------|------------|
| Provider TBD | `generic` Terraform + manual VM |
| Ops overload | Start with B only; automate phase 0–1 |
| A too early | FR-011-08 blocks commercial shared pool |
| QEMU vs prod gap | Stage inventory mirrors Cell manifest |

---

## Verification

| Phase | Command / artifact |
|-------|-------------------|
| 0 | `python scripts/validate-cell-manifest.py deploy/cloud/cells/_template/example.yaml` |
| 1–2 | `scripts/smoke-deploy-acceptance.sh` against Cell FQDN |
| 2 | Restore drill log in registry |
| 4 | Cross-tenant integration test (future) |
