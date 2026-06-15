# Spec 011: Korus Cloud Platform

**Feature branch:** `011-korus-cloud-platform`  
**Created:** 2026-06-15  
**Status:** `approved` (design) · `phase 0` not started  
**Input:** Организация hosted/private cloud на базе существующего Docker/Ansible стека (цели A–D).

**Full design:** [`design/cloud-platform-design.md`](design/cloud-platform-design.md)

---

## Goal

Построить **платформу Cells** для развёртывания Korus Messenger как:

| Слой | Модель | Приоритет |
|------|--------|-----------|
| **D** | Infra platform (compute, obs, backup, TLS) | 1 |
| **C** | Private cloud (internal / холдинг) | 2 |
| **B** | Dedicated hosted (**1 клиент = 1 Cell**) | 3 — commercial v1 |
| **A** | Managed SaaS (shared Cell) | 4 — после B |

**Cell** — атом: server stack + web stack, Ansible `site.yml`, профиль `pilot` / `standard` / `enterprise`.

---

## Relationship to other specs

| Spec | Relationship |
|------|--------------|
| **003** | Ansible playbooks, `site.yml`, smokes — база provision Cell |
| **006** | Pilot/enterprise compose profiles — sizing Cells |
| **007** | Stage TLS, vault — шаблон для Cell TLS |
| **010** | Sales materials (`competitor_comparison_segment_*.html`) — positioning hosted SKU |

**Note:** spec **012** = competitor presentation spider-web; spec **013** = live-streaming (renumbered from 011→012→013).

---

## User Scenarios & Testing

### User Story 1 — Platform foundation (D) (Priority: P0)

Как platform SRE, я хочу поднимать Cell из git manifest одной командой, чтобы не править compose вручную.

**Independent Test:** `validate-cell-manifest.py` + provision internal Cell на stage/QEMU pattern; smoke green.

**Acceptance Scenarios:**

1. **Given** manifest `cells/internal-dev.yaml`, **When** `cell-provision.yml`, **Then** API+UI reachable по FQDN.
2. **Given** `provider=generic`, **When** IP в tfvars, **Then** generated Ansible inventory без cloud API.
3. **Given** upgrade image tag, **When** `cell-upgrade.yml`, **Then** smoke green, другие Cells не затронуты.

---

### User Story 2 — Internal private cloud (C) (Priority: P1)

Как IT холдинга, я хочу internal Cell с несколькими org, чтобы dogfood платформу до первого клиента.

**Independent Test:** 2+ organizations в одном Cell; admin org A не видит данные org B.

**Acceptance Scenarios:**

1. **Given** internal Cell `standard`, **When** 2 org created, **Then** API isolation enforced.
2. **Given** platform obs, **When** scrape Cell, **Then** metrics с label `cell_id`.

---

### User Story 3 — Dedicated hosted Cell (B) (Priority: P1)

Как sales/ops, я хочу продать hosted Cell клиенту с DNS platform subdomain или customer CNAME, чтобы соответствовать комплаенсу on-prem без ЦОД клиента.

**Independent Test:** Cell с `dns.mode=customer_cname`, backup preset `bank`, `billing_model` в manifest; smoke + quarterly restore drill doc.

**Acceptance Scenarios:**

1. **Given** manifest без `commercial.billing_model`, **When** validate, **Then** reject.
2. **Given** B Cell, **When** backup daily, **Then** objects under `{cell_id}/` offsite S3.
3. **Given** two B Cells, **When** restore Cell A, **Then** Cell B unaffected.

---

### User Story 4 — Observability & backup (D) (Priority: P1)

Как on-call, я хочу central Prometheus и configurable backup presets, чтобы выполнять SLA hosted SKU.

**Independent Test:** Alert on stopped core-api; backup preset `bank` = S3 daily + air-gap weekly.

**Acceptance Scenarios:**

1. **Given** platform Prometheus, **When** core-api down, **Then** alert fires with `cell_id`.
2. **Given** backup preset `bank`, **When** weekly job, **Then** air-gap target written.

---

### User Story 5 — Control plane & GitOps (D) (Priority: P1)

Как platform admin, я хочу git registry Cells и lifecycle statuses, чтобы audit provision/decommission.

**Independent Test:** `registry.yaml` lifecycle planned → active → decommissioned; secrets only vault refs.

---

### User Story 6 — Managed SaaS pool (A) (Priority: P3, future)

Как product, я хочу shared Cell для малых tenant после опыта B, с hybrid PG isolation (RLS &lt;10k RU → schema or dedicated Cell).

**Blocked by:** pen-test, RLS migrations, US3 ≥5 Cells in production.

---

## Functional Requirements

| ID | Requirement |
|----|-------------|
| FR-011-01 | Cell manifest JSON Schema; validator in CI |
| FR-011-02 | `commercial.billing_model` **required**; no platform default |
| FR-011-03 | `dns.mode`: `platform_subdomain` \| `customer_cname` |
| FR-011-04 | Backup targets configurable per manifest; presets `default` \| `bank` \| `pilot` \| `enterprise` |
| FR-011-05 | VM-first compute; Terraform provider abstraction (`generic` first) |
| FR-011-06 | Edge E1: TLS on web VM (`roles/tls`) |
| FR-011-07 | Git-only Cell registry v1 |
| FR-011-08 | Commercial v1 = dedicated Cell (B) only |
| FR-011-09 | Shared SaaS (A): hybrid PG — RLS &lt;10k RU, schema or Cell &gt;10k |

---

## Success Criteria

- **SC-011-01:** Phase 0 artifacts in repo (manifest template, validator, provision playbook skeleton).
- **SC-011-02:** Internal Cell (C) smoke green on platform obs.
- **SC-011-03:** First paying B Cell with both DNS modes documented.
- **SC-011-04:** Quarterly restore drill logged for at least one Cell.
- **SC-011-05:** A pool — only after SC-011-03 + security gate (explicit sign-off).

---

## Out of Scope (v1)

- Kubernetes for app Cells
- Self-service customer portal / billing automation
- Multi-region active-active
- Customer CNAME on shared SaaS (A v1)
