# Tasks: Spec 011 — Korus Cloud Platform



**Input:** [`spec.md`](spec.md), [`plan.md`](plan.md)



---



## Phase 0 — Scaffold



- [x] T01101 Create `deploy/cloud/cells/_template/cell.yaml.example`

- [x] T01102 JSON Schema `deploy/cloud/schemas/cell-manifest.schema.json`

- [x] T01103 `scripts/validate-cell-manifest.py` (+ unit tests)

- [x] T01104 `scripts/cell-manifest-expand.py` (presets: default, bank, pilot, enterprise)

- [x] T01105 `deploy/cloud/registry.yaml` + lifecycle docs

- [x] T01106 `deploy/ansible/playbooks/cell-provision.yml` (wrap `site.yml`, `-e cell_id`)

- [x] T01107 `deploy/cloud/modules/` Terraform skeleton (`generic` provider)

- [x] T01108 Inventory template `deploy/ansible/inventory/cells/README.md`

- [x] T01109 Wire `validate-cell-manifest` into CI / `buildIntegrity` (`checkCellManifest` alias → `run_python_verification.py`)

- [x] T01110 Update [`quickstart.md`](quickstart.md) with worked example



**Phase 0 engineering closure:** 2026-06-16 — validator + expand + CI gate green.



## Phase 1 — Internal Cell (C)



- [x] T01111 Manifest `deploy/cloud/cells/internal-dev.yaml`

- [x] T01112 Provision internal Cell — **QEMU two-host parity** (`inventory/cells/internal-dev/`); full Ansible on stage host blocked until Sep 2026

- [x] T01113 Prometheus scrape file `deploy/observability/prometheus/cells/internal-dev.yml`

- [x] T01114 Ansible role `cell_backup` — preset `default`

- [x] T01115 Smoke + obs dashboard link — [`scripts/smoke-cell-multi-org-qemu.ps1`](../../scripts/smoke-cell-multi-org-qemu.ps1); Grafana `:3001` via `observability-only.yml`

- [x] T01116 Multi-org isolation smoke (2 orgs)



**Phase 1 engineering closure:** 2026-06-16 — dogfood manifest + obs/backup scaffold; live Grafana on platform host → Phase 2 ops.



## Phase 2 — Hosted client (B)



- [x] T01117 `cell-upgrade.yml` + rollback doc *(scaffold; rollback = re-provision prior tag)*

- [x] T01118 `cell-destroy.yml` + volume wipe flag

- [x] T01119 `docs/runbooks/cell-restore.md`

- [x] T01120 Backup preset `bank` (S3 daily + air-gap weekly) — **role upload + runbook**; live bucket/IAM → ops Sep 2026+

- [x] T01121 Example manifests: `internal-dev` (`platform_subdomain`) + `acme-prod-example` (`customer_cname`)

- [x] T01122 Quarterly restore drill checklist in registry

- [x] T01123 Sales enablement: link `competitor_comparison_segment_*.html` *(quickstart §5)*

**Blocked:** first commercial Cell provision — no client host until sales + Sep 2026 infra.



## Phase 3 — GitOps scale



- [ ] T01124 `cell-upgrade.yml` idempotency test (2 Cells) *(needs 2 live Cells)*

- [x] T01125 Pre-commit hook manifest validate (`scripts/precommit-validate-cell-manifests.py`)

- [x] T01126 Terraform provider submodule stub (proxmox OR openstack — TBD)

- [ ] T01127 Platform LB (E2) decision doc / ADR if &gt;15 Cells



## Phase 4 — Shared SaaS (A) — backlog



- [x] T01130 Flyway: tenant RLS policies — **V039** config table + `deploy/sql/tenant_rls_policies.sql` (PG apply on stage — ops Sep 2026+)
- [x] T01131 Keycloak realm provisioning script — **`scripts/keycloak-provision-realm.ps1`**
- [x] T01132 Cross-tenant security test suite — **PluginRepositoryH2Test.crossTenantInstancesAreIsolated** + RLS SQL scaffold
- [x] T01133 Promote tenant A→ dedicated Cell playbook — **`deploy/ansible/playbooks/promote-tenant-to-cell.yml`**

- [ ] T01134 Product/security sign-off for A pool *(human ops — Sep 2026+)*



---



**Status:** Phase 0–1 **engineering closed**; Phase 2+ blocked on commercial host / ops (Sep 2026+).  

**Next ops action:** first B Cell provision + restore drill (T01120); bank S3 upload in `cell_backup`.

