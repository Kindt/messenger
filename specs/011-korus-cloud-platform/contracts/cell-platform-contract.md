# Contract: Cell platform acceptance (Spec 011)

**Spec:** [`../spec.md`](../spec.md)  
**Design:** [`../design/cloud-platform-design.md`](../design/cloud-platform-design.md)

---

## Phase 0 — Scaffold

| # | Criterion | Evidence |
|---|-----------|----------|
| P0-1 | JSON Schema covers required manifest fields | `cell-manifest.schema.json` in repo |
| P0-2 | Validator fails manifest without `commercial.billing_model` | unit test log |
| P0-3 | Example manifest validates | CI green |
| P0-4 | `cell-provision.yml` documented in quickstart | quickstart.md |

---

## Phase 1 — Internal Cell (C)

| # | Criterion | Evidence |
|---|-----------|----------|
| P1-1 | Cell reachable HTTPS or HTTP per manifest | curl /health |
| P1-2 | Prometheus scrape `cell_id` label | screenshot or query |
| P1-3 | Backup daily job writes to configured prefix | object listing |
| P1-4 | 2 orgs — no cross-org read in API | manual or automated test |

---

## Phase 2 — Dedicated hosted (B)

| # | Criterion | Evidence |
|---|-----------|----------|
| P2-1 | `dns.mode=platform_subdomain` Cell live | FQDN + smoke |
| P2-2 | `dns.mode=customer_cname` Cell live | FQDN + smoke |
| P2-3 | Restore drill completed | registry log + smoke post-restore |
| P2-4 | Cell B restore does not affect Cell A | drill notes |
| P2-5 | `smoke-deploy-acceptance.sh` green | log artifact |

---

## Phase 4 — Shared SaaS (A) — future gate

| # | Criterion | Evidence |
|---|-----------|----------|
| P4-1 | Cross-tenant read/write denied | security test report |
| P4-2 | Product + Security sign-off | ops-signoff-log row |
| P4-3 | Promote tenant to B Cell playbook executed once | runbook log |

---

## Non-goals (v1)

- Kubernetes app Cells
- Automated billing export
- Customer self-service portal
- Multi-region DR
